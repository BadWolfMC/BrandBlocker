package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolCodecTest {
    @Test
    void presenceRoundTripsNegotiationMetadata() throws Exception {
        Presence presence = new Presence(1, 2, GuardianProtocol.KNOWN_CAPABILITIES, "1.0.0");
        assertEquals(presence, ProtocolCodec.decodePresence(ProtocolCodec.encodePresence(presence)));
    }

    @Test
    void protocolVersionBoundsMatchUnsignedShortWireEncoding() throws Exception {
        Presence maximum = new Presence(
            GuardianProtocol.MAX_PROTOCOL_VERSION,
            GuardianProtocol.MAX_PROTOCOL_VERSION,
            GuardianProtocol.KNOWN_CAPABILITIES,
            "1.0.0"
        );
        assertEquals(maximum, ProtocolCodec.decodePresence(ProtocolCodec.encodePresence(maximum)));

        assertThrows(IllegalArgumentException.class,
            () -> new Presence(0, 1, GuardianProtocol.KNOWN_CAPABILITIES, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new Presence(1, GuardianProtocol.MAX_PROTOCOL_VERSION + 1,
                GuardianProtocol.KNOWN_CAPABILITIES, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new Challenge(0, GuardianProtocol.REQUIRED_CAPABILITIES, nonce()));
        assertThrows(IllegalArgumentException.class,
            () -> new Response(GuardianProtocol.MAX_PROTOCOL_VERSION + 1,
                GuardianProtocol.KNOWN_CAPABILITIES, nonce(), minimalManifest()));
    }

    @Test
    void challengeRoundTripsCapabilitiesAndNonce() throws Exception {
        Challenge challenge = new Challenge(1, GuardianProtocol.REQUIRED_CAPABILITIES, nonce());
        Challenge decoded = ProtocolCodec.decodeChallenge(ProtocolCodec.encodeChallenge(challenge));
        assertEquals(challenge.protocolVersion(), decoded.protocolVersion());
        assertEquals(challenge.requiredCapabilities(), decoded.requiredCapabilities());
        assertArrayEquals(challenge.nonce(), decoded.nonce());
    }

    @Test
    void canonicalManifestRoundTripsDeterministically() throws Exception {
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2",
            "0.19.5",
            "0.2.0",
            GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(
                new ManifestEntry("z-child", "2", "a-parent", OriginKind.ARCHIVE),
                new ManifestEntry("a-parent", "1", null, OriginKind.DIRECTORY)
            )
        ));
        Response response = new Response(1, GuardianProtocol.KNOWN_CAPABILITIES, nonce(), manifest);
        byte[] encoded = ProtocolCodec.encodeResponse(response);
        Response decoded = ProtocolCodec.decodeResponse(encoded);

        assertEquals(manifest, decoded.manifest());
        assertArrayEquals(encoded, ProtocolCodec.encodeResponse(decoded));
    }

    @Test
    void realisticLargeNestedManifestRoundTrips() throws Exception {
        List<ManifestEntry> entries = new ArrayList<>();
        entries.add(new ManifestEntry("root", "1.0.0", null, OriginKind.ARCHIVE));
        for (int i = 1; i < 300; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i), "1.2.3+build." + i,
                "root", OriginKind.NESTED));
        }

        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "0.1.0-phase2", GuardianProtocol.KNOWN_CAPABILITIES, entries));
        byte[] encoded = ProtocolCodec.encodeResponse(
            new Response(GuardianProtocol.VERSION, GuardianProtocol.KNOWN_CAPABILITIES, nonce(), manifest));

        Response decoded = ProtocolCodec.decodeResponse(encoded);
        ManifestCanonicalizer.validateCanonical(decoded.manifest());
        assertEquals(300, decoded.manifest().entries().size());
        assertEquals(manifest, decoded.manifest());
    }

    @Test
    void manifestWireModelContainsOnlyPrivacySafePolicyFields() {
        assertEquals(
            List.of("minecraftVersion", "fabricLoaderVersion", "cerberusVersion", "capabilities", "entries"),
            Arrays.stream(Manifest.class.getRecordComponents()).map(component -> component.getName()).toList()
        );
        assertEquals(
            List.of("modId", "version", "parentModId", "originKind"),
            Arrays.stream(ManifestEntry.class.getRecordComponents()).map(component -> component.getName()).toList()
        );
    }

    @Test
    void exactFieldBoundariesAreAcceptedAndOneByteOverIsRejected() {
        String maxId = "a" + "b".repeat(GuardianProtocol.MAX_MOD_ID_BYTES - 1);
        String maxVersion = "v".repeat(GuardianProtocol.MAX_VERSION_BYTES);
        String maxRelease = "r".repeat(GuardianProtocol.MAX_RELEASE_METADATA_BYTES);

        Manifest accepted = ManifestCanonicalizer.canonicalize(new Manifest(
            maxRelease,
            maxRelease,
            maxRelease,
            GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry(maxId, maxVersion, null, OriginKind.ARCHIVE))
        ));
        assertEquals(maxId, accepted.entries().getFirst().modId());

        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry("a" + "b".repeat(GuardianProtocol.MAX_MOD_ID_BYTES), "1", null,
                OriginKind.ARCHIVE)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry("valid-id", "v".repeat(GuardianProtocol.MAX_VERSION_BYTES + 1), null,
                OriginKind.ARCHIVE)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "r".repeat(GuardianProtocol.MAX_RELEASE_METADATA_BYTES + 1), "loader", "cerberus",
            GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry("valid-id", "1", null, OriginKind.ARCHIVE)))));
    }

    @Test
    void fabricModIdSyntaxIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("a"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("1bad"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("BAD-ID"));
        assertThrows(IllegalArgumentException.class, () -> canonicalSingle("bad.id"));
    }

    @Test
    void manifestEntryCountBoundaryIsEnforced() {
        assertEquals(GuardianProtocol.MAX_MANIFEST_ENTRIES,
            ManifestCanonicalizer.canonicalize(manifestWithEntries(GuardianProtocol.MAX_MANIFEST_ENTRIES))
                .entries().size());
        assertThrows(IllegalArgumentException.class,
            () -> ManifestCanonicalizer.canonicalize(manifestWithEntries(GuardianProtocol.MAX_MANIFEST_ENTRIES + 1)));
    }

    @Test
    void containmentCycleAndDepthLimitsAreEnforced() {
        Manifest cycle = new Manifest("26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES, List.of(
            new ManifestEntry("aa", "1", "bb", OriginKind.NESTED),
            new ManifestEntry("bb", "1", "aa", OriginKind.NESTED)
        ));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(cycle));

        ManifestCanonicalizer.canonicalize(containmentChain(GuardianProtocol.MAX_RELATIONSHIP_DEPTH));
        assertThrows(IllegalArgumentException.class,
            () -> ManifestCanonicalizer.canonicalize(containmentChain(GuardianProtocol.MAX_RELATIONSHIP_DEPTH + 1)));
    }

    @Test
    void oversizedPayloadIsRejectedOnDecode() {
        assertThrows(ProtocolException.class,
            () -> ProtocolCodec.decodeResponse(new byte[GuardianProtocol.MAX_PAYLOAD_BYTES + 1]));
    }

    @Test
    void aggregatePayloadLimitIsEnforcedOnEncode() {
        List<ManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < GuardianProtocol.MAX_MANIFEST_ENTRIES; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i),
                "v".repeat(GuardianProtocol.MAX_VERSION_BYTES), null, OriginKind.ARCHIVE));
        }
        Manifest manifest = ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES, entries));
        Response response = new Response(GuardianProtocol.VERSION, GuardianProtocol.KNOWN_CAPABILITIES,
            nonce(), manifest);

        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.encodeResponse(response));
    }

    @Test
    void truncatedResponseIsRejected() {
        Response response = new Response(GuardianProtocol.VERSION, GuardianProtocol.KNOWN_CAPABILITIES,
            nonce(), minimalManifest());
        byte[] encoded = ProtocolCodec.encodeResponse(response);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodeResponse(truncated));
    }

    @Test
    void malformedUtf8IsRejected() {
        byte[] payload = ProtocolCodec.encodePresence(new Presence(1, 1, 7, "x"));
        payload[payload.length - 1] = (byte) 0x80;
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decodePresence(payload));
    }

    @Test
    void duplicateAndImpossibleRelationshipsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", 7, List.of(
                new ManifestEntry("aa", "1", null, OriginKind.ARCHIVE),
                new ManifestEntry("aa", "2", null, OriginKind.ARCHIVE)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", 7,
            List.of(new ManifestEntry("aa", "1", "missing", OriginKind.ARCHIVE)))));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "l", "c", 7,
            List.of(new ManifestEntry("bad id", "1", null, OriginKind.ARCHIVE)))));
    }

    @Test
    void nonCanonicalWireManifestIsDetectable() {
        Manifest manifest = new Manifest("26.2", "l", "c", 7, List.of(
            new ManifestEntry("zz", "1", null, OriginKind.ARCHIVE),
            new ManifestEntry("aa", "1", null, OriginKind.ARCHIVE)
        ));
        assertThrows(IllegalArgumentException.class, () -> ManifestCanonicalizer.validateCanonical(manifest));
    }

    private static Manifest canonicalSingle(String id) {
        return ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry(id, "1", null, OriginKind.ARCHIVE))));
    }

    private static Manifest minimalManifest() {
        return ManifestCanonicalizer.canonicalize(new Manifest(
            "26.2", "0.19.5", "0.1.0-phase2", GuardianProtocol.KNOWN_CAPABILITIES,
            List.of(new ManifestEntry("fabricloader", "0.19.5", null, OriginKind.ARCHIVE))));
    }

    private static Manifest manifestWithEntries(int count) {
        List<ManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new ManifestEntry("m" + String.format("%03d", i), "1", null, OriginKind.ARCHIVE));
        }
        return new Manifest("26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES, entries);
    }

    private static Manifest containmentChain(int depth) {
        List<ManifestEntry> entries = new ArrayList<>();
        entries.add(new ManifestEntry("m00", "1", null, OriginKind.ARCHIVE));
        for (int i = 1; i <= depth; i++) {
            entries.add(new ManifestEntry("m" + String.format("%02d", i), "1",
                "m" + String.format("%02d", i - 1), OriginKind.NESTED));
        }
        return new Manifest("26.2", "loader", "cerberus", GuardianProtocol.KNOWN_CAPABILITIES, entries);
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) i;
        }
        return nonce;
    }
}
