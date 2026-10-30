package com.company.kanban.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

/** The fixed colours people can give planning items and task cards. */
final class ItemColors {
    private static final Set<String> COLORS = Set.of("blue", "green", "orange", "red", "purple", "yellow", "pink", "gray");

    private ItemColors() {}

    /** Returns the colour key, null for the default colour, or 400 for anything else. */
    static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown colour");
        return key;
    }
}
