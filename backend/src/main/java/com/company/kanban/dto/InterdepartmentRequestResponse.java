package com.company.kanban.dto;
import com.company.kanban.entity.*;
import java.time.*;
import java.util.List;
public record InterdepartmentRequestResponse(Long id, String requestingDepartment, String targetDepartment, Long createdById, String createdByName, Long assignedToId, String assignedToName, String title, String description, Priority priority, LocalDate neededBy, InterdepartmentRequestStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime acknowledgedAt, LocalDateTime completedAt, String rejectionReason, Long linkedTaskId, LocalDateTime archivedAt, List<StatusHistory> history, List<Attachment> attachments) {
    public record StatusHistory(InterdepartmentRequestStatus fromStatus, InterdepartmentRequestStatus toStatus, Long changedById, String changedByName, LocalDateTime changedAt, String note) {}
    public record Attachment(Long id, String fileName, String contentType, long size, Long uploadedById, String uploadedByName, LocalDateTime uploadedAt) {}
}
