package com.badwolfmc.guardian.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandRootNormalizerTest {
    @Test
    void normalizesCaseSlashAndArgumentsDeterministically() {
        CommandRoot root = CommandRootNormalizer.normalize(" /BuKkIt:PlUgInS   extra arguments ");
        assertEquals("bukkit:plugins", root.value());
        assertTrue(root.namespaced());
        assertEquals("bukkit", root.namespace());
        assertEquals("plugins", root.label());
        assertEquals("bukkit_3aplugins", root.permissionKey());

        assertEquals(root, CommandRootNormalizer.normalize("bukkit:plugins"));
    }

    @Test
    void stripsExactlyOneTransportSlash() {
        assertEquals("plugins", CommandRootNormalizer.normalize("/plugins").value());
        assertEquals("/wand", CommandRootNormalizer.normalize("//wand").value());
    }

    @Test
    void supportsLegacyQuestionMarkRootWithoutUnsafePermissionSuffix() {
        CommandRoot root = CommandRootNormalizer.normalize("?");
        assertEquals("?", root.value());
        assertEquals("_3f", root.permissionKey());
        assertEquals("guardian.protection.visibility.bypass._3f",
            ProtectionPermissions.visibilityBypass(root));
    }


    @Test
    void ruleConstructorCannotBypassNormalizationOrDuplicateDetection() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new ProtectionRule(
                ProtectionSurface.COMMAND_VISIBILITY,
                true,
                ProtectionRuleMode.DENYLIST,
                java.util.Set.of("Plugins", "/PLUGINS")
            ));
        assertTrue(ex.getMessage().contains("duplicate normalized command root 'plugins'"));
    }

    @Test
    void rejectsBlankAndOversizedRoots() {
        assertThrows(IllegalArgumentException.class, () -> CommandRootNormalizer.normalize(" / "));
        assertThrows(IllegalArgumentException.class,
            () -> CommandRootNormalizer.normalize("a".repeat(CommandRootNormalizer.MAX_ROOT_LENGTH + 1)));
    }
}
