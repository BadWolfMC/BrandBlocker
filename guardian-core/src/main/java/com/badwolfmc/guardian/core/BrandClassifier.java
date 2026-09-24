package com.badwolfmc.guardian.core;

import java.util.Locale;

/** Normalizes self-reported Java brands without treating them as authenticated identity. */
public final class BrandClassifier {
    private BrandClassifier() {
    }

    public static ClientClassification classify(String rawBrand) {
        String normalized = normalize(rawBrand);
        if (normalized.isEmpty()) {
            return ClientClassification.JAVA_UNKNOWN;
        }
        return switch (normalized) {
            case "vanilla" -> ClientClassification.JAVA_VANILLA;
            case "optifine" -> ClientClassification.JAVA_OPTIFINE;
            case "fabric" -> ClientClassification.JAVA_FABRIC;
            default -> ClientClassification.JAVA_UNKNOWN;
        };
    }

    public static String normalize(String rawBrand) {
        return rawBrand == null ? "" : rawBrand.trim().toLowerCase(Locale.ROOT);
    }
}
