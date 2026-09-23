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
    void presenceRoundTrips() throws Exception {
        Presence decoded = ProtocolCodec.decodePresence(
            ProtocolCodec.encodePresence(new Presence(GuardianProtocol.VERSION))
        );
        assertEquals(GuardianProtocol.VERSION, decoded.protocolVersion());
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

    @Test
    void proxyAdmissionRoundTripsAndAuthenticates() throws Exception {
        byte[] secret = new byte[GuardianProtocol.PROXY_SECRET_BYTES];
        java.util.Arrays.fill(secret, (byte) 0x5A);
        byte[] sessionId = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        for (int i = 0; i < sessionId.length; i++) {
            sessionId[i] = (byte) (0x10 + i);
        }
        java.util.UUID playerId = java.util.UUID.fromString("bfa787d5-633b-47b8-a669-2fdbdbecfe91");
        ProxyAdmissionAssertion original = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            playerId,
            sessionId,
            1_000L,
            16_000L
        );

        ProxyAdmissionAssertion decoded = ProxyAdmissionCodec.decodeAndVerify(
            ProxyAdmissionCodec.encode(original, secret),
            secret
        );

        assertEquals(original.assertionVersion(), decoded.assertionVersion());
        assertEquals(playerId, decoded.playerId());
        assertArrayEquals(sessionId, decoded.proxySessionId());
        assertEquals(1_000L, decoded.issuedAtEpochMillis());
        assertEquals(16_000L, decoded.expiresAtEpochMillis());
    }

    @Test
    void proxyAdmissionRejectsTampering() {
        byte[] secret = new byte[GuardianProtocol.PROXY_SECRET_BYTES];
        java.util.Arrays.fill(secret, (byte) 0x33);
        byte[] sessionId = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        ProxyAdmissionAssertion assertion = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            java.util.UUID.randomUUID(),
            sessionId,
            1_000L,
            16_000L
        );
        byte[] encoded = ProxyAdmissionCodec.encode(assertion, secret);
        encoded[encoded.length / 2] ^= 0x01;

        ProtocolException ex = assertThrows(
            ProtocolException.class,
            () -> ProxyAdmissionCodec.decodeAndVerify(encoded, secret)
        );
        assertTrue(ex.getMessage().contains("HMAC"));
    }

    @Test
    void proxyAdmissionRequiresExact32ByteSecret() {
        String encoded = java.util.Base64.getEncoder().encodeToString(new byte[31]);
        assertThrows(IllegalArgumentException.class, () -> ProxyAdmissionCodec.decodeBase64Secret(encoded));
    }
}
