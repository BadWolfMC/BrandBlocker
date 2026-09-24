package com.badwolfmc.guardian.paper.locale;

import java.util.Map;
import java.util.Objects;

public record GuardianLocaleCatalog(
    String selectedLocale,
    Map<String, String> selected,
    Map<String, String> fallback
) {
    public GuardianLocaleCatalog {
        Objects.requireNonNull(selectedLocale, "selectedLocale");
        selected = Map.copyOf(Objects.requireNonNull(selected, "selected"));
        fallback = Map.copyOf(Objects.requireNonNull(fallback, "fallback"));
    }

    public String template(String key) {
        String value = selected.get(key);
        if (value != null) {
            return value;
        }
        value = fallback.get(key);
        if (value == null) {
            throw new IllegalStateException("required locale key disappeared from validated catalog: " + key);
        }
        return value;
    }
}
