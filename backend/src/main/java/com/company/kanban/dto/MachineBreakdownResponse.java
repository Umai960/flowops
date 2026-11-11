package com.company.kanban.dto;

import com.company.kanban.entity.MachineBreakdown;
import com.company.kanban.entity.MachineBreakdownStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/** downtimeMinutes runs from the report until completion, or until now while still open. */
public record MachineBreakdownResponse(Long id, Long machineId, String machineCode, String machineName, String problem,
                                       String actionTaken, MachineBreakdownStatus status, Long reportedById, String reportedByName,
                                       Long completedById, String completedByName, LocalDateTime reportedAt,
                                       LocalDateTime startedAt, LocalDateTime completedAt, long downtimeMinutes) {
    public static MachineBreakdownResponse from(MachineBreakdown b, LocalDateTime now) {
        LocalDateTime end = b.getCompletedAt() == null ? now : b.getCompletedAt();
        long minutes = Math.max(0, Duration.between(b.getReportedAt(), end).toMinutes());
        var m = b.getMachine(); var reporter = b.getReportedBy(); var completer = b.getCompletedBy();
        return new MachineBreakdownResponse(b.getId(), m.getId(), m.getCode(), m.getName(), b.getProblem(), b.getActionTaken(),
                b.getStatus(), reporter.getId(), reporter.getName(), completer == null ? null : completer.getId(),
                completer == null ? null : completer.getName(), b.getReportedAt(), b.getStartedAt(), b.getCompletedAt(), minutes);
    }
}
