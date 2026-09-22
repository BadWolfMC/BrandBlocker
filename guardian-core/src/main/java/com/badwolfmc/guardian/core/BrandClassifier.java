package com.badwolfmc.guardian.core;

import java.util.Locale;

public final class BrandClassifier {
    private BrandClassifier() {
    }

    public static ClientClassification classify(String rawBrand) {
        if (rawBrand == null || rawBrand.isBlank()) {
            return ClientClassification.JAVA_UNKNOWN;
        }
        String normalized = rawBrand.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "vanilla" -> ClientClassification.JAVA_VANILLA;
            case "fabric" -> ClientClassification.JAVA_FABRIC;
            default -> ClientClassification.JAVA_UNKNOWN;
        };
    }
}
