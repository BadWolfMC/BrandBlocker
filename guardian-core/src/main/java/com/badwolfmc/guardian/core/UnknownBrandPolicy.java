package com.badwolfmc.guardian.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Exact normalized brand rules used only for JAVA_UNKNOWN connections. */
public record UnknownBrandPolicy(BrandRuleMode mode, Set<String> brands) {
    public UnknownBrandPolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(brands, "brands");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String brand : brands) {
            String value = BrandClassifier.normalize(brand);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("unknown brand rule may not be blank");
            }
            if (!normalized.add(value)) {
                throw new IllegalArgumentException("duplicate unknown brand rule: " + value);
            }
        }
        brands = Set.copyOf(normalized);
    }

    /**
     * Resolves a matching raw brand, falling back to the explicit JAVA_UNKNOWN class action.
     * Config validation is responsible for ensuring the fallback agrees with the selected mode.
     */
    public ClientAction resolve(String rawBrand, ClientAction unknownFallback) {
        Objects.requireNonNull(unknownFallback, "unknownFallback");
        boolean listed = brands.contains(BrandClassifier.normalize(rawBrand));
        if (!listed) {
            return unknownFallback;
        }
        return mode == BrandRuleMode.ALLOWLIST ? ClientAction.ALLOW : ClientAction.DENY;
    }
}
