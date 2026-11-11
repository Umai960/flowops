package com.company.kanban.controller;

import com.company.kanban.dto.*;
import com.company.kanban.entity.User;
import com.company.kanban.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class MachineController {
    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;
    private final MachineService service;

    public MachineController(MachineService service) { this.service = service; }

    @GetMapping public List<MachineResponse> list(@AuthenticationPrincipal User user) { return service.list(user); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MachineResponse create(@Valid @RequestBody MachineRequest request, @AuthenticationPrincipal User user) { return service.create(request, user); }
    @PutMapping("/{id}")
    public MachineResponse update(@PathVariable Long id, @Valid @RequestBody MachineRequest request, @AuthenticationPrincipal User user) { return service.update(id, request, user); }

    @PostMapping("/import")
    public MachineImportResult importCsv(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
        if (file.getSize() > MAX_IMPORT_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is larger than 2 MB");
        return service.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), user);
    }

    @GetMapping("/breakdowns")
    public List<MachineBreakdownResponse> breakdowns(@RequestParam(defaultValue = "open") String view, @AuthenticationPrincipal User user) { return service.breakdowns(view, user); }
    @GetMapping("/{id}/breakdowns")
    public List<MachineBreakdownResponse> history(@PathVariable Long id, @AuthenticationPrincipal User user) { return service.machineHistory(id, user); }
    @PostMapping("/{id}/breakdowns") @ResponseStatus(HttpStatus.CREATED)
    public MachineBreakdownResponse report(@PathVariable Long id, @Valid @RequestBody MachineBreakdownRequest request, @AuthenticationPrincipal User user) { return service.report(id, request, user); }
    @GetMapping("/parts")
    public List<MachinePartResponse> currentParts(@AuthenticationPrincipal User user) { return service.currentParts(user); }
    @GetMapping("/{id}/parts")
    public List<MachinePartResponse> machineParts(@PathVariable Long id, @AuthenticationPrincipal User user) { return service.machineParts(id, user); }
    @PostMapping("/{id}/parts") @ResponseStatus(HttpStatus.CREATED)
    public MachinePartResponse recordPart(@PathVariable Long id, @Valid @RequestBody MachinePartRequest request, @AuthenticationPrincipal User user) { return service.recordPart(id, request, user); }
    @PutMapping("/parts/{id}")
    public MachinePartResponse updatePart(@PathVariable Long id, @Valid @RequestBody MachinePartRequest request, @AuthenticationPrincipal User user) { return service.updatePart(id, request, user); }
    @DeleteMapping("/parts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePart(@PathVariable Long id, @AuthenticationPrincipal User user) { service.deletePart(id, user); }
    @PutMapping("/breakdowns/{id}")
    public MachineBreakdownResponse updateBreakdown(@PathVariable Long id, @Valid @RequestBody MachineBreakdownUpdateRequest request, @AuthenticationPrincipal User user) { return service.updateBreakdown(id, request, user); }
}
