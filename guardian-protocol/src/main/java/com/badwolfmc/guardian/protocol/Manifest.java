package com.badwolfmc.guardian.protocol;

import java.util.List;
import java.util.Objects;

public record Manifest(String minecraftVersion, String fabricLoaderVersion, String cerberusVersion,
                       long capabilities, List<ManifestEntry> entries) {
    public Manifest {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(fabricLoaderVersion, "fabricLoaderVersion");
        Objects.requireNonNull(cerberusVersion, "cerberusVersion");
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
