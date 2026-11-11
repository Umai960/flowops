package com.company.kanban.dto;

import com.company.kanban.entity.MachinePart;

import java.time.LocalDate;

/** expiryStatus: NO_EXPIRY, OK, EXPIRING (within the warning window), EXPIRED or REPLACED. */
public record MachinePartResponse(Long id, Long machineId, String machineCode, String machineName, String partName, String partNumber,
                                  LocalDate installedOn, LocalDate expiryDate, String notes, Long recordedById, String recordedByName,
                                  LocalDate replacedOn, String expiryStatus, Long daysLeft) {
    public static final int WARNING_DAYS = 30;

    public static MachinePartResponse from(MachinePart p, LocalDate today) {
        Long daysLeft = p.getExpiryDate() == null ? null : p.getExpiryDate().toEpochDay() - today.toEpochDay();
        String status = p.getReplacedOn() != null ? "REPLACED"
                : daysLeft == null ? "NO_EXPIRY"
                : daysLeft < 0 ? "EXPIRED"
                : daysLeft <= WARNING_DAYS ? "EXPIRING" : "OK";
        var m = p.getMachine();
        return new MachinePartResponse(p.getId(), m.getId(), m.getCode(), m.getName(), p.getPartName(), p.getPartNumber(),
                p.getInstalledOn(), p.getExpiryDate(), p.getNotes(), p.getRecordedBy().getId(), p.getRecordedBy().getName(),
                p.getReplacedOn(), status, daysLeft);
    }
}
