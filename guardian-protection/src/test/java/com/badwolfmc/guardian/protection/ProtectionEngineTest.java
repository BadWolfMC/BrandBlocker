package com.badwolfmc.guardian.protection;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionEngineTest {
    @Test
    void executionPolicyDeniesConfiguredPlayerCommandRoot() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );

        ProtectionDecision denied = engine.evaluateExecution("/PLUGINS anything", none()).orElseThrow();
        assertTrue(denied.denied());
        assertEquals(ProtectionReason.EXECUTION_DENIED, denied.reason());
        assertEquals("plugins", denied.root().value());

        assertFalse(engine.evaluateExecution("/help", none()).orElseThrow().denied());
    }

    @Test
    void visibilityAllowlistExposesOnlyListedRoots() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.ALLOWLIST, "spawn", "msg"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );

        assertFalse(engine.evaluateVisibility("/spawn", none()).orElseThrow().denied());
        assertTrue(engine.evaluateVisibility("/plugins", none()).orElseThrow().denied());
    }

    @Test
    void visibilityDenylistHidesOnlyListedRoots() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );

        assertTrue(engine.evaluateVisibility("plugins", none()).orElseThrow().denied());
        assertFalse(engine.evaluateVisibility("spawn", none()).orElseThrow().denied());
    }

    @Test
    void guessedHiddenRootAndArgumentBufferUseSameVisibilityDecision() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "worldedit"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );

        ProtectionDecision tree = engine.evaluateVisibility("worldedit", none()).orElseThrow();
        ProtectionDecision suggestions = engine.evaluateVisibility("/WORLDEDIT schem load", none()).orElseThrow();
        assertEquals(tree.outcome(), suggestions.outcome());
        assertEquals(tree.reason(), suggestions.reason());
        assertEquals(tree.root(), suggestions.root());
    }

    @Test
    void namespacedAllowlistDeniesUnlistedAndPermitsConfiguredAlias() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.COMMAND_VISIBILITY, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.NAMESPACED_COMMAND, true, ProtectionRuleMode.ALLOWLIST,
                "minecraft:msg", "essentials:warp")
        );

        assertFalse(engine.evaluateExecution("/essentials:warp spawn", none()).orElseThrow().denied());
        ProtectionDecision denied = engine.evaluateExecution("/bukkit:plugins", none()).orElseThrow();
        assertTrue(denied.denied());
        assertEquals(ProtectionReason.NAMESPACE_DENIED, denied.reason());
    }

    @Test
    void namespacedDenylistBlocksOnlyListedRoots() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.COMMAND_VISIBILITY, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.NAMESPACED_COMMAND, true, ProtectionRuleMode.DENYLIST, "bukkit:plugins")
        );

        assertTrue(engine.evaluateExecution("/bukkit:plugins", none()).orElseThrow().denied());
        assertFalse(engine.evaluateExecution("/minecraft:msg hello", none()).orElseThrow().denied());
    }

    @Test
    void bypassHierarchyIsCentralizedAndFeatureScoped() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, true, ProtectionRuleMode.DENYLIST, "bukkit:plugins")
        );

        ProtectionPermissionView commandOnly = permission(ProtectionPermissions.COMMAND_BYPASS);
        assertFalse(engine.evaluateExecution("plugins", commandOnly).orElseThrow().denied());
        assertTrue(engine.evaluateVisibility("plugins", commandOnly).orElseThrow().denied());
        assertTrue(engine.evaluateExecution("bukkit:plugins", commandOnly).orElseThrow().denied());

        ProtectionPermissionView global = permission(ProtectionPermissions.BYPASS);
        assertFalse(engine.evaluateExecution("plugins", global).orElseThrow().denied());
        assertFalse(engine.evaluateVisibility("plugins", global).orElseThrow().denied());
        assertFalse(engine.evaluateExecution("bukkit:plugins", global).orElseThrow().denied());
    }

    @Test
    void perCommandVisibilityBypassAppliesToRootAndArgumentsOnly() {
        ProtectionPolicy policy = new ProtectionPolicy(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "worldedit"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "worldedit"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST),
            true,
            true
        );
        ProtectionEngine engine = new ProtectionEngine(policy);
        String bypass = ProtectionPermissions.visibilityBypass("worldedit");
        ProtectionPermissionView permissions = permission(bypass);

        ProtectionDecision tree = engine.evaluateVisibility("worldedit", permissions).orElseThrow();
        ProtectionDecision args = engine.evaluateVisibility("/worldedit wand", permissions).orElseThrow();
        assertFalse(tree.denied());
        assertFalse(args.denied());
        assertEquals(ProtectionReason.COMMAND_VISIBILITY_BYPASS, tree.reason());
        assertEquals(bypass, tree.bypassPermission().orElseThrow());

        assertTrue(engine.evaluateExecution("worldedit", permissions).orElseThrow().denied(),
            "visibility bypass must not grant execution bypass");
    }


    @Test
    void notificationPermissionNeverActsAsBypassAndBypassDoesNotImplyNotification() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, false, ProtectionRuleMode.DENYLIST),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );

        assertTrue(engine.evaluateExecution("plugins", permission(ProtectionPermissions.NOTIFY))
            .orElseThrow().denied(), "notify permission must not bypass execution policy");

        ProtectionDecision bypassed = engine.evaluateExecution(
            "plugins", permission(ProtectionPermissions.COMMAND_BYPASS)).orElseThrow();
        assertFalse(bypassed.denied());
        assertEquals(ProtectionReason.COMMAND_BYPASS, bypassed.reason());
    }

    @Test
    void legacyEzProtectorPermissionsHaveNoGuardianAuthority() {
        ProtectionEngine engine = engine(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST)
        );
        ProtectionPermissionView legacyOnly = permission("ezprotector.bypass.command.custom");

        assertTrue(engine.evaluateExecution("plugins", legacyOnly).orElseThrow().denied());
        assertTrue(engine.evaluateVisibility("plugins", legacyOnly).orElseThrow().denied());
    }

    @Test
    void onlyVisibilityContractChangesRequireCommandTreeRefresh() {
        ProtectionPolicy baseline = new ProtectionPolicy(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "plugins"),
            rule(ProtectionSurface.NAMESPACED_COMMAND, true, ProtectionRuleMode.DENYLIST, "bukkit:plugins"),
            true,
            true
        );
        ProtectionPolicy executionOnlyChange = new ProtectionPolicy(
            rule(ProtectionSurface.COMMAND_EXECUTION, true, ProtectionRuleMode.DENYLIST, "version"),
            baseline.visibility(), baseline.namespaces(), true, true);
        ProtectionPolicy notificationOnlyChange = new ProtectionPolicy(
            baseline.execution(), baseline.visibility(), baseline.namespaces(), true, false);
        ProtectionPolicy visibilityChange = new ProtectionPolicy(
            baseline.execution(),
            rule(ProtectionSurface.COMMAND_VISIBILITY, true, ProtectionRuleMode.DENYLIST, "version"),
            baseline.namespaces(), true, true);
        ProtectionPolicy perCommandBypassChange = new ProtectionPolicy(
            baseline.execution(), baseline.visibility(), baseline.namespaces(), false, true);

        assertFalse(executionOnlyChange.commandTreeVisibilityDiffersFrom(baseline));
        assertFalse(notificationOnlyChange.commandTreeVisibilityDiffersFrom(baseline));
        assertTrue(visibilityChange.commandTreeVisibilityDiffersFrom(baseline));
        assertTrue(perCommandBypassChange.commandTreeVisibilityDiffersFrom(baseline));
    }

    @Test
    void namespaceRulesRejectUnnamespacedConfiguration() {
        assertThrows(IllegalArgumentException.class, () ->
            rule(ProtectionSurface.NAMESPACED_COMMAND, true, ProtectionRuleMode.DENYLIST, "plugins"));
    }

    private static ProtectionEngine engine(
        ProtectionRule execution,
        ProtectionRule visibility,
        ProtectionRule namespaces
    ) {
        return new ProtectionEngine(new ProtectionPolicy(execution, visibility, namespaces, true, true));
    }

    private static ProtectionRule rule(
        ProtectionSurface surface,
        boolean enabled,
        ProtectionRuleMode mode,
        String... roots
    ) {
        return ProtectionRule.create(surface, enabled, mode, Set.of(roots));
    }

    private static ProtectionPermissionView none() {
        return permission();
    }

    private static ProtectionPermissionView permission(String... granted) {
        Set<String> permissions = Set.of(granted);
        return permissions::contains;
    }
}
