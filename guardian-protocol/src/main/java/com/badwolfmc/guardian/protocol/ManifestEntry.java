package com.badwolfmc.guardian.protocol;

import java.util.Objects;

public record ManifestEntry(
    String modId,
    String version,
    String parentModId,
    OriginKind originKind,
    ArtifactSha256 artifactSha256
) {
    public ManifestEntry {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(originKind, "originKind");
        if (modId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("modId/version must not be blank");
        }
        if (parentModId != null && parentModId.isBlank()) {
            throw new IllegalArgumentException("parentModId must be null or non-blank");
        }
    }

    public ManifestEntry(String modId, String version, String parentModId, OriginKind originKind) {
        this(modId, version, parentModId, originKind, null);
    }
}
