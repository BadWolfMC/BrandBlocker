package com.badwolfmc.guardian.core.artifact;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** One exact administrator-catalogued Fabric artifact. */
public record ApprovedArtifact(String modId, String version, ArtifactSha256 sha256)
    implements Comparable<ApprovedArtifact> {
    private static final Pattern FABRIC_MOD_ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public ApprovedArtifact {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sha256, "sha256");
        if (!FABRIC_MOD_ID.matcher(modId).matches()) {
            throw new IllegalArgumentException("invalid Fabric mod id: " + modId);
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("artifact version must not be blank");
        }
        if (version.getBytes(StandardCharsets.UTF_8).length > GuardianProtocol.MAX_VERSION_BYTES) {
            throw new IllegalArgumentException(
                "artifact version exceeds " + GuardianProtocol.MAX_VERSION_BYTES + " UTF-8 bytes");
        }
    }

    @Override
    public int compareTo(ApprovedArtifact other) {
        int result = modId.compareTo(other.modId);
        if (result != 0) return result;
        result = version.compareTo(other.version);
        if (result != 0) return result;
        return sha256.compareTo(other.sha256);
    }
}
