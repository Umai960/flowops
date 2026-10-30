package com.company.kanban.service;

import com.company.kanban.dto.UpdateTaskDatesRequest;
import com.company.kanban.entity.*;
import com.company.kanban.repository.BoardRepository;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.KanbanColumnRepository;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GanttServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final BoardRepository boards = mock(BoardRepository.class);
    private final AuthorizationService authorization = new AuthorizationService();
    private final TaskService taskService = new TaskService(tasks, mock(KanbanColumnRepository.class), mock(UserRepository.class),
            mock(DepartmentRepository.class), authorization, mock(NotificationService.class));
    private final GanttService service = new GanttService(boards, tasks, taskService, authorization);

    private Department ppc;
    private Department rdd;
    private User ppcStaff;
    private User rddStaff;
    private User admin;
    private Board plant;
    private Board trial;
    private Task ppcTask;

    @BeforeEach
    void setUp() {
        ppc = entity(new Department("PPC"), 10L);
        rdd = entity(new Department("RDD"), 20L);
        ppcStaff = entity(new User("PPC Staff", "ppc@test", "x", Role.STAFF, ppc), 1L);
        rddStaff = entity(new User("RDD Staff", "rdd@test", "x", Role.STAFF, rdd), 2L);
        admin = entity(new User("Admin", "admin@test", "x", Role.ADMIN, ppc), 3L);
        plant = entity(new Board("Plant move", "", ppc), 30L);
        trial = entity(new Board("Trial run", "", rdd), 31L);
        ppcTask = task(100L, "Order racks", new KanbanColumn("To Do", 1, plant));
        Task general = task(101L, "General follow-up", null);
        general.setDepartment(ppc);
        when(boards.findByDepartmentId(10L)).thenReturn(List.of(plant));
        when(boards.findAll()).thenReturn(List.of(trial, plant));
        when(tasks.findByEffectiveDepartmentId(10L)).thenReturn(List.of(ppcTask, general));
        when(tasks.findAllProjectTasks()).thenReturn(List.of(ppcTask, task(102L, "Mix batch", new KanbanColumn("Done", 4, trial))));
        when(tasks.findById(100L)).thenReturn(Optional.of(ppcTask));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void staffSeeOnlyTheirDepartmentProjectsWithoutGeneralTasks() {
        var projects = service.getProjects(null, ppcStaff);

        assertEquals(1, projects.size());
        assertEquals("Plant move", projects.get(0).name());
        assertEquals(List.of(100L), projects.get(0).tasks().stream().map(t -> t.id()).toList());
    }

    @Test
    void staffCannotAskForAnotherDepartment() {
        var error = assertThrows(ResponseStatusException.class, () -> service.getProjects(10L, rddStaff));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void adminWithoutDepartmentSeesEveryProject() {
        var projects = service.getProjects(null, admin);

        assertEquals(List.of("Plant move", "Trial run"), projects.stream().map(p -> p.name()).toList());
        assertEquals(1, projects.get(1).tasks().size());
    }

    @Test
    void draggingSavesStartAndDueDates() {
        var response = taskService.updateTaskDates(100L,
                new UpdateTaskDatesRequest(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6)), ppcStaff);

        assertEquals(LocalDate.of(2026, 11, 2), response.startDate());
        assertEquals(LocalDate.of(2026, 11, 6), response.dueDate());
    }

    @Test
    void startAfterDueIsRejected() {
        var error = assertThrows(ResponseStatusException.class, () -> taskService.updateTaskDates(100L,
                new UpdateTaskDatesRequest(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 6)), ppcStaff));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void otherDepartmentsCannotMoveTheTask() {
        var error = assertThrows(ResponseStatusException.class, () -> taskService.updateTaskDates(100L,
                new UpdateTaskDatesRequest(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6)), rddStaff));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private Task task(Long id, String title, KanbanColumn column) {
        Task task = entity(new Task(title, "", Priority.MEDIUM, LocalDate.of(2026, 11, 6), 1, column, ppcStaff), id);
        task.setCreatedAt(LocalDateTime.of(2026, 10, 30, 9, 0));
        return task;
    }

    private static <T> T entity(T value, Long id) {
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
