package com.badwolfmc.guardian.protocol;

public final class GuardianProtocol {
    public static final int VERSION = 1;
    public static final int NONCE_BYTES = 16;
    public static final int MAX_PAYLOAD_BYTES = 1024;
    public static final int MAX_MANIFEST_ENTRIES = 8;
    public static final int MAX_MOD_ID_BYTES = 64;
    public static final int MAX_VERSION_BYTES = 64;

    public static final String CHALLENGE_CHANNEL = "guardian:challenge";
    public static final String RESPONSE_CHANNEL = "guardian:response";

    public static final String PHASE0_DENY_MOD_ID = "guardian-phase0a-deny";

    static final int MAGIC = 0x47554130; // GUA0
    static final int TYPE_CHALLENGE = 1;
    static final int TYPE_RESPONSE = 2;

    private GuardianProtocol() {
    }
}
