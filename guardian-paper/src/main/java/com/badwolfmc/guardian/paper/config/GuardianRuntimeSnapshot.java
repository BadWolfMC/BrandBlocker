package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.paper.locale.GuardianLocaleCatalog;

import java.util.Objects;

public record GuardianRuntimeSnapshot(GuardianPaperSettings settings, GuardianLocaleCatalog localeCatalog) {
    public GuardianRuntimeSnapshot {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(localeCatalog, "localeCatalog");
    }
}
