package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Response;

public final class Phase0ManifestEvaluator {
    private Phase0ManifestEvaluator() {
    }

    public static GuardianDecision evaluate(Response response) {
        boolean deliberateDeny = response.manifest().stream()
            .anyMatch(entry -> GuardianProtocol.PHASE0_DENY_MOD_ID.equals(entry.modId()));

        if (deliberateDeny) {
            return GuardianDecision.deny(
                DecisionReason.MANIFEST_DENIED,
                "Phase 0A deliberate deny marker was present"
            );
        }
        return GuardianDecision.allow(
            DecisionReason.CERBERUS_VERIFIED,
            "Cerberus response and Phase 0A test manifest validated"
        );
    }
}
