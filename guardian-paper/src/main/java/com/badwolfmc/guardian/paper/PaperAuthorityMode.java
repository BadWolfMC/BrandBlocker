package com.badwolfmc.guardian.paper;

import java.util.Locale;

public enum PaperAuthorityMode {
    STANDALONE,
    VELOCITY;

    public static PaperAuthorityMode parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("admission.authority is required");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "standalone" -> STANDALONE;
            case "velocity" -> VELOCITY;
            default -> throw new IllegalArgumentException(
                "admission.authority must be 'standalone' or 'velocity', not '" + value + "'");
        };
    }
}
