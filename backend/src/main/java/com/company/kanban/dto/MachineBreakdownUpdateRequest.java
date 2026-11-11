package com.company.kanban.dto;

import com.company.kanban.entity.MachineBreakdownStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MachineBreakdownUpdateRequest(@NotNull MachineBreakdownStatus status, @Size(max = 2000) String actionTaken) {
}
