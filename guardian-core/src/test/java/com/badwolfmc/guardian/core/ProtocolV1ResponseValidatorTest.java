package com.badwolfmc.guardian.core;
import com.badwolfmc.guardian.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProtocolV1ResponseValidatorTest {
 @Test void acceptsCanonicalManifest(){assertEquals(DecisionReason.CERBERUS_VERIFIED,ProtocolV1ResponseValidator.validate(nonce(),response(nonce(),GuardianProtocol.KNOWN_CAPABILITIES)).reason());}
 @Test void rejectsNonceMismatch(){byte[] wrong=nonce();wrong[0]=9;assertEquals(DecisionReason.MANIFEST_INVALID,ProtocolV1ResponseValidator.validate(nonce(),response(wrong,GuardianProtocol.KNOWN_CAPABILITIES)).reason());}
 @Test void rejectsMissingCapability(){assertEquals(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,ProtocolV1ResponseValidator.validate(nonce(),response(nonce(),GuardianProtocol.CAP_CANONICAL_MANIFEST_V1)).reason());}
 @Test void rejectsNonCanonicalOrder(){Manifest m=new Manifest("26.2","0.19.5","x",GuardianProtocol.KNOWN_CAPABILITIES,List.of(new ManifestEntry("z","1",null,OriginKind.ARCHIVE),new ManifestEntry("a","1",null,OriginKind.ARCHIVE)));Response r=new Response(1,GuardianProtocol.KNOWN_CAPABILITIES,nonce(),m);assertEquals(DecisionReason.MANIFEST_INVALID,ProtocolV1ResponseValidator.validate(nonce(),r).reason());}
 @Test void rejectsUnsupportedResponseProtocol(){Response r=response(nonce(),GuardianProtocol.KNOWN_CAPABILITIES);Response wrong=new Response(GuardianProtocol.VERSION+1,r.capabilities(),r.nonce(),r.manifest());assertEquals(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,ProtocolV1ResponseValidator.validate(nonce(),wrong).reason());}
 private static Response response(byte[] n,long caps){Manifest m=ManifestCanonicalizer.canonicalize(new Manifest("26.2","0.19.5","x",caps,List.of(new ManifestEntry("fabricloader","0.19.5",null,OriginKind.ARCHIVE))));return new Response(1,caps,n,m);}
 private static byte[] nonce(){return new byte[GuardianProtocol.NONCE_BYTES];}
}
