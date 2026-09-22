package com.badwolfmc.guardian.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ProtocolCodec {
    private ProtocolCodec() {
    }

    public static byte[] encodeChallenge(Challenge challenge) {
        return write(out -> {
            out.writeInt(GuardianProtocol.MAGIC);
            out.writeByte(GuardianProtocol.TYPE_CHALLENGE);
            out.writeShort(challenge.protocolVersion());
            out.write(challenge.nonce());
        });
    }

    public static Challenge decodeChallenge(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_CHALLENGE, in -> {
            int protocolVersion = Short.toUnsignedInt(in.readShort());
            byte[] nonce = in.readNBytes(GuardianProtocol.NONCE_BYTES);
            if (nonce.length != GuardianProtocol.NONCE_BYTES) {
                throw new ProtocolException("truncated challenge nonce");
            }
            ensureFullyConsumed(in);
            return new Challenge(protocolVersion, nonce);
        });
    }

    public static byte[] encodeResponse(Response response) {
        if (response.manifest().size() > GuardianProtocol.MAX_MANIFEST_ENTRIES) {
            throw new IllegalArgumentException("too many manifest entries");
        }
        return write(out -> {
            out.writeInt(GuardianProtocol.MAGIC);
            out.writeByte(GuardianProtocol.TYPE_RESPONSE);
            out.writeShort(response.protocolVersion());
            out.write(response.nonce());
            out.writeByte(response.manifest().size());
            for (ManifestEntry entry : response.manifest()) {
                writeBoundedUtf8(out, entry.modId(), GuardianProtocol.MAX_MOD_ID_BYTES);
                writeBoundedUtf8(out, entry.version(), GuardianProtocol.MAX_VERSION_BYTES);
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws ProtocolException {
        return read(payload, GuardianProtocol.TYPE_RESPONSE, in -> {
            int protocolVersion = Short.toUnsignedInt(in.readShort());
            byte[] nonce = in.readNBytes(GuardianProtocol.NONCE_BYTES);
            if (nonce.length != GuardianProtocol.NONCE_BYTES) {
                throw new ProtocolException("truncated response nonce");
            }

            int count = in.readUnsignedByte();
            if (count > GuardianProtocol.MAX_MANIFEST_ENTRIES) {
                throw new ProtocolException("manifest entry count exceeds limit");
            }

            List<ManifestEntry> manifest = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String modId = readBoundedUtf8(in, GuardianProtocol.MAX_MOD_ID_BYTES);
                String version = readBoundedUtf8(in, GuardianProtocol.MAX_VERSION_BYTES);
                manifest.add(new ManifestEntry(modId, version));
            }
            ensureFullyConsumed(in);
            return new Response(protocolVersion, nonce, manifest);
        });
    }

    private static <T> T read(byte[] payload, int expectedType, Reader<T> reader) throws ProtocolException {
        if (payload == null || payload.length == 0) {
            throw new ProtocolException("empty payload");
        }
        if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException("payload exceeds " + GuardianProtocol.MAX_PAYLOAD_BYTES + " bytes");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != GuardianProtocol.MAGIC) {
                throw new ProtocolException("invalid protocol magic");
            }
            int type = in.readUnsignedByte();
            if (type != expectedType) {
                throw new ProtocolException("unexpected message type " + type);
            }
            return reader.read(in);
        } catch (EOFException ex) {
            throw new ProtocolException("truncated payload", ex);
        } catch (IOException ex) {
            throw new ProtocolException("unable to decode payload", ex);
        }
    }

    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writer.write(out);
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("payload exceeds " + GuardianProtocol.MAX_PAYLOAD_BYTES + " bytes");
            }
            return payload;
        } catch (IOException ex) {
            throw new IllegalStateException("in-memory protocol encoding failed", ex);
        }
    }

    private static void writeBoundedUtf8(DataOutputStream out, String value, int maxBytes) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new IllegalArgumentException("UTF-8 field length must be 1.." + maxBytes + " bytes");
        }
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private static String readBoundedUtf8(DataInputStream in, int maxBytes) throws IOException, ProtocolException {
        int length = in.readUnsignedByte();
        if (length == 0 || length > maxBytes) {
            throw new ProtocolException("invalid UTF-8 field length " + length);
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new ProtocolException("truncated UTF-8 field");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void ensureFullyConsumed(DataInputStream in) throws IOException, ProtocolException {
        if (in.available() != 0) {
            throw new ProtocolException("unexpected trailing bytes");
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream in) throws IOException, ProtocolException;
    }
}
