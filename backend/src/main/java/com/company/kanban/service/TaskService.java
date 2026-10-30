package com.company.kanban.service;

import com.company.kanban.dto.CreateTaskRequest;
import com.company.kanban.dto.MoveTaskRequest;
import com.company.kanban.dto.TaskResponse;
import com.company.kanban.dto.UpdateTaskRequest;
import com.company.kanban.dto.UpdateTaskStatusRequest;
import com.company.kanban.dto.ReviewAction;
import com.company.kanban.dto.ReviewActionRequest;
import com.company.kanban.dto.ReassignTaskRequest;
import com.company.kanban.dto.ReviewQueueItem;
import com.company.kanban.entity.KanbanColumn;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.Task;
import com.company.kanban.entity.TaskStatus;
import com.company.kanban.entity.User;
import com.company.kanban.repository.KanbanColumnRepository;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.TaskCheckpointRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final KanbanColumnRepository kanbanColumnRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final int doneVisibleDays;

    @Autowired
    public TaskService(
            TaskRepository taskRepository,
            KanbanColumnRepository kanbanColumnRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            AuthorizationService authorizationService,
            NotificationService notificationService,
            @Value("${flowops.retention.completed-task-visible-days:${app.tasks.done-visible-days:7}}") int doneVisibleDays) {

        this.taskRepository = taskRepository;
        this.kanbanColumnRepository = kanbanColumnRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.doneVisibleDays = doneVisibleDays;
    }

    // Optional so unit tests that build TaskService directly do not need the chat module.
    private ChatService chatService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setChatService(ChatService chatService) { this.chatService = chatService; }

    private TaskCheckpointRepository checkpointRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCheckpointRepository(TaskCheckpointRepository checkpointRepository) { this.checkpointRepository = checkpointRepository; }

    TaskService(TaskRepository taskRepository, KanbanColumnRepository kanbanColumnRepository,
                UserRepository userRepository, DepartmentRepository departmentRepository,
                AuthorizationService authorizationService, NotificationService notificationService) {
        this(taskRepository, kanbanColumnRepository, userRepository, departmentRepository,
                authorizationService, notificationService, 7);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByColumn(Long columnId, User currentUser) {

        KanbanColumn column = kanbanColumnRepository.findById(columnId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Column not found"));
        authorizationService.requireColumnAccess(currentUser, column);

        return taskRepository
                .findActiveByColumnId(columnId, activeDoneCutoff())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(User currentUser) {

        return taskRepository
                .findActiveByAssigneeId(currentUser.getId(), activeDoneCutoff())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByUser(Long userId, User currentUser) {
        User staffUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        authorizationService.requireStaffViewerAccess(currentUser, staffUser);

        return taskRepository.findActiveByAssigneeId(userId, activeDoneCutoff())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByDepartment(Long departmentId, User currentUser) {
        authorizationService.requireDepartmentAccess(currentUser, departmentId);
        return taskRepository.findActiveByEffectiveDepartmentId(departmentId, activeDoneCutoff()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, User currentUser) {

        boolean generalTask = request.columnId() == null;
        KanbanColumn column = null;
        Department taskDepartment;
        if (generalTask) {
            if (request.departmentId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required for a general task");
            }
            taskDepartment = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
            authorizationService.requireGeneralTaskCreation(currentUser, taskDepartment);
        } else {
            column = kanbanColumnRepository.findById(request.columnId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Column not found"));
            authorizationService.requireColumnAccess(currentUser, column);
            taskDepartment = column.getBoard().getDepartment();
        }

        if (request.assigneeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee is required");
        }

        User assignee = currentUser.getRole() == com.company.kanban.entity.Role.STAFF
                ? currentUser
                : null;

        if (request.assigneeId() != null) {
            assignee = userRepository
                    .findById(request.assigneeId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Assignee not found"
                            )
                    );
            authorizationService.requireAssignableUser(currentUser, assignee);
            authorizationService.requireAssigneeMatchesTaskDepartment(
                    assignee,
                    taskDepartment
            );
        }

        int position = generalTask
                ? taskRepository.countByColumnIsNullAndStatus(TaskStatus.DRAFT) + 1
                : taskRepository.countByColumnId(column.getId()) + 1;

        Task task = new Task(
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate(),
                position,
                column,
                assignee
        );
        task.setCreatedBy(currentUser);
        if (generalTask) task.setDepartment(taskDepartment);
        task.setWorkload(request.workload());
        task.setColor(ItemColors.normalize(request.color()));
        task.setStatus(generalTask ? TaskStatus.DRAFT : statusFromColumn(column));

        Task savedTask = taskRepository.save(task);

        notificationService.notifyTaskAssigned(savedTask, currentUser);

        return toResponse(savedTask);
    }

    @Transactional
    public TaskResponse updateTask(
            Long taskId,
            UpdateTaskRequest request,
            User currentUser) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Task not found"
                        )
                );
        authorizationService.requireTaskAccess(currentUser, task);

        User oldAssignee = task.getAssignee();
        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        var oldPriority = task.getPriority();
        var oldDueDate = task.getDueDate();
        Integer oldWorkload = task.getWorkload();
        User assignee = null;

        if (request.assigneeId() != null) {
            assignee = userRepository
                    .findById(request.assigneeId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Assignee not found"
                            )
                    );
            authorizationService.requireAssignableUser(currentUser, assignee);
            authorizationService.requireAssigneeMatchesTaskDepartment(
                    assignee,
                    task.getDepartment()
            );
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setAssignee(assignee);
        task.setWorkload(request.workload());
        task.setColor(ItemColors.normalize(request.color()));

        Task savedTask = taskRepository.save(task);

        if (!Objects.equals(oldAssignee == null ? null : oldAssignee.getId(), assignee == null ? null : assignee.getId())) {
            if (assignee != null) notificationService.notifyTaskReassigned(savedTask, oldAssignee, assignee, currentUser);
        }

        return toResponse(savedTask);
    }

    @Transactional
    public TaskResponse moveTask(
            Long taskId,
            MoveTaskRequest request,
            User currentUser) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Task not found"
                        )
                );
        authorizationService.requireTaskOwnerMove(currentUser, task);

        KanbanColumn sourceColumn = task.getColumn();
        if (sourceColumn == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "General tasks are moved by status in My Kanban");
        }

        KanbanColumn targetColumn =
                kanbanColumnRepository.findById(request.targetColumnId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Target column not found"
                                )
                        );
        authorizationService.requireColumnAccess(currentUser, targetColumn);

        if (!sourceColumn.getBoard().getId()
                .equals(targetColumn.getBoard().getId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot move task to another board"
            );
        }

        List<Task> targetTasks =
                taskRepository
                        .findByColumnIdAndIdNotOrderByPositionAsc(
                                targetColumn.getId(),
                                taskId
                        );

        int requestedPosition = request.targetPosition();

        int targetPosition = Math.max(
                1,
                Math.min(
                        requestedPosition,
                        targetTasks.size() + 1
                )
        );

        TaskStatus previousStatus = task.getStatus();
        task.setColumn(targetColumn);
        task.setStatus(statusFromColumn(targetColumn));
        updateCompletionTimestamp(task);
        if (task.getStatus() == TaskStatus.REVIEW) task.setSubmittedForReviewAt(java.time.LocalDateTime.now());
        else task.setSubmittedForReviewAt(null);

        targetTasks.add(
                targetPosition - 1,
                task
        );

        for (int i = 0; i < targetTasks.size(); i++) {
            targetTasks.get(i).setPosition(i + 1);
        }

        taskRepository.saveAll(targetTasks);

        if (!sourceColumn.getId()
                .equals(targetColumn.getId())) {

            List<Task> sourceTasks =
                    taskRepository
                            .findByColumnIdOrderByPositionAsc(
                                    sourceColumn.getId()
                            );

            for (int i = 0; i < sourceTasks.size(); i++) {
                sourceTasks.get(i).setPosition(i + 1);
            }

            taskRepository.saveAll(sourceTasks);
        }

        if (previousStatus != TaskStatus.REVIEW && task.getStatus() == TaskStatus.REVIEW) {
            notificationService.notifyReviewSubmitted(task, currentUser);
        }

        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTaskStatus(
            Long taskId,
            UpdateTaskStatusRequest request,
            User currentUser) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Task not found"));
        authorizationService.requirePersonalStatusMove(
                currentUser,
                task,
                request.status()
        );

        KanbanColumn sourceColumn = task.getColumn();
        if (sourceColumn == null) {
            return updateGeneralTaskStatus(task, request, currentUser);
        }
        String targetColumnName = columnNameFromStatus(request.status());
        KanbanColumn targetColumn = kanbanColumnRepository
                .findByBoardIdAndName(
                        sourceColumn.getBoard().getId(),
                        targetColumnName
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Target status column is not configured for this board"
                ));

        List<Task> targetTasks = taskRepository
                .findByColumnIdAndIdNotOrderByPositionAsc(
                        targetColumn.getId(), taskId);
        int targetPosition = Math.max(
                1,
                Math.min(request.targetPosition(), targetTasks.size() + 1)
        );

        TaskStatus previousStatus = task.getStatus();
        task.setColumn(targetColumn);
        task.setStatus(request.status());
        updateCompletionTimestamp(task);
        if (request.status() == TaskStatus.REVIEW) task.setSubmittedForReviewAt(java.time.LocalDateTime.now());
        if (request.status() != TaskStatus.REVIEW) task.setSubmittedForReviewAt(null);
        targetTasks.add(targetPosition - 1, task);
        normalizePositions(targetTasks);
        taskRepository.saveAll(targetTasks);

        if (!sourceColumn.getId().equals(targetColumn.getId())) {
            List<Task> sourceTasks = taskRepository
                    .findByColumnIdOrderByPositionAsc(sourceColumn.getId());
            normalizePositions(sourceTasks);
            taskRepository.saveAll(sourceTasks);
        }

        if (previousStatus != TaskStatus.REVIEW && request.status() == TaskStatus.REVIEW) {
            notificationService.notifyReviewSubmitted(task, currentUser);
        }

        return toResponse(task);
    }

    @Transactional
    public TaskResponse reviewAction(
            Long taskId,
            ReviewActionRequest request,
            User currentUser) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Task not found"));
        authorizationService.requireReviewActionAccess(currentUser, task, request.action());

        if (task.getStatus() != TaskStatus.REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review actions are only available for tasks in REVIEW"
            );
        }

        TaskStatus targetStatus = request.action() == ReviewAction.APPROVE
                ? TaskStatus.DONE
                : TaskStatus.DOING;
        KanbanColumn sourceColumn = task.getColumn();
        if (sourceColumn == null) {
            List<Task> targetTasks = new ArrayList<>(
                    taskRepository.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(targetStatus, task.getId()));
            List<Task> sourceTasks = new ArrayList<>(
                    taskRepository.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus.REVIEW, task.getId()));
            task.setStatus(targetStatus);
            updateCompletionTimestamp(task);
            task.setSubmittedForReviewAt(null);
            task.setPosition(targetTasks.size() + 1);
            targetTasks.add(task);
            normalizePositions(targetTasks);
            normalizePositions(sourceTasks);
            taskRepository.saveAll(targetTasks);
            taskRepository.saveAll(sourceTasks);
            notificationService.notifyReviewResult(task, currentUser, request.action() == ReviewAction.APPROVE);
            return toResponse(task);
        }
        KanbanColumn targetColumn = kanbanColumnRepository
                .findByBoardIdAndName(
                        sourceColumn.getBoard().getId(),
                        columnNameFromStatus(targetStatus)
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Target status column is not configured for this board"
                ));

        List<Task> targetTasks = taskRepository
                .findByColumnIdAndIdNotOrderByPositionAsc(
                        targetColumn.getId(), taskId);
        task.setColumn(targetColumn);
        task.setStatus(targetStatus);
        updateCompletionTimestamp(task);
        if (request.action() == ReviewAction.RETURN) task.setSubmittedForReviewAt(null);
        targetTasks.add(task);
        normalizePositions(targetTasks);
        taskRepository.saveAll(targetTasks);

        List<Task> sourceTasks = taskRepository
                .findByColumnIdOrderByPositionAsc(sourceColumn.getId());
        normalizePositions(sourceTasks);
        taskRepository.saveAll(sourceTasks);

        notificationService.notifyReviewResult(task, currentUser, request.action() == ReviewAction.APPROVE);

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<ReviewQueueItem> reviewQueue(User currentUser) {
        authorizationService.requireWorkloadDashboardAccess(currentUser);
        return taskRepository.findByStatusOrderBySubmittedForReviewAtAsc(TaskStatus.REVIEW).stream()
                .filter(task -> authorizationService.canAccessDepartment(currentUser, task.getDepartment().getId()))
                .map(task -> new ReviewQueueItem(task.getId(), task.getTitle(), task.getDescription(), task.getWorkload(), task.getPriority(),
                        task.getDueDate(), task.getAssignee() == null ? null : task.getAssignee().getId(),
                        task.getAssignee() == null ? null : task.getAssignee().getName(),
                        task.isGeneralTask() ? null : task.getColumn().getBoard().getId(),
                        task.isGeneralTask() ? "General Task" : task.getColumn().getBoard().getName(),
                        task.getDepartment().getId(), task.getDepartment().getName(),
                        task.getSubmittedForReviewAt(), task.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public TaskResponse reassignTask(Long taskId, ReassignTaskRequest request, User currentUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        User assignee = userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));

        authorizationService.requireTaskReassignmentAccess(currentUser, task, assignee);
        User oldAssignee = task.getAssignee();
        if (oldAssignee != null && oldAssignee.getId().equals(assignee.getId())) return toResponse(task);
        task.setAssignee(assignee);
        Task saved = taskRepository.save(task);
        notificationService.notifyTaskReassigned(saved, oldAssignee, assignee, currentUser);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTask(Long taskId, User currentUser) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Task not found"
                        )
                );
        authorizationService.requireTaskAccess(currentUser, task);

        Long columnId = task.getColumn() == null ? null : task.getColumn().getId();

        if (chatService != null) chatService.deleteForSubject(com.company.kanban.entity.ChatSubjectType.TASK, taskId);
        // RDD checkpoints reference the task, so they must go first.
        if (checkpointRepository != null) checkpointRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);

        taskRepository.flush();

        if (columnId == null) return;

        List<Task> remainingTasks = taskRepository.findByColumnIdOrderByPositionAsc(columnId);

        for (int i = 0; i < remainingTasks.size(); i++) {
            remainingTasks.get(i).setPosition(i + 1);
        }

        taskRepository.saveAll(remainingTasks);
    }

    private TaskResponse toResponse(Task task) {

        User assignee = task.getAssignee();
        User createdBy = task.getCreatedBy();
        KanbanColumn column = task.getColumn();
        Department department = task.getDepartment();

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getWorkload(),
                task.getDueDate(),
                task.getPosition(),

                column == null ? null : column.getBoard().getId(),
                column == null ? "General Task" : column.getBoard().getName(),

                column == null ? null : column.getId(),
                column == null ? null : column.getName(),

                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getName() : null,

                createdBy != null ? createdBy.getId() : null,
                createdBy != null ? createdBy.getName() : null,

                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),

                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                task.isGeneralTask(),
                task.getColor()
        );
    }

    private TaskResponse updateGeneralTaskStatus(Task task, UpdateTaskStatusRequest request, User currentUser) {
        TaskStatus previousStatus = task.getStatus();
        List<Task> targetTasks = new ArrayList<>(
                taskRepository.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(
                        request.status(), task.getId()));
        List<Task> sourceTasks = previousStatus == request.status() ? null : new ArrayList<>(
                taskRepository.findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(
                        previousStatus, task.getId()));
        int targetPosition = Math.max(1, Math.min(request.targetPosition(), targetTasks.size() + 1));
        task.setStatus(request.status());
        updateCompletionTimestamp(task);
        task.setSubmittedForReviewAt(request.status() == TaskStatus.REVIEW
                ? java.time.LocalDateTime.now() : null);
        targetTasks.add(targetPosition - 1, task);
        normalizePositions(targetTasks);
        taskRepository.saveAll(targetTasks);
        if (sourceTasks != null) {
            normalizePositions(sourceTasks);
            taskRepository.saveAll(sourceTasks);
        }
        if (previousStatus != TaskStatus.REVIEW && request.status() == TaskStatus.REVIEW) {
            notificationService.notifyReviewSubmitted(task, currentUser);
        }
        return toResponse(task);
    }

    private TaskStatus statusFromColumn(KanbanColumn column) {
        return switch (column.getName()) {
            case "To Do" -> TaskStatus.DRAFT;
            case "In Progress" -> TaskStatus.DOING;
            case "Review" -> TaskStatus.REVIEW;
            case "Done" -> TaskStatus.DONE;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported Kanban column"
            );
        };
    }

    private String columnNameFromStatus(TaskStatus status) {
        return switch (status) {
            case DRAFT -> "To Do";
            case DOING -> "In Progress";
            case REVIEW -> "Review";
            case DONE -> "Done";
        };
    }

    private void updateCompletionTimestamp(Task task) {
        if (task.getStatus() == TaskStatus.DONE && task.getCompletedAt() == null) {
            task.setCompletedAt(java.time.LocalDateTime.now());
        } else if (task.getStatus() != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
    }

    private void normalizePositions(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i + 1);
        }
    }

    private LocalDateTime activeDoneCutoff() {
        return LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
                .minusDays(doneVisibleDays).atStartOfDay();
    }
}
