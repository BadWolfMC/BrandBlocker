package com.badwolfmc.guardian.protection;

import java.util.Objects;

/** Single authority for Protection bypass semantics across all policy surfaces. */
public final class ProtectionBypassResolver {
    public ProtectionBypass resolve(
        ProtectionSurface surface,
        CommandRoot root,
        ProtectionPolicy policy,
        ProtectionPermissionView permissions
    ) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(permissions, "permissions");

        if (permissions.hasPermission(ProtectionPermissions.BYPASS)) {
            return ProtectionBypass.granted(ProtectionBypassKind.GLOBAL, ProtectionPermissions.BYPASS);
        }

        String featurePermission = switch (surface) {
            case COMMAND_EXECUTION -> ProtectionPermissions.COMMAND_BYPASS;
            case NAMESPACED_COMMAND -> ProtectionPermissions.NAMESPACE_BYPASS;
            case COMMAND_VISIBILITY -> ProtectionPermissions.VISIBILITY_BYPASS;
        };
        if (permissions.hasPermission(featurePermission)) {
            return ProtectionBypass.granted(ProtectionBypassKind.FEATURE, featurePermission);
        }

        if (surface == ProtectionSurface.COMMAND_VISIBILITY && policy.perCommandVisibilityBypass()) {
            String commandPermission = ProtectionPermissions.visibilityBypass(root);
            if (permissions.hasPermission(commandPermission)) {
                return ProtectionBypass.granted(
                    ProtectionBypassKind.COMMAND_VISIBILITY, commandPermission);
            }
        }

        return ProtectionBypass.none();
    }
}
