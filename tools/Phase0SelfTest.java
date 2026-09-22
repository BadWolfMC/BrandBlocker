import com.badwolfmc.guardian.core.BrandClassifier;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.Phase0ManifestEvaluator;
import com.badwolfmc.guardian.core.Phase0ResponseValidator;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.Response;

import java.util.List;

public final class Phase0SelfTest {
    public static void main(String[] args) throws Exception {
        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte) (i * 7);

        Presence decodedPresence = ProtocolCodec.decodePresence(
            ProtocolCodec.encodePresence(new Presence(GuardianProtocol.VERSION)));
        require(decodedPresence.protocolVersion() == GuardianProtocol.VERSION, "presence protocol round-trip");

        Challenge decodedChallenge = ProtocolCodec.decodeChallenge(
            ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce)));
        require(java.util.Arrays.equals(nonce, decodedChallenge.nonce()), "challenge nonce round-trip");

        Response allowedResponse = new Response(GuardianProtocol.VERSION, nonce,
            List.of(new ManifestEntry("cerberus", "0.0.1")));
        Response decodedResponse = ProtocolCodec.decodeResponse(ProtocolCodec.encodeResponse(allowedResponse));
        require(decodedResponse.manifest().equals(allowedResponse.manifest()), "response manifest round-trip");
        require(Phase0ManifestEvaluator.evaluate(decodedResponse).reason() == DecisionReason.CERBERUS_VERIFIED,
            "normal manifest should allow");

        Response deniedResponse = new Response(GuardianProtocol.VERSION, nonce,
            List.of(new ManifestEntry(GuardianProtocol.PHASE0_DENY_MOD_ID, "1")));
        require(Phase0ManifestEvaluator.evaluate(deniedResponse).reason() == DecisionReason.MANIFEST_DENIED,
            "deny marker should deny");

        Response incompatible = new Response(GuardianProtocol.VERSION + 1, nonce, List.of());
        require(Phase0ResponseValidator.validate(nonce, incompatible).reason() == DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
            "unsupported protocol must stay distinct");

        byte[] wrongNonce = nonce.clone();
        wrongNonce[0] ^= 1;
        Response mismatchedNonce = new Response(GuardianProtocol.VERSION, wrongNonce, List.of());
        require(Phase0ResponseValidator.validate(nonce, mismatchedNonce).reason() == DecisionReason.MANIFEST_INVALID,
            "nonce mismatch must be invalid rather than policy denied");

        require(BrandClassifier.classify("fabric") == ClientClassification.JAVA_FABRIC, "fabric classification");
        require(BrandClassifier.classify("vanilla") == ClientClassification.JAVA_VANILLA, "vanilla classification");
        require(BrandClassifier.classify("fake-fabric-client") == ClientClassification.JAVA_UNKNOWN,
            "classification must not use substring matching");

        byte[] tooLarge = new byte[GuardianProtocol.MAX_PAYLOAD_BYTES + 1];
        boolean rejected = false;
        try {
            ProtocolCodec.decodeResponse(tooLarge);
        } catch (Exception expected) {
            rejected = true;
        }
        require(rejected, "oversized payload should reject");

        System.out.println("Phase0SelfTest: PASS");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
