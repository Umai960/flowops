package com.company.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePpcPlanningItemRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 32) String priority,
        @Size(max = 32) String status,
        @Size(max = 16) String color
) {
    public UpdatePpcPlanningItemRequest(String title, String description, LocalDate startDate, LocalDate endDate,
                                        String priority, String status) {
        this(title, description, startDate, endDate, priority, status, null);
    }
}
