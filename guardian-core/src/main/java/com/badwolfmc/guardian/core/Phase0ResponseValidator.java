package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Response;

import java.util.Arrays;
import java.util.Objects;

/**
 * Platform-neutral validation for the Phase 0A challenge response.
 *
 * <p>This intentionally stops short of the production protocol design. It exists so the
 * prototype's failure distinctions can be tested without Paper or Fabric.</p>
 */
public final class Phase0ResponseValidator {
    private Phase0ResponseValidator() {
    }

    public static GuardianDecision validate(byte[] expectedNonce, Response response) {
        Objects.requireNonNull(expectedNonce, "expectedNonce");
        Objects.requireNonNull(response, "response");

        if (expectedNonce.length != GuardianProtocol.NONCE_BYTES) {
            return GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                "server challenge nonce has invalid length");
        }
        if (response.protocolVersion() != GuardianProtocol.VERSION) {
            return GuardianDecision.deny(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "client protocol=" + response.protocolVersion() + ", server protocol=" + GuardianProtocol.VERSION);
        }
        if (!Arrays.equals(expectedNonce, response.nonce())) {
            return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID, "challenge nonce mismatch");
        }
        return Phase0ManifestEvaluator.evaluate(response);
    }
}
