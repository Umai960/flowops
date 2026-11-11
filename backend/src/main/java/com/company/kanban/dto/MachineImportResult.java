package com.company.kanban.dto;

import java.util.List;

public record MachineImportResult(int created, int updated, List<String> errors) {
}
