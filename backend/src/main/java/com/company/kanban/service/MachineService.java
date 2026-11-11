package com.company.kanban.service;

import com.company.kanban.dto.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.MachineBreakdownRepository;
import com.company.kanban.repository.MachineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Machine list and breakdown records for the Maintenance department. */
@Service
public class MachineService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final List<MachineBreakdownStatus> OPEN = List.of(MachineBreakdownStatus.OPEN, MachineBreakdownStatus.IN_PROGRESS);
    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("code", "code"), Map.entry("machine code", "code"), Map.entry("kod", "code"), Map.entry("kod mesin", "code"),
            Map.entry("name", "name"), Map.entry("machine name", "name"), Map.entry("nama", "name"), Map.entry("nama mesin", "name"),
            Map.entry("location", "location"), Map.entry("lokasi", "location"), Map.entry("line", "location"),
            Map.entry("model", "model"),
            Map.entry("serial no", "serial"), Map.entry("serial no.", "serial"), Map.entry("serial number", "serial"), Map.entry("no siri", "serial"), Map.entry("no. siri", "serial"),
            Map.entry("status", "status"),
            Map.entry("notes", "notes"), Map.entry("note", "notes"), Map.entry("catatan", "notes"), Map.entry("remarks", "notes"));

    private final MachineRepository machines;
    private final MachineBreakdownRepository breakdowns;
    private final AuthorizationService authorization;
    private final NotificationService notifications;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MachineService(MachineRepository machines, MachineBreakdownRepository breakdowns, AuthorizationService authorization, NotificationService notifications) {
        this(machines, breakdowns, authorization, notifications, Clock.system(ZONE));
    }

    MachineService(MachineRepository machines, MachineBreakdownRepository breakdowns, AuthorizationService authorization, NotificationService notifications, Clock clock) {
        this.machines = machines; this.breakdowns = breakdowns; this.authorization = authorization; this.notifications = notifications; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> list(User user) {
        authorization.requireMachineAccess(user);
        Map<Long, Long> open = breakdowns.findByStatusInOrderByReportedAtDesc(OPEN).stream()
                .collect(Collectors.groupingBy(b -> b.getMachine().getId(), Collectors.counting()));
        return machines.findAllByOrderByCodeAsc().stream().map(m -> MachineResponse.from(m, open.getOrDefault(m.getId(), 0L))).toList();
    }

    @Transactional
    public MachineResponse create(MachineRequest request, User user) {
        authorization.requireMachineManagement(user);
        String code = required(request.code(), "Machine code");
        if (machines.findByCodeIgnoreCase(code).isPresent()) throw bad("Machine code " + code + " already exists");
        Machine machine = new Machine(code, required(request.name(), "Machine name"));
        apply(machine, request);
        return MachineResponse.from(machines.save(machine), 0);
    }

    @Transactional
    public MachineResponse update(Long id, MachineRequest request, User user) {
        authorization.requireMachineManagement(user);
        Machine machine = find(id);
        String code = required(request.code(), "Machine code");
        machines.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw bad("Machine code " + code + " already exists"); });
        machine.setCode(code);
        machine.setName(required(request.name(), "Machine name"));
        apply(machine, request);
        return MachineResponse.from(machines.save(machine), openCount(machine.getId()));
    }

    @Transactional
    public MachineImportResult importCsv(String text, User user) {
        authorization.requireMachineManagement(user);
        List<List<String>> rows = MachineCsv.parse(text == null ? "" : text);
        if (rows.isEmpty()) throw bad("The file is empty");
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = HEADERS.get(header.get(i).trim().toLowerCase(Locale.ROOT));
            if (key != null) columns.putIfAbsent(key, i);
        }
        if (!columns.containsKey("code") || !columns.containsKey("name")) throw bad("The first row must have Code and Name columns");
        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Function<String, String> cell = key -> {
                Integer i = columns.get(key);
                return i == null || i >= row.size() ? null : clean(row.get(i));
            };
            String code = cell.apply("code"), name = cell.apply("name");
            int line = r + 1;
            if (code == null || name == null) { errors.add("Row " + line + ": Code and Name are required"); continue; }
            if (code.length() > 60 || name.length() > 160) { errors.add("Row " + line + ": Code or Name is too long"); continue; }
            if (!seen.add(code.toLowerCase(Locale.ROOT))) { errors.add("Row " + line + ": code " + code + " appears twice"); continue; }
            MachineStatus status;
            try { status = parseStatus(cell.apply("status")); }
            catch (IllegalArgumentException e) { errors.add("Row " + line + ": unknown status " + cell.apply("status")); continue; }
            Optional<Machine> existing = machines.findByCodeIgnoreCase(code);
            Machine machine = existing.orElseGet(() -> new Machine(code, name));
            machine.setName(name);
            if (columns.containsKey("location")) machine.setLocation(limit(cell.apply("location"), 120));
            if (columns.containsKey("model")) machine.setModel(limit(cell.apply("model"), 120));
            if (columns.containsKey("serial")) machine.setSerialNumber(limit(cell.apply("serial"), 120));
            if (columns.containsKey("notes")) machine.setNotes(limit(cell.apply("notes"), 2000));
            if (status != null) machine.setStatus(status);
            machines.save(machine);
            if (existing.isPresent()) updated++; else created++;
        }
        return new MachineImportResult(created, updated, errors);
    }

    @Transactional(readOnly = true)
    public List<MachineBreakdownResponse> breakdowns(String view, User user) {
        authorization.requireMachineAccess(user);
        LocalDateTime now = now();
        List<MachineBreakdown> items = "history".equalsIgnoreCase(view)
                ? breakdowns.findTop200ByStatusOrderByCompletedAtDesc(MachineBreakdownStatus.COMPLETED)
                : breakdowns.findByStatusInOrderByReportedAtDesc(OPEN);
        return items.stream().map(b -> MachineBreakdownResponse.from(b, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<MachineBreakdownResponse> machineHistory(Long machineId, User user) {
        authorization.requireMachineAccess(user);
        find(machineId);
        LocalDateTime now = now();
        return breakdowns.findByMachineIdOrderByReportedAtDesc(machineId).stream().map(b -> MachineBreakdownResponse.from(b, now)).toList();
    }

    @Transactional
    public MachineBreakdownResponse report(Long machineId, MachineBreakdownRequest request, User user) {
        authorization.requireMachineAccess(user);
        Machine machine = find(machineId);
        if (!machine.isActive()) throw bad("This machine is inactive");
        MachineBreakdown breakdown = breakdowns.save(new MachineBreakdown(machine, required(request.problem(), "Problem"), user, now()));
        machine.setStatus(MachineStatus.BREAKDOWN);
        machines.save(machine);
        notifications.notifyMachineBreakdown(breakdown, user);
        return MachineBreakdownResponse.from(breakdown, now());
    }

    @Transactional
    public MachineBreakdownResponse updateBreakdown(Long id, MachineBreakdownUpdateRequest request, User user) {
        authorization.requireMachineAccess(user);
        MachineBreakdown breakdown = breakdowns.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Breakdown not found"));
        if (breakdown.getStatus() == MachineBreakdownStatus.COMPLETED) throw bad("This breakdown is already completed");
        LocalDateTime now = now();
        Machine machine = breakdown.getMachine();
        if (request.actionTaken() != null) breakdown.setActionTaken(clean(request.actionTaken()));
        switch (request.status()) {
            case OPEN -> machine.setStatus(MachineStatus.BREAKDOWN);
            case IN_PROGRESS -> {
                if (breakdown.getStartedAt() == null) breakdown.setStartedAt(now);
                machine.setStatus(MachineStatus.UNDER_MAINTENANCE);
            }
            case COMPLETED -> {
                if (breakdown.getStartedAt() == null) breakdown.setStartedAt(now);
                breakdown.setCompletedAt(now);
                breakdown.setCompletedBy(user);
                if (!breakdowns.existsByMachineIdAndStatusInAndIdNot(machine.getId(), OPEN, breakdown.getId())) machine.setStatus(MachineStatus.RUNNING);
            }
        }
        breakdown.setStatus(request.status());
        machines.save(machine);
        return MachineBreakdownResponse.from(breakdowns.save(breakdown), now);
    }

    private void apply(Machine machine, MachineRequest request) {
        machine.setLocation(clean(request.location()));
        machine.setModel(clean(request.model()));
        machine.setSerialNumber(clean(request.serialNumber()));
        machine.setNotes(clean(request.notes()));
        if (request.status() != null) machine.setStatus(request.status());
        if (request.active() != null) machine.setActive(request.active());
    }

    static MachineStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        return MachineStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
    }

    private long openCount(Long machineId) {
        return breakdowns.findByMachineIdOrderByReportedAtDesc(machineId).stream().filter(b -> OPEN.contains(b.getStatus())).count();
    }

    private Machine find(Long id) { return machines.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found")); }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZONE)); }
    private static String clean(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static String limit(String v, int max) { return v == null || v.length() <= max ? v : v.substring(0, max); }
    private static String required(String v, String label) { String c = clean(v); if (c == null) throw bad(label + " is required"); return c; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
