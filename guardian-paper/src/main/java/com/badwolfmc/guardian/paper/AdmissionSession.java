package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.protocol.Response;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class AdmissionSession {
    private final UUID playerId;
    private final CompletableFuture<Response> response = new CompletableFuture<>();
    private final AtomicReference<GuardianDecision> decision = new AtomicReference<>();
    private volatile byte[] nonce;
    private volatile boolean challengeSent;

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

    boolean challengeSent() {
        return challengeSent;
    }

    void markChallengeSent() {
        this.challengeSent = true;
    }
}
