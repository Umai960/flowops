package com.company.kanban.dto;

import com.company.kanban.entity.Priority;
import com.company.kanban.entity.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(

        Long id,
        String title,
        String description,
        Priority priority,
        TaskStatus status,
        Integer workload,
        LocalDate dueDate,
        Integer position,

        Long boardId,
        String boardName,

        Long columnId,
        String columnName,

        Long assigneeId,
        String assigneeName,

        Long createdById,
        String createdByName,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,

        Long departmentId,
        String departmentName,
        boolean generalTask,
        String color

) {
}
