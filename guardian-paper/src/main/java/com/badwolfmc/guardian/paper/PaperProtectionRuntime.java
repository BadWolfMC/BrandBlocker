package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.protection.ProtectionPermissions;

/**
 * Phase 1A Paper host for the independent Protection domain.
 *
 * <p>No command execution/visibility behavior is installed here; that is Phase 1B.</p>
 */
final class PaperProtectionRuntime {
    private final GuardianPaperPlugin plugin;

    PaperProtectionRuntime(GuardianPaperPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        plugin.getLogger().info("Guardian Protection domain enabled (Phase 1A foundation; enforcement begins in Phase 1B). "
            + "Permission root=" + ProtectionPermissions.ROOT);
    }

    void disable() {
        // No listeners or tasks exist until Phase 1B.
    }
}
