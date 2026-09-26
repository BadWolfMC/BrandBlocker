package com.badwolfmc.guardian.core.artifact;

public record ArtifactImportResult(
    int scannedJars,
    int discoveredArtifacts,
    int addedCatalogEntries,
    int totalCatalogEntries,
    boolean catalogChanged
) {}
