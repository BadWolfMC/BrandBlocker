package com.badwolfmc.guardian.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record Response(int protocolVersion, byte[] nonce, List<ManifestEntry> manifest) {
    public Response {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(manifest, "manifest");
        if (nonce.length != GuardianProtocol.NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must be exactly " + GuardianProtocol.NONCE_BYTES + " bytes");
        }
        nonce = Arrays.copyOf(nonce, nonce.length);
        manifest = List.copyOf(manifest);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }
}
