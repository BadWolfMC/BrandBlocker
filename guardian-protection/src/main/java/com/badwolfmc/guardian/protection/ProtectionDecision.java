package com.badwolfmc.guardian.protection;

import java.util.Objects;
import java.util.Optional;

public record ProtectionDecision(
    ProtectionOutcome outcome,
    ProtectionReason reason,
    ProtectionSurface surface,
    CommandRoot root,
    ProtectionRule rule,
    Optional<String> bypassPermission
) {
    public ProtectionDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(rule, "rule");
        bypassPermission = Objects.requireNonNull(bypassPermission, "bypassPermission");
        if (rule.surface() != surface) {
            throw new IllegalArgumentException(
                "Protection decision surface " + surface + " does not match rule surface " + rule.surface());
        }
    }

    public boolean denied() {
        return outcome == ProtectionOutcome.DENY;
    }
}
