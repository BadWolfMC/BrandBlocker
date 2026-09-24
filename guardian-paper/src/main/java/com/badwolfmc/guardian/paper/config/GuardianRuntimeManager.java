package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.paper.locale.GuardianLocaleLoader;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Parse -> validate -> immutable candidate -> atomic activation. */
public final class GuardianRuntimeManager {
    private final Path configPath;
    private final Path localesDirectory;
    private final GuardianConfigLoader configLoader;
    private final GuardianLocaleLoader localeLoader;
    private final AtomicReference<GuardianRuntimeSnapshot> active = new AtomicReference<>();

    public GuardianRuntimeManager(Path configPath, Path localesDirectory) {
        this(configPath, localesDirectory, new GuardianConfigLoader(), new GuardianLocaleLoader());
    }

    GuardianRuntimeManager(
        Path configPath,
        Path localesDirectory,
        GuardianConfigLoader configLoader,
        GuardianLocaleLoader localeLoader
    ) {
        this.configPath = configPath;
        this.localesDirectory = localesDirectory;
        this.configLoader = configLoader;
        this.localeLoader = localeLoader;
    }

    public GuardianRuntimeSnapshot loadInitial() throws GuardianConfigurationException {
        if (active.get() != null) {
            throw new IllegalStateException("Guardian runtime snapshot is already active");
        }
        GuardianRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    public GuardianRuntimeSnapshot reload() throws GuardianConfigurationException {
        if (active.get() == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        GuardianRuntimeSnapshot candidate = loadCandidate();
        active.set(candidate);
        return candidate;
    }

    public GuardianRuntimeSnapshot current() {
        GuardianRuntimeSnapshot snapshot = active.get();
        if (snapshot == null) {
            throw new IllegalStateException("Guardian runtime snapshot has not been initialized");
        }
        return snapshot;
    }

    private GuardianRuntimeSnapshot loadCandidate() throws GuardianConfigurationException {
        GuardianPaperSettings settings = configLoader.load(configPath);
        return new GuardianRuntimeSnapshot(
            settings,
            localeLoader.load(localesDirectory, settings.locale())
        );
    }
}
