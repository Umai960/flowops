package com.company.kanban.dto;

import com.company.kanban.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(

        @NotBlank
        String title,

        String description,

        @NotNull
        Priority priority,

        LocalDate dueDate,

        Long columnId,

        Long departmentId,

        @NotNull(message = "assigneeId is required")
        Long assigneeId,

        @NotNull
        @Min(1)
        @Max(5)
        Integer workload,

        @Size(max = 16)
        String color

) {
    public CreateTaskRequest(String title, String description, Priority priority, LocalDate dueDate, Long columnId, Long departmentId, Long assigneeId, Integer workload) {
        this(title, description, priority, dueDate, columnId, departmentId, assigneeId, workload, null);
    }
}
