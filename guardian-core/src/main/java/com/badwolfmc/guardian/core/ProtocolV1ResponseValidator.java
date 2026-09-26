package com.badwolfmc.guardian.core;

import com.badwolfmc.guardian.protocol.*;
import java.util.Arrays;

public final class ProtocolV1ResponseValidator {
    private ProtocolV1ResponseValidator() {}
    public static GuardianDecision validate(byte[] expectedNonce, Response response) {
        if(expectedNonce==null||expectedNonce.length!=GuardianProtocol.NONCE_BYTES) return GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,"server challenge nonce has invalid length");
        if(response.protocolVersion()!=GuardianProtocol.VERSION) return GuardianDecision.deny(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,"client protocol="+response.protocolVersion()+", server protocol="+GuardianProtocol.VERSION);
        if((response.capabilities()&GuardianProtocol.REQUIRED_CAPABILITIES)!=GuardianProtocol.REQUIRED_CAPABILITIES) return GuardianDecision.deny(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,"Cerberus lacks required protocol-v1 capabilities");
        if(response.capabilities()!=response.manifest().capabilities()) return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,"response/manifest capability mismatch");
        if(!Arrays.equals(expectedNonce,response.nonce())) return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,"challenge nonce mismatch");
        try{ManifestCanonicalizer.validateCanonical(response.manifest());}catch(IllegalArgumentException ex){return GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,ex.getMessage());}
        return GuardianDecision.allow(DecisionReason.CERBERUS_VERIFIED,"Cerberus protocol-v1 response and canonical manifest validated ("+response.manifest().entries().size()+" mods)");
    }
}
