package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class VelocityAdmissionSession {
    private final CompletableFuture<GuardianDecision> decisionFuture = new CompletableFuture<>();
    private final AtomicReference<GuardianDecision> decision = new AtomicReference<>();
    private final AtomicReference<ClientClassification> classification = new AtomicReference<>();
    private final AtomicBoolean challengeSent = new AtomicBoolean();
    private final AtomicBoolean responseReceived = new AtomicBoolean();
    private final byte[] proxySessionId;
    private volatile byte[] nonce;
    private volatile Presence cerberusPresence;

    VelocityAdmissionSession(byte[] proxySessionId) {
        if (proxySessionId == null || proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
            throw new IllegalArgumentException(
                "proxySessionId must be " + GuardianProtocol.PROXY_SESSION_ID_BYTES + " bytes");
        }
        this.proxySessionId = proxySessionId.clone();
    }

    CompletableFuture<GuardianDecision> decisionFuture() {
        return decisionFuture;
    }

    GuardianDecision decision() {
        return decision.get();
    }

    boolean decide(GuardianDecision value) {
        if (!decision.compareAndSet(null, value)) {
            return false;
        }
        decisionFuture.complete(value);
        return true;
    }

    ClientClassification classification() {
        return classification.get();
    }

    void setClassification(ClientClassification value) {
        classification.compareAndSet(null, value);
    }

    Presence cerberusPresence() { return cerberusPresence; }

    synchronized boolean recordPresence(Presence presence) {
        if (cerberusPresence == null) { cerberusPresence = presence; return true; }
        return java.util.Objects.equals(cerberusPresence, presence);
    }

    boolean cerberusPresent() { return cerberusPresence != null; }

    boolean tryMarkResponseReceived() { return responseReceived.compareAndSet(false, true); }

    boolean challengeSent() {
        return challengeSent.get();
    }

    boolean tryMarkChallengeSent() {
        return challengeSent.compareAndSet(false, true);
    }

    byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    void setNonce(byte[] value) {
        nonce = value.clone();
    }

    byte[] proxySessionId() {
        return proxySessionId.clone();
    }

    boolean admitted() {
        GuardianDecision value = decision.get();
        return value != null && value.outcome() == DecisionOutcome.ALLOW;
    }
}
