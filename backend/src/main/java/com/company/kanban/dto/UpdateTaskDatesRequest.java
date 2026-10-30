package com.company.kanban.dto;

import java.time.LocalDate;

public record UpdateTaskDatesRequest(

        LocalDate startDate,

        LocalDate dueDate

) {
}
