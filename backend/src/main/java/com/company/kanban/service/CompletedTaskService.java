package com.company.kanban.service;

import com.company.kanban.dto.TaskResponse;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.Task;
import com.company.kanban.entity.User;
import com.company.kanban.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class CompletedTaskService {
    private static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final TaskRepository tasks;
    private final AuthorizationService authorization;
    public CompletedTaskService(TaskRepository tasks, AuthorizationService authorization) { this.tasks = tasks; this.authorization = authorization; }

    @Transactional(readOnly = true)
    public Page<TaskResponse> find(LocalDate from, LocalDate to, User user, Pageable pageable) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        Long departmentId = user.getRole() == Role.ADMIN ? 0L : user.getDepartment().getId();
        Long assigneeId = user.getRole() == Role.STAFF ? user.getId() : 0L;
        LocalDateTime start = from == null ? LocalDateTime.of(1, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime end = to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to.plusDays(1).atStartOfDay();
        return tasks.findCompletedHistory(start, end, assigneeId, departmentId, from == null && to == null, pageable).map(this::toResponse);
    }

    private TaskResponse toResponse(Task t) {
        var c = t.getColumn(); var a = t.getAssignee(); var creator = t.getCreatedBy(); var d = t.getDepartment();
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(), t.getWorkload(), t.getDueDate(), t.getPosition(),
                c == null ? null : c.getBoard().getId(), c == null ? "General Task" : c.getBoard().getName(), c == null ? null : c.getId(), c == null ? null : c.getName(),
                a == null ? null : a.getId(), a == null ? null : a.getName(), creator == null ? null : creator.getId(), creator == null ? null : creator.getName(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt(), d == null ? null : d.getId(), d == null ? null : d.getName(), t.isGeneralTask(), t.getColor());
    }
}
