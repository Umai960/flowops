package com.company.kanban.dto;

import com.company.kanban.entity.MachineStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MachineRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 120) String location,
        @Size(max = 120) String model,
        @Size(max = 120) String serialNumber,
        MachineStatus status,
        @Size(max = 2000) String notes,
        Boolean active
) {
}
