package com.badwolfmc.guardian.protection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable normalized rule for one Protection surface. */
public record ProtectionRule(
    ProtectionSurface surface,
    boolean enabled,
    ProtectionRuleMode mode,
    Set<String> roots
) {
    public ProtectionRule {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(roots, "roots");

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : roots) {
            CommandRoot root = CommandRootNormalizer.normalize(raw);
            if (surface == ProtectionSurface.NAMESPACED_COMMAND && !root.namespaced()) {
                throw new IllegalArgumentException(
                    "namespaced-command rules must contain full namespace:command roots: " + raw);
            }
            if (!normalized.add(root.value())) {
                throw new IllegalArgumentException("duplicate normalized command root '" + root.value() + "'");
            }
        }
        roots = Set.copyOf(normalized);

        if (surface == ProtectionSurface.COMMAND_EXECUTION && mode != ProtectionRuleMode.DENYLIST) {
            throw new IllegalArgumentException("command execution policy is a configured deny list");
        }
    }

    public static ProtectionRule create(
        ProtectionSurface surface,
        boolean enabled,
        ProtectionRuleMode mode,
        Collection<String> roots
    ) {
        Objects.requireNonNull(roots, "roots");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : roots) {
            String root = CommandRootNormalizer.normalize(raw).value();
            if (!normalized.add(root)) {
                throw new IllegalArgumentException("duplicate normalized command root '" + root + "'");
            }
        }
        return new ProtectionRule(surface, enabled, mode, normalized);
    }

    public boolean denies(CommandRoot root) {
        if (!enabled) {
            return false;
        }
        boolean listed = roots.contains(root.value());
        return mode == ProtectionRuleMode.ALLOWLIST ? !listed : listed;
    }
}
