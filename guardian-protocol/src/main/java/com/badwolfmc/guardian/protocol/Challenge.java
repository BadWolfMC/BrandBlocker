package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.Objects;

public record Challenge(int protocolVersion, byte[] nonce) {
    public Challenge {
        Objects.requireNonNull(nonce, "nonce");
        if (nonce.length != GuardianProtocol.NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        }
        nonce = Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }
}
