package com.badwolfmc.cerberus;

import com.badwolfmc.cerberus.network.ChallengePayload;
import com.badwolfmc.cerberus.network.ResponsePayload;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.Response;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class CerberusClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Cerberus/Phase0A");

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundConfiguration().register(ChallengePayload.TYPE, ChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ResponsePayload.TYPE, ResponsePayload.CODEC);

        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.TYPE, (payload, context) -> {
            try {
                Challenge challenge = ProtocolCodec.decodeChallenge(payload.bytes());

                if (Boolean.getBoolean("cerberus.phase0a.suppressResponse")) {
                    LOGGER.info("Received Guardian Phase 0A challenge; deliberately suppressing the response for timeout testing.");
                    return;
                }
                if (!ClientConfigurationNetworking.canSend(ResponsePayload.TYPE)) {
                    LOGGER.warn("Guardian response channel is not advertised by the server; not sending a Phase 0A response.");
                    return;
                }
                if (Boolean.getBoolean("cerberus.phase0a.malformed")) {
                    context.responseSender().sendPacket(new ResponsePayload(new byte[] {0x00}));
                    LOGGER.info("Sent deliberately malformed Guardian Phase 0A response.");
                    return;
                }

                int responseProtocol = Integer.getInteger("cerberus.phase0a.protocol", GuardianProtocol.VERSION);
                List<ManifestEntry> manifest = buildTestManifest();
                Response response = new Response(responseProtocol, challenge.nonce(), manifest);
                context.responseSender().sendPacket(new ResponsePayload(ProtocolCodec.encodeResponse(response)));
                LOGGER.info("Responded to Guardian Phase 0A challenge with protocol {} and {} test manifest entries.",
                    responseProtocol, manifest.size());
            } catch (ProtocolException | RuntimeException ex) {
                LOGGER.warn("Ignoring invalid Guardian Phase 0A challenge", ex);
            }
        });

        LOGGER.info("Cerberus Phase 0A initialized. Diagnostic switches: deny, protocol, suppressResponse, malformed.");
    }

    private static List<ManifestEntry> buildTestManifest() {
        FabricLoader loader = FabricLoader.getInstance();
        List<ManifestEntry> manifest = new ArrayList<>();
        manifest.add(new ManifestEntry("cerberus", versionOf(loader, "cerberus")));
        manifest.add(new ManifestEntry("fabricloader", versionOf(loader, "fabricloader")));
        manifest.add(new ManifestEntry("minecraft", versionOf(loader, "minecraft")));

        if (Boolean.getBoolean("cerberus.phase0a.deny")) {
            manifest.add(new ManifestEntry(GuardianProtocol.PHASE0_DENY_MOD_ID, "1"));
        }
        return List.copyOf(manifest);
    }

    private static String versionOf(FabricLoader loader, String modId) {
        return loader.getModContainer(modId)
            .map(ModContainer::getMetadata)
            .map(metadata -> metadata.getVersion().getFriendlyString())
            .orElse("unknown");
    }
}
