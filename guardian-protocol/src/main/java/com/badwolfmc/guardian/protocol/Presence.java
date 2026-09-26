package com.badwolfmc.guardian.protocol;

import java.util.Objects;

public record Presence(int minProtocolVersion, int maxProtocolVersion, long capabilities, String cerberusVersion) {
    public Presence {
        Objects.requireNonNull(cerberusVersion, "cerberusVersion");
        if (minProtocolVersion < 1 || maxProtocolVersion < minProtocolVersion
            || maxProtocolVersion > GuardianProtocol.MAX_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("protocol range must be within 1.." + GuardianProtocol.MAX_PROTOCOL_VERSION);
        }
    }

    public boolean supports(int protocolVersion) {
        return protocolVersion >= minProtocolVersion && protocolVersion <= maxProtocolVersion;
    }
}
