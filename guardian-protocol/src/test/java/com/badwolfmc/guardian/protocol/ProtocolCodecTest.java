package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCodecTest {
    private static byte[] nonce() {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) i;
        }
        return nonce;
    }

    @Test
    void challengeRoundTrips() throws Exception {
        Challenge decoded = ProtocolCodec.decodeChallenge(
            ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce()))
        );
        assertEquals(GuardianProtocol.VERSION, decoded.protocolVersion());
        assertArrayEquals(nonce(), decoded.nonce());
    }

    @Test
    void responseRoundTrips() throws Exception {
        Response original = new Response(GuardianProtocol.VERSION, nonce(), List.of(
            new ManifestEntry("cerberus", "0.0.1"),
            new ManifestEntry("fabricloader", "0.19.5")
        ));
        Response decoded = ProtocolCodec.decodeResponse(ProtocolCodec.encodeResponse(original));
        assertEquals(original.protocolVersion(), decoded.protocolVersion());
        assertArrayEquals(original.nonce(), decoded.nonce());
        assertEquals(original.manifest(), decoded.manifest());
    }

    @Test
    void oversizedPayloadIsRejectedBeforeParsing() {
        byte[] bytes = new byte[GuardianProtocol.MAX_PAYLOAD_BYTES + 1];
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(bytes));
    }

    @Test
    void trailingDataIsRejected() {
        byte[] encoded = ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce()));
        byte[] withTrailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeChallenge(withTrailing));
    }
}
