package com.badwolfmc.guardian.protocol;

import java.util.Objects;

public record ManifestEntry(String modId, String version) {
    public ManifestEntry {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(version, "version");
    }
}
