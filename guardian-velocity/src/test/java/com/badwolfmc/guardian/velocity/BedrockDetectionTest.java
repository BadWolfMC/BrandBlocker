package com.badwolfmc.guardian.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockDetectionTest {
    @Test
    void eitherSupportedApiMayProvidePositiveBedrockEvidence() {
        assertTrue(new BedrockDetection(BedrockSignal.BEDROCK, BedrockSignal.NOT_BEDROCK).bedrock());
        assertTrue(new BedrockDetection(BedrockSignal.UNAVAILABLE, BedrockSignal.BEDROCK).bedrock());
        assertFalse(new BedrockDetection(BedrockSignal.NOT_BEDROCK, BedrockSignal.NOT_BEDROCK).bedrock());
    }

    @Test
    void disagreementIsOnlyMeaningfulWhenBothApisAreAvailable() {
        assertTrue(new BedrockDetection(BedrockSignal.BEDROCK, BedrockSignal.NOT_BEDROCK).disagrees());
        assertTrue(new BedrockDetection(BedrockSignal.NOT_BEDROCK, BedrockSignal.BEDROCK).disagrees());
        assertFalse(new BedrockDetection(BedrockSignal.BEDROCK, BedrockSignal.UNAVAILABLE).disagrees());
        assertFalse(new BedrockDetection(BedrockSignal.BEDROCK, BedrockSignal.BEDROCK).disagrees());
    }
}
