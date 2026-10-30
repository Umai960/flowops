package com.company.kanban.dto;

import java.util.List;

public record GanttProjectResponse(

        Long id,
        String name,
        Long departmentId,
        String departmentName,
        List<TaskResponse> tasks

) {
}
