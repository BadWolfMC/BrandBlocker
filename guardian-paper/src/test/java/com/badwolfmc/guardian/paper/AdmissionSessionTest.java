package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionSessionTest {
    @Test
    void onlyOneResponseCanBeClaimedPerChallenge() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);

        assertTrue(session.tryMarkResponseReceived());
        assertFalse(session.tryMarkResponseReceived());
    }

    @Test
    void duplicatePresenceMustBeIdentical() {
        AdmissionSession session = new AdmissionSession(UUID.randomUUID(), null);
        Presence presence = new Presence(1, 1, GuardianProtocol.KNOWN_CAPABILITIES, "test");

        assertTrue(session.recordPresence(presence));
        assertTrue(session.recordPresence(presence));
        assertFalse(session.recordPresence(new Presence(99, 99,
            GuardianProtocol.KNOWN_CAPABILITIES, "test")));
    }
}
