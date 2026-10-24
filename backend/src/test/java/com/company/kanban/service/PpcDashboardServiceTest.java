package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PpcDashboardServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final RawMaterialArrivalRepository materials = mock(RawMaterialArrivalRepository.class);
    private final PpcPlanningItemRepository planning = mock(PpcPlanningItemRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MonthlyWorkReportRepository reports = mock(MonthlyWorkReportRepository.class);
    private final AuthorizationService authorization = new AuthorizationService();
    private final Department ppc = entity(new Department("PPC"), 1L);
    private final User bob = entity(new User("Bob", "bob@test", "x", Role.STAFF, ppc), 2L);
    private final PpcDashboardService service = new PpcDashboardService(tasks, materials, planning, users, reports, authorization,
            Clock.fixed(LocalDate.of(2026, 8, 24).atStartOfDay(PpcDashboardService.ZONE).toInstant(), PpcDashboardService.ZONE));

    @Test
    void staffCountsActiveDueTodayOverdueAndGeneralTasks() {
        Task today = task("Today", LocalDate.of(2026, 8, 24), TaskStatus.DOING);
        Task overdue = task("Late", LocalDate.of(2026, 8, 22), TaskStatus.DRAFT);
        Task done = task("Done", LocalDate.of(2026, 8, 20), TaskStatus.DONE);
        when(tasks.findByAssigneeIdOrderByStatusAscPositionAsc(2L)).thenReturn(List.of(today, overdue, done));
        when(materials.findByActualArrivalDateIsNull()).thenReturn(List.of());
        when(planning.findUpcoming(any(), any(), any())).thenReturn(List.of());

        var result = service.get(bob);

        assertEquals(2, result.taskSummary().active());
        assertEquals(1, result.taskSummary().dueToday());
        assertEquals(1, result.taskSummary().overdue());
        assertEquals(1, result.kanbanSummary().toDo());
        assertEquals(1, result.kanbanSummary().inProgress());
    }

    @Test
    void nonPpcUserCannotReadPpcDashboard() {
        User user = new User("Alice", "alice@test", "x", Role.STAFF, new Department("RDD"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.get(user));
        verifyNoInteractions(tasks, materials, planning, users, reports);
    }

    private Task task(String title, LocalDate due, TaskStatus status) {
        Task task = new Task(title, "", Priority.MEDIUM, due, 1, null, bob);
        task.setDepartment(ppc); task.setStatus(status); return entity(task, (long) title.hashCode());
    }
    private static <T> T entity(T value, Long id) { ReflectionTestUtils.setField(value, "id", id); return value; }
}
