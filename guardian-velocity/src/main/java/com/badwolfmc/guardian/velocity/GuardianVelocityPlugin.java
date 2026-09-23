package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.BrandClassifier;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.Phase0ResponseValidator;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.ProxyAdmissionCodec;
import com.badwolfmc.guardian.protocol.Response;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Phase 0B.2 Velocity feasibility spike.
 *
 * <p>This checkpoint adds a trusted, short-lived Velocity-to-Paper admission assertion to the
 * already-proven proxy-side CONFIGURATION handshake. Geyser/Floodgate classification remains
 * deferred to the next Phase 0B checkpoint.</p>
 */
@Plugin(
    id = "guardian",
    name = "Guardian",
    version = "0.0.6-phase0b2",
    description = "Guardian Phase 0B.2 Velocity feasibility spike",
    authors = {"BadWolfMC"}
)
public final class GuardianVelocityPlugin {
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;

    private static final ChannelIdentifier PRESENCE =
        MinecraftChannelIdentifier.from(GuardianProtocol.PRESENCE_CHANNEL);
    private static final ChannelIdentifier CHALLENGE =
        MinecraftChannelIdentifier.from(GuardianProtocol.CHALLENGE_CHANNEL);
    private static final ChannelIdentifier RESPONSE =
        MinecraftChannelIdentifier.from(GuardianProtocol.RESPONSE_CHANNEL);
    private static final ChannelIdentifier PROXY_ADMISSION =
        MinecraftChannelIdentifier.from(GuardianProtocol.PROXY_ADMISSION_CHANNEL);

    private final ProxyServer server;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, VelocityAdmissionSession> sessions = new ConcurrentHashMap<>();
    private byte[] proxySecret;

    @Inject
    public GuardianVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Register all security-sensitive Guardian channels so PluginMessageEvent is fired for
        // them. The event handler below marks them handled before examining the source, which is
        // the Velocity-documented pattern for preventing client/backend spoofing or leakage.
        server.getChannelRegistrar().register(PRESENCE, CHALLENGE, RESPONSE, PROXY_ADMISSION);
        try {
            proxySecret = ProxyAdmissionCodec.decodeBase64Secret(
                System.getenv("GUARDIAN_PHASE0B_PROXY_SECRET"));
            logger.info("Guardian Phase 0B.2 trusted proxy assertions enabled; timeout={}s.",
                HANDSHAKE_TIMEOUT_SECONDS);
        } catch (IllegalArgumentException ex) {
            proxySecret = null;
            logger.warn("Guardian Phase 0B.2 proxy assertions are unavailable because "
                + "GUARDIAN_PHASE0B_PROXY_SECRET is not a valid Base64-encoded 32-byte shared secret: {}. "
                + "Proxy-side admission remains available, but Guardian-Paper in VELOCITY authority mode "
                + "will fail closed without an assertion.",
                ex.getMessage());
        }
    }

    @Subscribe
    public EventTask onPlayerConfiguration(PlayerConfigurationEvent event) {
        Player player = event.player();
        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player.getUniqueId(), ignored -> new VelocityAdmissionSession(newProxySessionId()));

        if (session.admitted()) {
            logger.info("Guardian Phase 0B.2 reconfiguration for {}: reusing admission for this proxy connection.",
                player.getUsername());
            sendProxyAdmission(player, event.server(), session);
            return null;
        }

        GuardianDecision existing = session.decision();
        if (existing != null) {
            applyDecision(player, existing);
            return null;
        }

        ClientClassification classification = BrandClassifier.classify(player.getClientBrand());
        session.setClassification(classification);
        logger.info("Guardian Phase 0B.2 configuration for {}: brand={}, classification={}, backend={}",
            player.getUsername(), String.valueOf(player.getClientBrand()), classification,
            event.server() == null ? "<none>" : event.server().getServerInfo().getName());

        if (classification == ClientClassification.JAVA_VANILLA) {
            GuardianDecision decision = GuardianDecision.allow(
                DecisionReason.VANILLA_POLICY, "vanilla allowed by Phase 0B.2");
            session.decide(decision);
            sendProxyAdmission(player, event.server(), session);
            return null;
        }

        if (classification != ClientClassification.JAVA_FABRIC) {
            GuardianDecision decision = GuardianDecision.deny(
                DecisionReason.CLIENT_DENIED,
                "unsupported/unknown Phase 0B.2 brand: " + String.valueOf(player.getClientBrand()));
            session.decide(decision);
            applyDecision(player, decision);
            return null;
        }

        armTimeout(player, session);
        startChallenge(player, session);

        CompletableFuture<Void> hold = session.decisionFuture().thenAccept(decision -> {
            if (decision.outcome() == DecisionOutcome.ALLOW) {
                sendProxyAdmission(player, event.server(), session);
            }
            applyDecision(player, decision);
        });

        // PlayerConfigurationEvent is explicitly awaited by Velocity. Returning a continuation
        // task therefore holds progression in CONFIGURATION without blocking a Velocity worker.
        return EventTask.resumeWhenComplete(hold.exceptionally(throwable -> {
            logger.error("Guardian Phase 0B.2 admission future failed for {}", player.getUsername(), throwable);
            player.disconnect(Component.text(messageFor(DecisionReason.CONFIGURATION_ERROR)));
            return null;
        }));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        ChannelIdentifier identifier = event.getIdentifier();
        if (!isGuardianChannel(identifier)) {
            return;
        }

        // Security invariant: never allow Guardian's client/proxy channels to pass through the
        // proxy in either direction, even if the packet is malformed or from the wrong source.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof Player player)) {
            logger.debug("Consumed backend-origin Guardian channel {} during Phase 0B.2.", identifier.getId());
            return;
        }

        VelocityAdmissionSession session = sessions.computeIfAbsent(
            player.getUniqueId(), ignored -> new VelocityAdmissionSession(newProxySessionId()));

        if (identifier.equals(PRESENCE)) {
            handlePresence(player, session, event.getData());
        } else if (identifier.equals(RESPONSE)) {
            handleResponse(player, session, event.getData());
        } else if (identifier.equals(PROXY_ADMISSION)) {
            // This channel is infrastructure-only. A normal client may know its name and format,
            // but Velocity consumes the packet and never forwards it to Guardian-Paper.
            logger.warn("Consumed client-origin proxy-admission assertion attempt from {}.",
                player.getUsername());
        } else {
            // guardian:challenge is proxy -> client only. A client-origin challenge is consumed.
            logger.warn("Consumed unexpected client-origin Guardian challenge from {}.", player.getUsername());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void handlePresence(Player player, VelocityAdmissionSession session, byte[] data) {
        if (data.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "CONFIGURATION presence exceeds Phase 0B.2 limit"));
            return;
        }

        final Presence presence;
        try {
            presence = ProtocolCodec.decodePresence(data);
        } catch (ProtocolException ex) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus CONFIGURATION presence: " + ex.getMessage()));
            return;
        }

        if (!session.recordPresence(presence.protocolVersion())) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "conflicting duplicate Cerberus presence"));
            return;
        }

        logger.info("Guardian Phase 0B.2 Cerberus presence from {}: protocol={}",
            player.getUsername(), presence.protocolVersion());

        if (presence.protocolVersion() != GuardianProtocol.VERSION) {
            session.decide(GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol " + presence.protocolVersion()
                    + ", Guardian supports " + GuardianProtocol.VERSION));
            return;
        }
    }

    private void startChallenge(Player player, VelocityAdmissionSession session) {
        if (session.decision() != null
            || session.classification() != ClientClassification.JAVA_FABRIC
            || !session.tryMarkChallengeSent()) {
            return;
        }

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        boolean sent;
        try {
            sent = player.sendPluginMessage(
                CHALLENGE,
                ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce))
            );
        } catch (RuntimeException ex) {
            logger.warn("Could not send Guardian Phase 0B.2 CONFIGURATION challenge to {}.",
                player.getUsername(), ex);
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "Velocity CONFIGURATION challenge send failed"));
            return;
        }

        if (!sent) {
            session.decide(GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR,
                "Velocity declined Guardian CONFIGURATION challenge send"));
            return;
        }

        logger.info("Guardian Phase 0B.2 CONFIGURATION challenge sent to {}.", player.getUsername());
    }

    private void handleResponse(Player player, VelocityAdmissionSession session, byte[] data) {
        if (session.decision() != null) {
            return;
        }
        if (!session.challengeSent() || session.nonce() == null) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "Cerberus response arrived before Guardian challenge"));
            return;
        }
        if (data.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "CONFIGURATION response exceeds Phase 0B.2 limit"));
            return;
        }

        final Response response;
        try {
            response = ProtocolCodec.decodeResponse(data);
        } catch (ProtocolException ex) {
            session.decide(GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus CONFIGURATION response: " + ex.getMessage()));
            return;
        }

        GuardianDecision decision = Phase0ResponseValidator.validate(session.nonce(), response);
        session.decide(decision);
        logger.info("Guardian Phase 0B.2 CONFIGURATION response from {}: {} / {} ({})",
            player.getUsername(), decision.outcome(), decision.reason(), decision.detail());
    }

    private void armTimeout(Player player, VelocityAdmissionSession session) {
        CompletableFuture.delayedExecutor(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (session.decision() != null) {
                return;
            }

            GuardianDecision timeoutDecision;
            if (!session.cerberusPresent()) {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CERBERUS_REQUIRED,
                    "Fabric client did not identify Cerberus after the Velocity CONFIGURATION challenge");
            } else if (session.challengeSent()) {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CERBERUS_TIMEOUT,
                    "Cerberus presence was received and challenge sent, but no valid response arrived in time");
            } else {
                timeoutDecision = GuardianDecision.deny(
                    DecisionReason.CONFIGURATION_ERROR,
                    "Cerberus presence was received but Guardian could not begin the challenge");
            }

            if (session.decide(timeoutDecision)) {
                logger.info("Guardian Phase 0B.2 timeout for {}: {}", player.getUsername(), timeoutDecision.reason());
            }
        });
    }

    private void applyDecision(Player player, GuardianDecision decision) {
        logger.info("Guardian Phase 0B.2 decision for {}: {} / {} ({})",
            player.getUsername(), decision.outcome(), decision.reason(), decision.detail());
        if (decision.outcome() == DecisionOutcome.DENY) {
            player.disconnect(Component.text(messageFor(decision.reason())));
        }
    }

    private boolean sendProxyAdmission(
        Player player, ServerConnection backend, VelocityAdmissionSession session
    ) {
        if (backend == null) {
            logger.warn("Guardian Phase 0B.2 could not assert admission for {} because no backend "
                + "configuration connection is available. Proxy-side admission remains authoritative.",
                player.getUsername());
            return false;
        }
        if (proxySecret == null) {
            logger.warn("Guardian Phase 0B.2 did not assert admission for {} -> {} because the shared "
                + "proxy secret is unavailable. A Guardian-Paper backend in VELOCITY authority mode "
                + "will fail closed.",
                player.getUsername(), backend.getServerInfo().getName());
            return false;
        }

        long issuedAt = System.currentTimeMillis();
        ProxyAdmissionAssertion assertion = new ProxyAdmissionAssertion(
            GuardianProtocol.PROXY_ASSERTION_VERSION,
            player.getUniqueId(),
            session.proxySessionId(),
            issuedAt,
            issuedAt + GuardianProtocol.PROXY_ASSERTION_TTL_MILLIS
        );

        final boolean sent;
        try {
            sent = backend.sendPluginMessage(
                PROXY_ADMISSION,
                ProxyAdmissionCodec.encode(assertion, proxySecret)
            );
        } catch (RuntimeException ex) {
            logger.warn("Guardian Phase 0B.2 proxy assertion send failed for {} -> {}.",
                player.getUsername(), backend.getServerInfo().getName(), ex);
            return false;
        }

        if (!sent) {
            logger.error("Guardian Phase 0B.2 backend {} declined proxy assertion for {}.",
                backend.getServerInfo().getName(), player.getUsername());
            return false;
        }

        logger.info("Guardian Phase 0B.2 trusted admission asserted for {} -> {}: session={}",
            player.getUsername(), backend.getServerInfo().getName(),
            HexFormat.of().formatHex(session.proxySessionId()));
        return true;
    }

    private byte[] newProxySessionId() {
        byte[] value = new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];
        random.nextBytes(value);
        return value;
    }

    private static boolean isGuardianChannel(ChannelIdentifier identifier) {
        return identifier.equals(PRESENCE)
            || identifier.equals(CHALLENGE)
            || identifier.equals(RESPONSE)
            || identifier.equals(PROXY_ADMISSION);
    }

    private static String messageFor(DecisionReason reason) {
        return switch (reason) {
            case CERBERUS_REQUIRED -> "Fabric is supported, but the Cerberus client mod is required.";
            case CERBERUS_TIMEOUT -> "Cerberus was detected, but the Guardian handshake timed out.";
            case CERBERUS_PROTOCOL_UNSUPPORTED ->
                "Cerberus is installed, but its Guardian protocol version is incompatible.";
            case MANIFEST_DENIED ->
                "Cerberus responded successfully, but the Phase 0B test manifest was denied.";
            case MANIFEST_INVALID -> "Cerberus returned invalid Guardian protocol data.";
            case CLIENT_DENIED -> "This client brand is not admitted by the Phase 0B prototype.";
            case CONFIGURATION_ERROR -> "Guardian could not complete the Phase 0B admission check.";
            default -> "Guardian denied this connection.";
        };
    }
}
