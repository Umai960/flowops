package com.company.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MachineBreakdownRequest(@NotBlank @Size(max = 2000) String problem) {
}
