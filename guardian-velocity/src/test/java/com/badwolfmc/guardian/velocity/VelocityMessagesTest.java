package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class VelocityMessagesTest {
    @Test
    void bundledFallbackLocaleCoversVelocityAdmissionDenials() {
        VelocityMessages messages = VelocityMessages.load();
        for (DecisionReason reason : DecisionReason.values()) {
            assertNotNull(messages.render(reason, ClientClassification.JAVA_FABRIC));
        }
    }
}
