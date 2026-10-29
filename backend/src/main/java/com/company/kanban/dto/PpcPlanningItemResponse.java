package com.company.kanban.dto;

import com.company.kanban.entity.PpcPlanningItem;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PpcPlanningItemResponse(
        Long id, String title, String description, LocalDate startDate, LocalDate endDate,
        boolean allDay, String priority, String status, Long linkedTaskId, Long createdById, String createdByName,
        Long departmentId, String departmentName,
        LocalDateTime createdAt, LocalDateTime updatedAt, String color
) {
    public static PpcPlanningItemResponse from(PpcPlanningItem item) {
        return new PpcPlanningItemResponse(item.getId(), item.getTitle(), item.getDescription(),
                item.getStartDate(), item.getEndDate(), item.isAllDay(), item.getPriority(), item.getStatus(), item.getLinkedTaskId(),
                item.getCreatedBy().getId(), item.getCreatedBy().getName(),
                item.getDepartment() == null ? null : item.getDepartment().getId(), item.getDepartment() == null ? null : item.getDepartment().getName(),
                item.getCreatedAt(), item.getUpdatedAt(), item.getColor());
    }
}
