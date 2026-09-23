package com.badwolfmc.guardian.protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * A short-lived, connection-scoped admission assertion issued by Guardian-Velocity for one
 * authenticated player connection.
 */
public record ProxyAdmissionAssertion(
    int assertionVersion,
    UUID playerId,
    byte[] proxySessionId,
    long issuedAtEpochMillis,
    long expiresAtEpochMillis
) {
    public ProxyAdmissionAssertion {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(proxySessionId, "proxySessionId");
        if (proxySessionId.length != GuardianProtocol.PROXY_SESSION_ID_BYTES) {
            throw new IllegalArgumentException(
                "proxySessionId must be " + GuardianProtocol.PROXY_SESSION_ID_BYTES + " bytes");
        }
        proxySessionId = proxySessionId.clone();
    }

    @Override
    public byte[] proxySessionId() {
        return proxySessionId.clone();
    }
}
