package com.badwolfmc.guardian.core;

/** Stable administrator-facing permission names for Guardian Admission. */
public final class AdmissionPermissions {
    public static final String ROOT = "guardian.admission";
    public static final String CLIENT_BYPASS = ROOT + ".client.bypass";
    public static final String MOD_BYPASS = ROOT + ".mod.bypass";
    public static final int MAX_PERMISSION_LENGTH = 128;

    private AdmissionPermissions() {
    }

    public static String profile(String profileId) {
        return dynamic(ROOT + ".profile.", profileId, "profile id", 48);
    }

    public static String clientBypass(String clientKey) {
        return dynamic(CLIENT_BYPASS + ".", clientKey, "client key", 32);
    }

    public static String modBypass(String modId) {
        return dynamic(MOD_BYPASS + ".", modId, "mod id", 64);
    }

    private static String dynamic(String prefix, String suffix, String label, int maxSuffixLength) {
        if (suffix == null || !suffix.matches("[a-z0-9._-]{1," + maxSuffixLength + "}")) {
            throw new IllegalArgumentException(label + " must match [a-z0-9._-]{1," + maxSuffixLength + "}");
        }
        String value = prefix + suffix;
        if (value.length() > MAX_PERMISSION_LENGTH) {
            throw new IllegalArgumentException("Guardian permission exceeds " + MAX_PERMISSION_LENGTH + " characters");
        }
        return value;
    }
}
