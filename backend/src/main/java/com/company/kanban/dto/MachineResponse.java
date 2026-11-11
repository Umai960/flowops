package com.company.kanban.dto;

import com.company.kanban.entity.Machine;
import com.company.kanban.entity.MachineStatus;

import java.time.LocalDateTime;

public record MachineResponse(Long id, String code, String name, String location, String model, String serialNumber,
                              MachineStatus status, String notes, boolean active, long openBreakdowns,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static MachineResponse from(Machine m, long openBreakdowns) {
        return new MachineResponse(m.getId(), m.getCode(), m.getName(), m.getLocation(), m.getModel(), m.getSerialNumber(),
                m.getStatus(), m.getNotes(), m.isActive(), openBreakdowns, m.getCreatedAt(), m.getUpdatedAt());
    }
}
