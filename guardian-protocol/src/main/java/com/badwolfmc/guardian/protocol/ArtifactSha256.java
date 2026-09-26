package com.badwolfmc.guardian.protocol;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Exact SHA-256 identity for one archive artifact. */
public record ArtifactSha256(String hex) implements Comparable<ArtifactSha256> {
    public static final int BYTES = 32;
    public static final int HEX_CHARS = BYTES * 2;
    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{" + HEX_CHARS + "}");
    private static final HexFormat HEX = HexFormat.of();

    public ArtifactSha256 {
        Objects.requireNonNull(hex, "hex");
        if (!LOWER_HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException(
                "SHA-256 digest must be exactly " + HEX_CHARS + " lowercase hexadecimal characters");
        }
    }

    public static ArtifactSha256 fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != BYTES) {
            throw new IllegalArgumentException("SHA-256 digest must be exactly " + BYTES + " bytes");
        }
        return new ArtifactSha256(HEX.formatHex(bytes));
    }

    public byte[] bytes() {
        return HEX.parseHex(hex);
    }

    public static ArtifactSha256 hashRegularFile(Path path, long maxBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (Files.isSymbolicLink(path)
            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("artifact is not a regular non-symlink file: " + path.getFileName());
        }
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new IOException("artifact exceeds " + maxBytes + " byte safety limit: " + path.getFileName());
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", ex);
        }

        long read = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                read += count;
                if (read > maxBytes) {
                    throw new IOException(
                        "artifact exceeded " + maxBytes + " byte safety limit while hashing: "
                            + path.getFileName());
                }
                digest.update(buffer, 0, count);
            }
        }
        return fromBytes(digest.digest());
    }

    @Override
    public int compareTo(ArtifactSha256 other) {
        return hex.compareTo(other.hex);
    }
}
