package com.badwolfmc.guardian.protection;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ProtectionArchitectureBoundaryTest {
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
        "org.bukkit.",
        "io.papermc.",
        "com.velocitypowered.",
        "net.fabricmc.",
        "org.geysermc.",
        "org.geysermc.floodgate.",
        "com.badwolfmc.guardian.core."
    );

    @Test
    void protectionDomainRemainsPlatformNeutralAndIndependentFromAdmissionCore() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    assertFalse(source.contains(forbidden),
                        () -> file + " must not reference " + forbidden);
                }
            }
        }
    }
}
