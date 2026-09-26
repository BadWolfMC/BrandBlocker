package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;

public record Challenge(int protocolVersion, long requiredCapabilities, byte[] nonce) {
    public Challenge {
        Objects.requireNonNull(nonce, "nonce");
        if (protocolVersion < 1 || protocolVersion > GuardianProtocol.MAX_PROTOCOL_VERSION) throw new IllegalArgumentException("protocolVersion out of range");
        if (nonce.length != GuardianProtocol.NONCE_BYTES) throw new IllegalArgumentException("nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        nonce = Arrays.copyOf(nonce, nonce.length);
    }
    @Override public byte[] nonce() { return Arrays.copyOf(nonce, nonce.length); }
}
