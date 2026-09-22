package com.badwolfmc.guardian.core;

import java.util.Objects;

public record GuardianDecision(DecisionOutcome outcome, DecisionReason reason, String detail) {
    public GuardianDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        detail = detail == null ? "" : detail;
    }

    public static GuardianDecision allow(DecisionReason reason, String detail) {
        return new GuardianDecision(DecisionOutcome.ALLOW, reason, detail);
    }

    public static GuardianDecision deny(DecisionReason reason, String detail) {
        return new GuardianDecision(DecisionOutcome.DENY, reason, detail);
    }
}
