package com.company.kanban.service;

import com.company.kanban.dto.GanttProjectResponse;
import com.company.kanban.dto.TaskResponse;
import com.company.kanban.entity.Board;
import com.company.kanban.entity.Task;
import com.company.kanban.entity.User;
import com.company.kanban.repository.BoardRepository;
import com.company.kanban.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Projects and their tasks for the Gantt chart. Like Planning, users only see
 * their own department; administrators pick a department or see all of them.
 */
@Service
public class GanttService {

    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final AuthorizationService authorizationService;

    public GanttService(BoardRepository boardRepository, TaskRepository taskRepository,
                        TaskService taskService, AuthorizationService authorizationService) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<GanttProjectResponse> getProjects(Long departmentId, User currentUser) {
        boolean allDepartments = authorizationService.isAdmin(currentUser) && departmentId == null;
        Long scope = departmentId != null ? departmentId : currentUser.getDepartment().getId();
        if (!allDepartments) authorizationService.requireDepartmentAccess(currentUser, scope);

        List<Board> boards = allDepartments ? boardRepository.findAll() : boardRepository.findByDepartmentId(scope);
        List<Task> tasks = allDepartments ? taskRepository.findAllProjectTasks() : taskRepository.findByEffectiveDepartmentId(scope);
        Map<Long, List<Task>> tasksByBoard = tasks.stream()
                .filter(task -> !task.isGeneralTask())
                .collect(Collectors.groupingBy(task -> task.getColumn().getBoard().getId()));

        return boards.stream()
                .sorted(Comparator.comparing((Board board) -> board.getDepartment().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Board::getName, String.CASE_INSENSITIVE_ORDER))
                .map(board -> new GanttProjectResponse(
                        board.getId(),
                        board.getName(),
                        board.getDepartment().getId(),
                        board.getDepartment().getName(),
                        tasksByBoard.getOrDefault(board.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(GanttService::chartStart).thenComparing(Task::getId))
                                .map(taskService::toResponse)
                                .toList()))
                .toList();
    }

    private static LocalDate chartStart(Task task) {
        if (task.getStartDate() != null) return task.getStartDate();
        return task.getCreatedAt() == null ? LocalDate.MIN : task.getCreatedAt().toLocalDate();
    }
}
