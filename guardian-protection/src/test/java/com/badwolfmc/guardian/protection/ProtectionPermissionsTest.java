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
    void dynamicVisibilityPermissionIsBoundedAndNormalized() {
        String permission = ProtectionPermissions.visibilityBypass("worldedit");
        assertEquals("guardian.protection.visibility.bypass.worldedit", permission);
        assertTrue(permission.length() <= ProtectionPermissions.MAX_PERMISSION_LENGTH);
        assertThrows(IllegalArgumentException.class,
            () -> ProtectionPermissions.visibilityBypass("WorldEdit:wand /with arguments"));
    }
}
