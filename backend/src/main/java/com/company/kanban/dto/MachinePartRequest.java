package com.company.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MachinePartRequest(
        @NotBlank @Size(max = 160) String partName,
        @Size(max = 80) String partNumber,
        @NotNull LocalDate installedOn,
        LocalDate expiryDate,
        @Size(max = 1000) String notes
) {
}
