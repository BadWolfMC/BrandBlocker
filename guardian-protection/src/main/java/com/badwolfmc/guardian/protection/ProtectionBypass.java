package com.badwolfmc.guardian.protection;

import java.util.Objects;
import java.util.Optional;

public record ProtectionBypass(ProtectionBypassKind kind, Optional<String> permission) {
    public ProtectionBypass {
        Objects.requireNonNull(kind, "kind");
        permission = Objects.requireNonNull(permission, "permission");
        if (kind == ProtectionBypassKind.NONE && permission.isPresent()) {
            throw new IllegalArgumentException("NONE bypass cannot carry a permission");
        }
        if (kind != ProtectionBypassKind.NONE && permission.isEmpty()) {
            throw new IllegalArgumentException("active bypass must identify its permission");
        }
    }

    public static ProtectionBypass none() {
        return new ProtectionBypass(ProtectionBypassKind.NONE, Optional.empty());
    }

    public static ProtectionBypass granted(ProtectionBypassKind kind, String permission) {
        return new ProtectionBypass(kind, Optional.of(permission));
    }

    public boolean granted() {
        return kind != ProtectionBypassKind.NONE;
    }
}
