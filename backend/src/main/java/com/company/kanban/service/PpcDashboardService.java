package com.company.kanban.service;

import com.company.kanban.dto.PpcDashboardResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PpcDashboardService {
    static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final TaskRepository tasks;
    private final RawMaterialArrivalRepository materials;
    private final PpcPlanningItemRepository planning;
    private final UserRepository users;
    private final MonthlyWorkReportRepository reports;
    private final AuthorizationService authorization;
    private final Clock clock;

    @Autowired
    public PpcDashboardService(TaskRepository tasks, RawMaterialArrivalRepository materials,
                               PpcPlanningItemRepository planning, UserRepository users,
                               MonthlyWorkReportRepository reports, AuthorizationService authorization) {
        this(tasks, materials, planning, users, reports, authorization, Clock.system(ZONE));
    }
    PpcDashboardService(TaskRepository tasks, RawMaterialArrivalRepository materials, PpcPlanningItemRepository planning,
                         UserRepository users, MonthlyWorkReportRepository reports, AuthorizationService authorization, Clock clock) {
        this.tasks = tasks; this.materials = materials; this.planning = planning; this.users = users;
        this.reports = reports; this.authorization = authorization; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PpcDashboardResponse get(User current) {
        authorization.requirePpcPlanningAccess(current);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<Task> scopedTasks = current.getRole() == Role.STAFF
                ? tasks.findByAssigneeIdOrderByStatusAscPositionAsc(current.getId())
                : tasks.findByEffectiveDepartmentId(current.getDepartment().getId());
        scopedTasks = scopedTasks.stream().filter(t -> t.getDepartment() != null
                && "PPC".equalsIgnoreCase(t.getDepartment().getName())
                && (current.getRole() == Role.STAFF || (t.getAssignee() != null && t.getAssignee().getRole() == Role.STAFF))
                && (t.getAssignee() == null || t.getAssignee().getStatus() == AccountStatus.ACTIVE)).toList();

        PpcDashboardResponse.TaskSummary taskSummary = new PpcDashboardResponse.TaskSummary(
                scopedTasks.stream().filter(this::active).count(),
                scopedTasks.stream().filter(t -> active(t) && today.equals(t.getDueDate())).count(),
                scopedTasks.stream().filter(t -> active(t) && t.getDueDate() != null && t.getDueDate().isBefore(today)).count());
        PpcDashboardResponse.KanbanSummary kanban = new PpcDashboardResponse.KanbanSummary(
                count(scopedTasks, TaskStatus.DRAFT), count(scopedTasks, TaskStatus.DOING),
                count(scopedTasks, TaskStatus.REVIEW), count(scopedTasks, TaskStatus.DONE));

        List<RawMaterialArrival> openMaterials = materials.findByActualArrivalDateIsNull();
        PpcDashboardResponse.RawMaterialSummary raw = rawSummary(openMaterials, today);
        List<PpcDashboardResponse.PlanningPreview> upcoming = planning.findUpcoming("PPC", today, PageRequest.of(0, 5)).stream()
                .map(x -> new PpcDashboardResponse.PlanningPreview(x.getId(), x.getTitle(), x.getStartDate(), x.getEndDate(), x.getPriority(), x.getStatus())).toList();

        if (current.getRole() == Role.STAFF) {
            return new PpcDashboardResponse(taskSummary, raw, upcoming, kanban, List.of(), 0, List.of(), null);
        }

        List<PpcDashboardResponse.OverdueTask> overdue = scopedTasks.stream()
                .filter(t -> active(t) && t.getDueDate() != null && t.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(Task::getDueDate))
                .limit(5)
                .map(t -> new PpcDashboardResponse.OverdueTask(t.getId(), t.getTitle(),
                        t.getAssignee() == null ? "Unassigned" : t.getAssignee().getName(),
                        t.getDueDate().until(today, java.time.temporal.ChronoUnit.DAYS), t.getWorkload())).toList();
        List<User> staff = users.findByDepartmentIdOrderByNameAsc(current.getDepartment().getId()).stream()
                .filter(u -> u.getRole() == Role.STAFF && u.getStatus() == AccountStatus.ACTIVE).toList();
        Map<Long, List<Task>> byAssignee = scopedTasks.stream().filter(t -> t.getAssignee() != null)
                .collect(Collectors.groupingBy(t -> t.getAssignee().getId()));
        List<PpcDashboardResponse.TeamWorkload> workload = staff.stream().map(u -> {
            List<Task> own = byAssignee.getOrDefault(u.getId(), List.of()).stream().filter(this::active).toList();
            return new PpcDashboardResponse.TeamWorkload(u.getId(), u.getName(), own.size(), own.stream().mapToInt(t -> t.getWorkload() == null ? 0 : t.getWorkload()).sum());
        }).toList();
        Set<Long> staffIds = staff.stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, MonthlyWorkReport> currentReports = reports.findByUserIdInAndYearAndMonth(staffIds, today.getYear(), today.getMonthValue()).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), Function.identity()));
        List<String> pending = staff.stream().filter(u -> !Optional.ofNullable(currentReports.get(u.getId())).map(r -> r.getStatus() == MonthlyWorkReportStatus.SUBMITTED).orElse(false)).map(User::getName).toList();
        PpcDashboardResponse.MonthlyReportStatus monthly = new PpcDashboardResponse.MonthlyReportStatus(staff.size() - pending.size(), staff.size(), pending);
        return new PpcDashboardResponse(taskSummary, raw, upcoming, kanban, overdue,
                scopedTasks.stream().filter(t -> t.getStatus() == TaskStatus.REVIEW).count(), workload, monthly);
    }

    private PpcDashboardResponse.RawMaterialSummary rawSummary(List<RawMaterialArrival> items, LocalDate today) {
        List<RawMaterialArrival> open = items.stream().filter(x -> x.getExpectedArrivalDate() != null).toList();
        long delayed = open.stream().filter(x -> x.getExpectedArrivalDate().isBefore(today)).count();
        long dueToday = open.stream().filter(x -> x.getExpectedArrivalDate().equals(today)).count();
        long tomorrow = open.stream().filter(x -> x.getExpectedArrivalDate().equals(today.plusDays(1))).count();
        long followUp = open.stream().filter(x -> followUpDue(x, today)).count();
        Map<Long, PpcDashboardResponse.RawMaterialPreview> preview = new LinkedHashMap<>();
        open.stream().filter(x -> followUpDue(x, today)).forEach(x -> preview.put(x.getId(), preview(x, "Follow-up due", today)));
        open.stream().filter(x -> x.getExpectedArrivalDate().isBefore(today)).forEach(x -> preview.putIfAbsent(x.getId(), preview(x, "Delayed", today)));
        open.stream().filter(x -> x.getExpectedArrivalDate().equals(today)).forEach(x -> preview.putIfAbsent(x.getId(), preview(x, "Due today", today)));
        open.stream().filter(x -> x.getExpectedArrivalDate().equals(today.plusDays(1))).forEach(x -> preview.putIfAbsent(x.getId(), preview(x, "Arriving tomorrow", today)));
        return new PpcDashboardResponse.RawMaterialSummary(delayed, dueToday, tomorrow, followUp, preview.values().stream().limit(5).toList());
    }
    private PpcDashboardResponse.RawMaterialPreview preview(RawMaterialArrival x, String attention, LocalDate today) {
        return new PpcDashboardResponse.RawMaterialPreview(x.getId(), x.getMaterialName(), x.getExpectedArrivalDate(), attention,
                Math.max(0, x.getExpectedArrivalDate().until(today, java.time.temporal.ChronoUnit.DAYS)));
    }
    private boolean followUpDue(RawMaterialArrival x, LocalDate today) {
        return x.getExpectedArrivalDate().isBefore(today)
                && (x.getFollowUpStatus() == RawMaterialFollowUpStatus.CONTACTED_SUPPLIER || x.getFollowUpStatus() == RawMaterialFollowUpStatus.WAITING_FOR_UPDATE)
                && x.getLastFollowUpAt() != null
                && x.getLastFollowUpAt().atZone(ZONE).toLocalDate().until(today, java.time.temporal.ChronoUnit.DAYS) >= 2;
    }
    private boolean active(Task task) { return task.getStatus() != null && task.getStatus() != TaskStatus.DONE; }
    private long count(List<Task> list, TaskStatus status) { return list.stream().filter(t -> t.getStatus() == status).count(); }
}
