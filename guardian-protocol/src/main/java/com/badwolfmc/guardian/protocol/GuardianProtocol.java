package com.badwolfmc.guardian.protocol;

public final class GuardianProtocol {
    public static final int VERSION = 1;
    public static final int NONCE_BYTES = 16;
    public static final int MAX_PAYLOAD_BYTES = 1024;
    public static final int MAX_MANIFEST_ENTRIES = 8;
    public static final int MAX_MOD_ID_BYTES = 64;
    public static final int MAX_VERSION_BYTES = 64;

    public static final int PROXY_ASSERTION_VERSION = 1;
    public static final int PROXY_SESSION_ID_BYTES = 16;
    public static final int PROXY_HMAC_BYTES = 32;
    public static final int PROXY_SECRET_BYTES = 32;
    public static final long PROXY_ASSERTION_TTL_MILLIS = 15_000L;
    public static final long PROXY_ASSERTION_CLOCK_SKEW_MILLIS = 2_000L;

    public static final String PRESENCE_CHANNEL = "guardian:presence";
    public static final String CHALLENGE_CHANNEL = "guardian:challenge";
    public static final String RESPONSE_CHANNEL = "guardian:response";
    public static final String PROXY_ADMISSION_CHANNEL = "guardian:proxy-admission";

    public static final String PHASE0_DENY_MOD_ID = "guardian-phase0a-deny";

    static final int MAGIC = 0x47554130; // GUA0
    static final int TYPE_CHALLENGE = 1;
    static final int TYPE_RESPONSE = 2;
    static final int TYPE_PRESENCE = 3;
    static final int TYPE_PROXY_ADMISSION = 4;

    private GuardianProtocol() {
    }
}
