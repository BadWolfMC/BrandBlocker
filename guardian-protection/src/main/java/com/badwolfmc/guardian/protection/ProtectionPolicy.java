package com.badwolfmc.guardian.protection;

import java.util.List;
import java.util.Objects;

/** Complete immutable Guardian Protection policy activated by the Paper host. */
public record ProtectionPolicy(
    ProtectionRule execution,
    ProtectionRule visibility,
    ProtectionRule namespaces,
    boolean perCommandVisibilityBypass,
    boolean notificationsEnabled
) {
    public ProtectionPolicy {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(namespaces, "namespaces");
        requireSurface(execution, ProtectionSurface.COMMAND_EXECUTION);
        requireSurface(visibility, ProtectionSurface.COMMAND_VISIBILITY);
        requireSurface(namespaces, ProtectionSurface.NAMESPACED_COMMAND);
    }

    /** Whether activating this policy changes what Guardian may advertise to command clients. */
    public boolean commandTreeVisibilityDiffersFrom(ProtectionPolicy other) {
        Objects.requireNonNull(other, "other");
        return !visibility.equals(other.visibility)
            || perCommandVisibilityBypass != other.perCommandVisibilityBypass;
    }

    public static ProtectionPolicy disabled() {
        return new ProtectionPolicy(
            ProtectionRule.create(ProtectionSurface.COMMAND_EXECUTION, false, ProtectionRuleMode.DENYLIST, List.of()),
            ProtectionRule.create(ProtectionSurface.COMMAND_VISIBILITY, false, ProtectionRuleMode.DENYLIST, List.of()),
            ProtectionRule.create(ProtectionSurface.NAMESPACED_COMMAND, false, ProtectionRuleMode.DENYLIST, List.of()),
            false,
            false
        );
    }

    private static void requireSurface(ProtectionRule rule, ProtectionSurface expected) {
        if (rule.surface() != expected) {
            throw new IllegalArgumentException("expected " + expected + " rule, got " + rule.surface());
        }
    }
}
