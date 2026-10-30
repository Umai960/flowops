package com.company.kanban.controller;

import com.company.kanban.dto.CreateTaskRequest;
import com.company.kanban.dto.MoveTaskRequest;
import com.company.kanban.dto.TaskResponse;
import com.company.kanban.dto.UpdateTaskRequest;
import com.company.kanban.dto.UpdateTaskStatusRequest;
import com.company.kanban.dto.UpdateTaskDatesRequest;
import com.company.kanban.dto.ReviewActionRequest;
import com.company.kanban.dto.ReassignTaskRequest;
import com.company.kanban.dto.ReviewQueueItem;
import com.company.kanban.service.TaskService;
import com.company.kanban.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.kanban.service.CompletedTaskService;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final CompletedTaskService completedTaskService;

    public TaskController(TaskService taskService, CompletedTaskService completedTaskService) {
        this.taskService = taskService;
        this.completedTaskService = completedTaskService;
    }

    @GetMapping("/column/{columnId}")
    public List<TaskResponse> getTasksByColumn(
            @PathVariable Long columnId,
            @AuthenticationPrincipal User currentUser) {

        return taskService.getTasksByColumn(columnId, currentUser);
    }

    @GetMapping("/my")
    public List<TaskResponse> getMyTasks(
            @AuthenticationPrincipal User currentUser) {

        return taskService.getMyTasks(currentUser);
    }

    @GetMapping("/reviews")
    public List<ReviewQueueItem> reviewQueue(@AuthenticationPrincipal User currentUser) {
        return taskService.reviewQueue(currentUser);
    }

    @GetMapping("/user/{userId}")
    public List<TaskResponse> getTasksByUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser) {

        return taskService.getTasksByUser(userId, currentUser);
    }

    @GetMapping("/completed")
    public Page<TaskResponse> completed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable, @AuthenticationPrincipal User currentUser) {
        return completedTaskService.find(from, to, currentUser, pageable);
    }

    @GetMapping("/department/{departmentId}")
    public List<TaskResponse> getTasksByDepartment(
            @PathVariable Long departmentId,
            @AuthenticationPrincipal User currentUser) {
        return taskService.getTasksByDepartment(departmentId, currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(
            @Valid
            @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.createTask(request, currentUser);
    }

    @PutMapping("/{taskId}/move")
    public TaskResponse moveTask(
            @PathVariable Long taskId,
            @Valid @RequestBody MoveTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.moveTask(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}/status")
    public TaskResponse updateTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.updateTaskStatus(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}/review-action")
    public TaskResponse reviewAction(
            @PathVariable Long taskId,
            @Valid @RequestBody ReviewActionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.reviewAction(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}/assignee")
    public TaskResponse reassignTask(
            @PathVariable Long taskId,
            @Valid @RequestBody ReassignTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        return taskService.reassignTask(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}/dates")
    public TaskResponse updateTaskDates(
            @PathVariable Long taskId,
            @RequestBody UpdateTaskDatesRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.updateTaskDates(taskId, request, currentUser);
    }

    @PutMapping("/{taskId}")
    public TaskResponse updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {

        return taskService.updateTask(taskId, request, currentUser);
    }

    @DeleteMapping("/{taskId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User currentUser) {

        taskService.deleteTask(taskId, currentUser);
    }
}
