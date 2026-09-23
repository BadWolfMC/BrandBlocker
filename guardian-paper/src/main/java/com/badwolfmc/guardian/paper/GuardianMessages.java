package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.DecisionReason;

final class GuardianMessages {
    private GuardianMessages() {
    }

    static String forReason(DecisionReason reason) {
        return switch (reason) {
            case PROXY_ASSERTION_REQUIRED -> "Guardian-Velocity did not provide a trusted admission assertion.";
            case PROXY_ASSERTION_INVALID -> "Guardian received an invalid proxy admission assertion.";
            case CERBERUS_REQUIRED -> "Fabric is supported, but the Cerberus client mod is required.";
            case CERBERUS_TIMEOUT -> "Cerberus was detected, but the Guardian handshake timed out.";
            case CERBERUS_PROTOCOL_UNSUPPORTED -> "Cerberus is installed, but its Guardian protocol version is incompatible.";
            case MANIFEST_DENIED -> "Cerberus responded successfully, but the Phase 0A test manifest was denied.";
            case MANIFEST_INVALID -> "Cerberus returned invalid Guardian protocol data.";
            case CLIENT_DENIED -> "This client brand is not admitted by the Phase 0A prototype.";
            case CONFIGURATION_ERROR -> "Guardian could not complete the Phase 0A admission check.";
            default -> "Guardian denied this connection.";
        };
    }
}
