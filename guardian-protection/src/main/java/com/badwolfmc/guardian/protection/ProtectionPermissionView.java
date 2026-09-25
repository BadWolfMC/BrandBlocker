package com.badwolfmc.guardian.protection;

@FunctionalInterface
public interface ProtectionPermissionView {
    boolean hasPermission(String permission);
}
