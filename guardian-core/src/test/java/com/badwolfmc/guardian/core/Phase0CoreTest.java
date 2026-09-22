package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Phase0CoreTest {
    @Test
    void classifiesOnlyExactNormalizedFabricAndVanillaBrands() {
        assertEquals(ClientClassification.JAVA_VANILLA, BrandClassifier.classify("vanilla"));
        assertEquals(ClientClassification.JAVA_VANILLA, BrandClassifier.classify(" VANILLA "));
        assertEquals(ClientClassification.JAVA_FABRIC, BrandClassifier.classify("fabric"));
        assertEquals(ClientClassification.JAVA_UNKNOWN, BrandClassifier.classify("fabric-but-not-really"));
        assertEquals(ClientClassification.JAVA_UNKNOWN, BrandClassifier.classify(null));
    }

    @Test
    void allowsOrdinaryPhase0Manifest() {
        byte[] nonce = nonce();
        GuardianDecision result = Phase0ResponseValidator.validate(nonce,
            new Response(GuardianProtocol.VERSION, nonce,
                List.of(new ManifestEntry("cerberus", "0.0.1-phase0a"))));

        assertEquals(DecisionOutcome.ALLOW, result.outcome());
        assertEquals(DecisionReason.CERBERUS_VERIFIED, result.reason());
    }

    @Test
    void deliberateMarkerProducesManifestDenied() {
        byte[] nonce = nonce();
        GuardianDecision result = Phase0ResponseValidator.validate(nonce,
            new Response(GuardianProtocol.VERSION, nonce,
                List.of(new ManifestEntry(GuardianProtocol.PHASE0_DENY_MOD_ID, "1"))));

        assertEquals(DecisionOutcome.DENY, result.outcome());
        assertEquals(DecisionReason.MANIFEST_DENIED, result.reason());
    }

    @Test
    void unsupportedProtocolRemainsDistinct() {
        byte[] nonce = nonce();
        GuardianDecision result = Phase0ResponseValidator.validate(nonce,
            new Response(GuardianProtocol.VERSION + 1, nonce, List.of()));

        assertEquals(DecisionOutcome.DENY, result.outcome());
        assertEquals(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED, result.reason());
    }

    @Test
    void mismatchedNonceIsInvalidRatherThanPolicyDenied() {
        byte[] expected = nonce();
        byte[] wrong = nonce();
        wrong[0] = (byte) (wrong[0] + 1);

        GuardianDecision result = Phase0ResponseValidator.validate(expected,
            new Response(GuardianProtocol.VERSION, wrong, List.of()));

        assertEquals(DecisionOutcome.DENY, result.outcome());
        assertEquals(DecisionReason.MANIFEST_INVALID, result.reason());
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) i;
        }
        return nonce;
    }
}
