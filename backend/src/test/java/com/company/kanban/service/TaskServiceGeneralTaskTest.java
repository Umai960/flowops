package com.company.kanban.service;

import com.company.kanban.dto.CreateTaskRequest;
import com.company.kanban.dto.UpdateTaskStatusRequest;
import com.company.kanban.dto.ReviewAction;
import com.company.kanban.dto.ReviewActionRequest;
import com.company.kanban.dto.TaskSnapshotResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.KanbanColumnRepository;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceGeneralTaskTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final KanbanColumnRepository columns = mock(KanbanColumnRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TaskService service = new TaskService(
            tasks, columns, users, departments, new AuthorizationService(), notifications);

    private Department ppc;
    private Department prod;
    private User ppcStaff;
    private User ppcManager;
    private User prodStaff;
    private User admin;

    @BeforeEach
    void setUp() {
        ppc = entity(new Department("PPC"), 10L);
        prod = entity(new Department("PROD"), 20L);
        ppcStaff = entity(new User("PPC Staff", "ppc@test", "x", Role.STAFF, ppc), 1L);
        ppcManager = entity(new User("PPC Manager", "manager@test", "x", Role.MANAGER, ppc), 2L);
        prodStaff = entity(new User("PROD Staff", "prod@test", "x", Role.STAFF, prod), 3L);
        admin = entity(new User("Admin", "admin@test", "x", Role.ADMIN, prod), 4L);
        when(departments.findById(10L)).thenReturn(Optional.of(ppc));
        when(departments.findById(20L)).thenReturn(Optional.of(prod));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            if (task.getId() == null) ReflectionTestUtils.setField(task, "id", 100L);
            return task;
        });
    }

    @Test
    void ppcStaffCreatesGeneralTaskWithoutProjectAndWithPpcOwnership() {
        when(users.findById(1L)).thenReturn(Optional.of(ppcStaff));

        var response = service.createTask(request(null, 10L, 1L), ppcStaff);

        assertTrue(response.generalTask());
        assertNull(response.boardId());
        assertNull(response.columnId());
        assertEquals("General Task", response.boardName());
        assertEquals(10L, response.departmentId());
        assertEquals("PPC", response.departmentName());
        assertEquals(1L, response.createdById());
        assertEquals(TaskStatus.DRAFT, response.status());
        verify(notifications).notifyTaskAssigned(any(Task.class), same(ppcStaff));
    }

    @Test
    void ppcProjectTaskCreationRemainsUnchanged() {
        Board board = entity(new Board("Production Plan", "", ppc), 30L);
        KanbanColumn column = entity(new KanbanColumn("To Do", 1, board), 40L);
        when(columns.findById(40L)).thenReturn(Optional.of(column));
        when(users.findById(1L)).thenReturn(Optional.of(ppcStaff));

        var response = service.createTask(request(40L, null, 1L), ppcStaff);

        assertFalse(response.generalTask());
        assertEquals(30L, response.boardId());
        assertEquals(40L, response.columnId());
        assertEquals("Production Plan", response.boardName());
        assertEquals(10L, response.departmentId());
    }

    @Test
    void ppcGeneralAssignmentCannotCrossDepartments() {
        when(users.findById(3L)).thenReturn(Optional.of(prodStaff));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createTask(request(null, 10L, 3L), ppcManager));

        assertEquals(403, error.getStatusCode().value());
        verify(tasks, never()).save(any());
    }

    @Test
    void staffCanCreateGeneralTaskForTheirDepartment() {
        when(users.findById(3L)).thenReturn(Optional.of(prodStaff));

        var response = service.createTask(request(null, 20L, 3L), prodStaff);

        assertTrue(response.generalTask());
        assertEquals(20L, response.departmentId());
        assertEquals(3L, response.assigneeId());
    }

    @Test
    void adminCanCreateAndAssignPpcGeneralTaskToPpcStaff() {
        when(users.findById(1L)).thenReturn(Optional.of(ppcStaff));

        var response = service.createTask(request(null, 10L, 1L), admin);

        assertTrue(response.generalTask());
        assertEquals(1L, response.assigneeId());
        assertEquals(4L, response.createdById());
    }

    @Test
    void myKanbanIncludesGeneralTaskWhileProjectColumnDoesNot() {
        Task general = generalTask(TaskStatus.DRAFT);
        Task project = projectTask();
        when(tasks.findActiveByAssigneeId(eq(1L), any())).thenReturn(List.of(general, project));
        when(columns.findById(40L)).thenReturn(Optional.of(project.getColumn()));
        when(tasks.findActiveByColumnId(eq(40L), any())).thenReturn(List.of(project));

        assertEquals(2, service.getMyTasks(ppcStaff).size());
        List<com.company.kanban.dto.TaskResponse> boardTasks = service.getTasksByColumn(40L, ppcStaff);
        assertEquals(1, boardTasks.size());
        assertFalse(boardTasks.getFirst().generalTask());
    }

    @Test
    void generalTaskMovesThroughReviewWithoutAProject() {
        Task general = generalTask(TaskStatus.DOING);
        when(tasks.findById(100L)).thenReturn(Optional.of(general));
        when(tasks.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus.REVIEW, 100L))
                .thenReturn(List.of());

        var response = service.updateTaskStatus(
                100L, new UpdateTaskStatusRequest(TaskStatus.REVIEW, 1), ppcStaff);

        assertEquals(TaskStatus.REVIEW, response.status());
        assertNotNull(general.getSubmittedForReviewAt());
        verify(notifications).notifyReviewSubmitted(general, ppcStaff);
    }

    @Test
    void staffCanCompleteGeneralTaskWithoutManagerApproval() {
        Task general = generalTask(TaskStatus.DOING);
        when(tasks.findById(100L)).thenReturn(Optional.of(general));
        when(tasks.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus.DONE, 100L))
                .thenReturn(List.of());
        when(tasks.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus.DOING, 100L))
                .thenReturn(List.of());

        var response = service.updateTaskStatus(
                100L, new UpdateTaskStatusRequest(TaskStatus.DONE, 1), ppcStaff);

        assertEquals(TaskStatus.DONE, response.status());
        verify(notifications, never()).notifyReviewSubmitted(any(), any());
    }

    @Test
    void generalTaskCanBeSnapshottedWithDepartmentAndNoBoard() {
        Task general = generalTask(TaskStatus.DOING);
        SnapshotBatch batch = new SnapshotBatch(LocalDate.of(2026, 8, 21), SnapshotType.END_OF_DAY,
                LocalDate.of(2026, 8, 21).atTime(17, 0), false);

        TaskSnapshot snapshot = new TaskSnapshot(batch, general);

        assertNull(snapshot.getBoardId());
        assertNull(snapshot.getBoardName());
        assertEquals(10L, snapshot.getDepartmentId());
        assertEquals("PPC", snapshot.getDepartmentName());
        assertEquals("DOING", snapshot.getColumnName());
        assertEquals("General Task", TaskSnapshotResponse.from(snapshot).boardName());
    }

    @Test
    void managerCanApproveGeneralTaskReviewWithoutAProject() {
        Task general = generalTask(TaskStatus.REVIEW);
        when(tasks.findById(100L)).thenReturn(Optional.of(general));

        var response = service.reviewAction(
                100L, new ReviewActionRequest(ReviewAction.APPROVE), ppcManager);

        assertEquals(TaskStatus.DONE, response.status());
        assertNull(general.getColumn());
        verify(notifications).notifyReviewResult(general, ppcManager, true);
    }

    @Test
    void tasksKeepAChosenColourAndRejectUnknownOnes() {
        when(users.findById(1L)).thenReturn(Optional.of(ppcStaff));
        assertEquals("blue", service.createTask(new CreateTaskRequest("Coloured", null, Priority.LOW, null, null, 10L, 1L, 3, " Blue "), ppcStaff).color());
        assertNull(service.createTask(request(null, 10L, 1L), ppcStaff).color());
        assertThrows(ResponseStatusException.class, () -> service.createTask(new CreateTaskRequest("Coloured", null, Priority.LOW, null, null, 10L, 1L, 3, "#00ff00"), ppcStaff));
    }

    private CreateTaskRequest request(Long columnId, Long departmentId, Long assigneeId) {
        return new CreateTaskRequest("Follow up supplier quotation", "Call supplier", Priority.HIGH,
                LocalDate.of(2026, 8, 22), columnId, departmentId, assigneeId, 3);
    }

    private Task generalTask(TaskStatus status) {
        Task task = new Task("General", "", Priority.MEDIUM, LocalDate.of(2026, 8, 21), 1, null, ppcStaff);
        task.setDepartment(ppc);
        task.setCreatedBy(ppcManager);
        task.setStatus(status);
        task.setWorkload(4);
        ReflectionTestUtils.setField(task, "id", 100L);
        return task;
    }

    private Task projectTask() {
        Board board = entity(new Board("Production Plan", "", ppc), 30L);
        KanbanColumn column = entity(new KanbanColumn("To Do", 1, board), 40L);
        Task task = new Task("Project", "", Priority.MEDIUM, null, 1, column, ppcStaff);
        task.setCreatedBy(ppcManager);
        task.setStatus(TaskStatus.DRAFT);
        task.setWorkload(2);
        ReflectionTestUtils.setField(task, "id", 101L);
        return task;
    }

    private static <T> T entity(T value, Long id) {
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
