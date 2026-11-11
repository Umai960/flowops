package com.company.kanban.service;

import java.util.ArrayList;
import java.util.List;

/** Minimal CSV reader for machine imports saved from Excel (comma or semicolon, quoted fields). */
final class MachineCsv {
    private MachineCsv() {}

    static List<List<String>> parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char delimiter = delimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        rows.removeIf(r -> r.stream().allMatch(String::isBlank));
        return rows;
    }

    private static char delimiter(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        return header.chars().filter(c -> c == ';').count() > header.chars().filter(c -> c == ',').count() ? ';' : ',';
    }
}
