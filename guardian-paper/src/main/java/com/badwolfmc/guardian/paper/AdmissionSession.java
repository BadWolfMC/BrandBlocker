package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.Response;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class AdmissionSession {
    private final UUID playerId;
    private final CompletableFuture<Response> response = new CompletableFuture<>();
    private final AtomicReference<GuardianDecision> decision = new AtomicReference<>();
    private final AtomicBoolean challengeSent = new AtomicBoolean();
    private volatile byte[] nonce;
    private volatile boolean cerberusPresent;
    private volatile Integer cerberusProtocol;
    private volatile boolean playHandshakeRequired;
    private volatile boolean quarantined;

    AdmissionSession(UUID playerId) {
        this.playerId = playerId;
    }

    UUID playerId() {
        return playerId;
    }

    CompletableFuture<Response> response() {
        return response;
    }

    GuardianDecision decision() {
        return decision.get();
    }

    void decide(GuardianDecision value) {
        decision.compareAndSet(null, value);
    }

    byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    void setNonce(byte[] nonce) {
        this.nonce = nonce.clone();
    }

    boolean cerberusPresent() {
        return cerberusPresent;
    }

    Integer cerberusProtocol() {
        return cerberusProtocol;
    }

    synchronized boolean recordPresence(int protocolVersion) {
        if (!cerberusPresent) {
            cerberusProtocol = protocolVersion;
            cerberusPresent = true;
            return true;
        }
        return cerberusProtocol != null && cerberusProtocol == protocolVersion;
    }

    boolean challengeSent() {
        return challengeSent.get();
    }

    boolean tryMarkChallengeSent() {
        return challengeSent.compareAndSet(false, true);
    }

    void requirePlayHandshake() {
        this.playHandshakeRequired = true;
    }

    boolean playHandshakeRequired() {
        return playHandshakeRequired;
    }

    void setQuarantined(boolean quarantined) {
        this.quarantined = quarantined;
    }

    boolean quarantined() {
        return quarantined;
    }
}
