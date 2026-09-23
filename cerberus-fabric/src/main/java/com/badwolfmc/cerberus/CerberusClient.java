package com.badwolfmc.cerberus;

import com.badwolfmc.cerberus.network.ChallengePayload;
import com.badwolfmc.cerberus.network.PresencePayload;
import com.badwolfmc.cerberus.network.ResponsePayload;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.Response;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
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
        registerPayloadTypes();
        registerConfigurationTransport();
        registerPlayTransport();
        LOGGER.info("Cerberus Phase 0A initialized. CONFIGURATION presence + PLAY challenge/response fallback. "
            + "Diagnostic switches: deny, protocol, suppressResponse, malformed.");
    }

    private static void registerPayloadTypes() {
        // CONFIGURATION: presence is useful and proven to cross Fabric -> Paper.
        PayloadTypeRegistry.clientboundConfiguration().register(ChallengePayload.TYPE, ChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(PresencePayload.TYPE, PresencePayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ResponsePayload.TYPE, ResponsePayload.CODEC);

        // PLAY fallback: same protocol messages, phase-specific Fabric registries.
        PayloadTypeRegistry.clientboundPlay().register(ChallengePayload.TYPE, ChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PresencePayload.TYPE, PresencePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResponsePayload.TYPE, ResponsePayload.CODEC);
    }

    private static void registerConfigurationTransport() {
        ClientConfigurationNetworking.registerGlobalReceiver(ChallengePayload.TYPE, (payload, context) -> {
            LOGGER.info("Guardian CONFIGURATION challenge received; responding.");
            respondToChallenge(payload, context.responseSender(), "CONFIGURATION");
        });

        ClientConfigurationConnectionEvents.START.register((listener, client) -> {
            try {
                boolean canSendPresence = ClientConfigurationNetworking.canSend(PresencePayload.TYPE);
                boolean canSendResponse = ClientConfigurationNetworking.canSend(ResponsePayload.TYPE);
                LOGGER.info("Guardian Phase 0A CONFIGURATION START: presenceSendable={}, responseSendable={}, challengeReceivable={}",
                    canSendPresence,
                    canSendResponse,
                    ClientConfigurationNetworking.getReceived().contains(ChallengePayload.TYPE.id()));

                if (!canSendPresence) {
                    LOGGER.warn("Connected server did not advertise the Guardian CONFIGURATION presence channel; "
                        + "attempting presence anyway (known Paper/Fabric interoperability behavior).");
                }

                int protocol = selectedProtocol();
                ClientConfigurationNetworking.send(
                    new PresencePayload(ProtocolCodec.encodePresence(new Presence(protocol)))
                );
                LOGGER.info("Attempted Guardian CONFIGURATION presence with protocol {} (serverAdvertised={}).",
                    protocol, canSendPresence);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not send Guardian CONFIGURATION presence", ex);
            }
        });
    }

    private static void registerPlayTransport() {
        ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.TYPE, (payload, context) ->
            respondToChallenge(payload, context.responseSender(), "PLAY"));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                boolean canSendPresence = ClientPlayNetworking.canSend(PresencePayload.TYPE);
                boolean canSendResponse = ClientPlayNetworking.canSend(ResponsePayload.TYPE);
                LOGGER.info("Guardian Phase 0A PLAY JOIN: presenceSendable={}, responseSendable={}, challengeReceivable={}",
                    canSendPresence,
                    canSendResponse,
                    ClientPlayNetworking.getReceived().contains(ChallengePayload.TYPE.id()));

                if (!canSendPresence) {
                    LOGGER.warn("Connected server did not advertise the Guardian PLAY presence channel; "
                        + "attempting presence anyway for fallback interoperability testing.");
                }

                int protocol = selectedProtocol();
                sender.sendPacket(new PresencePayload(ProtocolCodec.encodePresence(new Presence(protocol))));
                LOGGER.info("Attempted Guardian PLAY presence with protocol {} (serverAdvertised={}).",
                    protocol, canSendPresence);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not send Guardian PLAY presence", ex);
            }
        });
    }

    private static void respondToChallenge(ChallengePayload payload, PacketSender sender, String phase) {
        try {
            Challenge challenge = ProtocolCodec.decodeChallenge(payload.bytes());

            // Re-announce presence when Guardian actively challenges us. On Velocity, the initial
            // CONFIGURATION START presence can arrive before a backend connection is in flight and
            // therefore before Velocity exposes plugin messages to plugins. Re-announcing here makes
            // the distinct CERBERUS_REQUIRED vs CERBERUS_TIMEOUT states robust without changing the
            // standalone Paper Phase 0A fallback.
            int responseProtocol = selectedProtocol();
            sender.sendPacket(new PresencePayload(ProtocolCodec.encodePresence(new Presence(responseProtocol))));

            if (Boolean.getBoolean("cerberus.phase0a.suppressResponse")) {
                LOGGER.info(
                    "Received Guardian {} challenge; re-announced presence then deliberately suppressed "
                        + "the response for timeout testing.",
                    phase
                );
                return;
            }

            if (Boolean.getBoolean("cerberus.phase0a.malformed")) {
                sender.sendPacket(new ResponsePayload(new byte[] {0x00}));
                LOGGER.info("Sent deliberately malformed Guardian {} response.", phase);
                return;
            }

            List<ManifestEntry> manifest = buildTestManifest();
            Response response = new Response(responseProtocol, challenge.nonce(), manifest);
            sender.sendPacket(new ResponsePayload(ProtocolCodec.encodeResponse(response)));
            LOGGER.info("Responded to Guardian {} challenge with protocol {} and {} test manifest entries.",
                phase, responseProtocol, manifest.size());
        } catch (ProtocolException | RuntimeException ex) {
            LOGGER.warn("Ignoring invalid Guardian {} challenge", phase, ex);
        }
    }

    private static int selectedProtocol() {
        return Integer.getInteger("cerberus.phase0a.protocol", GuardianProtocol.VERSION);
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
