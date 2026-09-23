package com.badwolfmc.guardian.paper;

enum PaperAuthorityMode {
    STANDALONE,
    VELOCITY;

    static PaperAuthorityMode parse(String value) {
        if (value == null) {
            return STANDALONE;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "standalone" -> STANDALONE;
            case "velocity" -> VELOCITY;
            default -> throw new IllegalArgumentException(
                "phase0.authority must be 'standalone' or 'velocity', not '" + value + "'");
        };
    }
}
