package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperProtectionArchitectureTest {
    @Test
    void protectionInterceptsOnlyTheSupportedPlayerCommandPath() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperProtectionRuntime.java"));

        assertTrue(source.contains("PlayerCommandPreprocessEvent"));
        assertFalse(source.contains("ServerCommandEvent"),
            "Protection must not intercept console, command-block, or other non-player commands");
        assertFalse(source.contains("dispatchCommand("),
            "Protection must not redispatch or wrap commands through another sender path");
    }

    @Test
    void rootTreeAndSuggestionsUseSupportedPaperSurfacesAndSharedDecision() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperProtectionRuntime.java"));

        assertTrue(source.contains("PlayerCommandSendEvent"));
        assertTrue(source.contains("AsyncPlayerSendSuggestionsEvent"));
        assertTrue(source.contains("evaluateVisibility(root, permissions)"));
        assertTrue(source.contains("evaluateVisibility(buffer, permissions)"));
        assertFalse(source.contains("startsWith(\"/\")"),
            "suggestion suppression must preserve the slash/no-slash normalization invariant");
        assertTrue(source.contains("commandTreeVisibilityDiffersFrom"));
        assertTrue(source.contains("refreshOnlineCommandTrees()"));
        assertTrue(source.contains("player.updateCommands()"));
    }

    @Test
    void protectionAdapterAvoidsUnsupportedInternals() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/com/badwolfmc/guardian/paper/PaperProtectionRuntime.java"));

        assertFalse(source.contains("net.minecraft."));
        assertFalse(source.contains("org.bukkit.craftbukkit"));
        assertFalse(source.contains("Class.forName("));
        assertFalse(source.contains("getDeclared"));
    }
}
