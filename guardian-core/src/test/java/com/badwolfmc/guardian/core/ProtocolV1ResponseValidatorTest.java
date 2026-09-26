package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestCanonicalizer;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import com.badwolfmc.guardian.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolV1ResponseValidatorTest {
    @Test
    void acceptsCanonicalManifest() {
        assertEquals(
            DecisionReason.CERBERUS_VERIFIED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), GuardianProtocol.KNOWN_CAPABILITIES)).reason()
        );
    }

    @Test
    void rejectsNonceMismatch() {
        byte[] wrong = nonce();
        wrong[0] = 9;
        assertEquals(
            DecisionReason.MANIFEST_INVALID,
            ProtocolV1ResponseValidator.validate(nonce(), response(wrong, GuardianProtocol.KNOWN_CAPABILITIES)).reason()
        );
    }

    @Test
    void rejectsMissingArtifactHashCapability() {
        long oldCapabilities = GuardianProtocol.CAP_CANONICAL_MANIFEST_V1
            | GuardianProtocol.CAP_CONTAINMENT_RELATIONSHIPS
            | GuardianProtocol.CAP_ORIGIN_KIND;
        assertEquals(
            DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            ProtocolV1ResponseValidator.validate(nonce(), response(nonce(), oldCapabilities)).reason()
        );
    }

    @Test
    void rejectsNonCanonicalOrder() {
        Manifest manifest = new Manifest(
            "26.2",
            "0.19.5",
            "x",
            GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(archive("z", 1), archive("a", 2))
        );
        Response response = new Response(1, GuardianProtocol.KNOWN_CAPABILITIES, nonce(), manifest);
        assertEquals(
            DecisionReason.MANIFEST_INVALID,
            ProtocolV1ResponseValidator.validate(nonce(), response).reason()
        );
    }

    @Test
    void rejectsUnsupportedResponseProtocol() {
        Response response = response(nonce(), GuardianProtocol.KNOWN_CAPABILITIES);
        Response wrong = new Response(
            GuardianProtocol.VERSION + 1,
            response.capabilities(),
            response.nonce(),
            response.manifest()
        );
        assertEquals(
            DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            ProtocolV1ResponseValidator.validate(nonce(), wrong).reason()
        );
    }

    private static Response response(byte[] nonce, long capabilities) {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2",
            "0.19.5",
            "x",
            capabilities,
            List.of(archive("fabricloader", 1))
        ));
        return new Response(1, capabilities, nonce, manifest);
    }

    private static ManifestEntry archive(String id, int seed) {
        byte[] bytes = new byte[ArtifactSha256.BYTES];
        java.util.Arrays.fill(bytes, (byte) seed);
        return new ManifestEntry(id, "1", null, OriginKind.ARCHIVE, ArtifactSha256.fromBytes(bytes));
    }

    private static byte[] nonce() {
        return new byte[GuardianProtocol.NONCE_BYTES];
    }
}
