package com.badwolfmc.guardian.protection;

import java.util.Objects;

/** Canonical, case-insensitive command root used by all Protection surfaces. */
public record CommandRoot(
    String value,
    boolean namespaced,
    String namespace,
    String label,
    String permissionKey
) {
    public CommandRoot {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(permissionKey, "permissionKey");
    }
}
