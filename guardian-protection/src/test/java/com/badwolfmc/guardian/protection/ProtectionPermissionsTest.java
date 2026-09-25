package com.badwolfmc.guardian.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionPermissionsTest {
    @Test
    void exposesOnlyGuardianProtectionNamespace() {
        assertTrue(ProtectionPermissions.BYPASS.startsWith("guardian.protection."));
        assertTrue(ProtectionPermissions.COMMAND_BYPASS.startsWith("guardian.protection."));
        assertTrue(ProtectionPermissions.NAMESPACE_BYPASS.startsWith("guardian.protection."));
        assertTrue(ProtectionPermissions.VISIBILITY_BYPASS.startsWith("guardian.protection."));
        assertTrue(ProtectionPermissions.NOTIFY.startsWith("guardian.protection."));
        assertFalse(ProtectionPermissions.BYPASS.startsWith("ezprotector."));
    }

    @Test
    void dynamicVisibilityPermissionIsBoundedAndCanonical() {
        String normal = ProtectionPermissions.visibilityBypass("worldedit");
        assertEquals("guardian.protection.visibility.bypass.worldedit", normal);
        assertTrue(normal.length() <= ProtectionPermissions.MAX_PERMISSION_LENGTH);

        String namespaced = ProtectionPermissions.visibilityBypass("Bukkit:Plugins");
        assertEquals("guardian.protection.visibility.bypass.bukkit_3aplugins", namespaced);
        assertTrue(namespaced.length() <= ProtectionPermissions.MAX_PERMISSION_LENGTH);

        String longRoot = ProtectionPermissions.visibilityBypass("a".repeat(96));
        assertTrue(longRoot.startsWith("guardian.protection.visibility.bypass.sha256-"));
        assertTrue(longRoot.length() <= ProtectionPermissions.MAX_PERMISSION_LENGTH);
    }
}
