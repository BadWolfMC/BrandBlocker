package com.badwolfmc.guardian.protection;

/** Stable administrator-facing permission names for Guardian Protection. */
public final class ProtectionPermissions {
    public static final String ROOT = "guardian.protection";
    public static final String BYPASS = ROOT + ".bypass";
    public static final String COMMAND_BYPASS = ROOT + ".command.bypass";
    public static final String NAMESPACE_BYPASS = ROOT + ".namespace.bypass";
    public static final String VISIBILITY_BYPASS = ROOT + ".visibility.bypass";
    public static final String NOTIFY = ROOT + ".notify";
    public static final int MAX_PERMISSION_LENGTH = 128;

    private ProtectionPermissions() {
    }

    public static String visibilityBypass(String normalizedCommandRoot) {
        if (normalizedCommandRoot == null || !normalizedCommandRoot.matches("[a-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("normalized command root must match [a-z0-9._-]{1,48}");
        }
        return checked(VISIBILITY_BYPASS + "." + normalizedCommandRoot);
    }

    private static String checked(String permission) {
        if (permission.length() > MAX_PERMISSION_LENGTH) {
            throw new IllegalArgumentException("Guardian permission exceeds " + MAX_PERMISSION_LENGTH + " characters");
        }
        return permission;
    }
}
