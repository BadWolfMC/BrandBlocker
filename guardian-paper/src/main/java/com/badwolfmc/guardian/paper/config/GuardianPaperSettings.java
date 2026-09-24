package com.badwolfmc.guardian.paper.config;

import com.badwolfmc.guardian.core.AdmissionPolicy;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;

import java.util.Objects;

public record GuardianPaperSettings(
    int schemaVersion,
    boolean admissionEnabled,
    boolean protectionEnabled,
    String locale,
    String helpUrl,
    PaperAuthorityMode authorityMode,
    int handshakeTimeoutSeconds,
    int challengeChannelWaitTicks,
    AdmissionPolicy admissionPolicy
) {
    public GuardianPaperSettings {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(helpUrl, "helpUrl");
        Objects.requireNonNull(authorityMode, "authorityMode");
        Objects.requireNonNull(admissionPolicy, "admissionPolicy");
    }

    public long handshakeTimeoutTicks() {
        return handshakeTimeoutSeconds * 20L;
    }
}
