package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BrandClassifier;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.Phase0ResponseValidator;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.Response;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GuardianPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, AdmissionSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, GuardianProtocol.CHALLENGE_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.RESPONSE_CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Guardian Phase 0A enabled; CONFIGURATION-stage feasibility mode only.");
    }

    @Override
    public void onDisable() {
        sessions.values().forEach(session ->
            session.response().completeExceptionally(new IllegalStateException("Guardian Phase 0A disabled")));
        sessions.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            getLogger().warning("Initial configuration had no authenticated UUID; Phase 0A session not created.");
            return;
        }
        sessions.put(playerId, new AdmissionSession(playerId));
        String brand = connection.getClientBrandName();
        getLogger().info(() -> "Phase 0A initial configuration: " + displayName(connection)
            + " brand=" + String.valueOf(brand)
            + " listeningChannels=" + connection.getListeningPluginChannels());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            return;
        }

        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            // Reconfiguration is intentionally not attested in Phase 0A. Only sessions created by
            // PlayerConnectionInitialConfigureEvent participate in the spike.
            return;
        }

        ClientClassification classification = BrandClassifier.classify(connection.getClientBrandName());
        getLogger().info(() -> "Phase 0A classified " + displayName(connection) + " as " + classification);

        if (classification == ClientClassification.JAVA_VANILLA) {
            session.decide(GuardianDecision.allow(DecisionReason.VANILLA_POLICY, "vanilla allowed by Phase 0A"));
            return;
        }
        if (classification != ClientClassification.JAVA_FABRIC) {
            session.decide(GuardianDecision.deny(DecisionReason.CLIENT_DENIED,
                "unsupported/unknown Phase 0A brand: " + String.valueOf(connection.getClientBrandName())));
            return;
        }

        Set<String> channels = connection.getListeningPluginChannels();
        if (!channels.contains(GuardianProtocol.CHALLENGE_CHANNEL)) {
            session.decide(GuardianDecision.deny(DecisionReason.CERBERUS_REQUIRED,
                "Fabric client did not advertise the Cerberus challenge channel"));
            return;
        }

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        try {
            // Mark first so an immediate Netty-thread response cannot race the local state update.
            session.markChallengeSent();
            connection.sendPluginMessage(
                this,
                GuardianProtocol.CHALLENGE_CHANNEL,
                ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce))
            );
        } catch (RuntimeException ex) {
            getLogger().warning("Could not send Phase 0A challenge to " + displayName(connection) + ": " + ex);
            session.decide(GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR, "challenge send failed"));
            return;
        }

        try {
            Response response = session.response().get(HANDSHAKE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            GuardianDecision decision = Phase0ResponseValidator.validate(session.nonce(), response);
            session.decide(decision);
        } catch (TimeoutException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT,
                "challenge channel was present but no response arrived in time"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            session.decide(GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                "configuration wait interrupted"));
        } catch (ExecutionException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "response parsing failed: " + rootMessage(ex)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!(event.getConnection() instanceof PlayerConfigurationConnection connection)) {
            return; // The event also fires during the earlier LOGIN phase.
        }

        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            return;
        }
        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return; // Not an initial Phase 0A session (e.g. later reconfiguration).
        }

        GuardianDecision decision = session.decision();
        if (decision == null) {
            decision = GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                "configuration reached validation without a final Phase 0A decision");
        }

        GuardianDecision finalDecision = decision;
        getLogger().info(() -> "Phase 0A decision for " + displayName(connection) + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " (" + finalDecision.detail() + ")");

        if (decision.outcome() == DecisionOutcome.DENY) {
            event.kickMessage(Component.text(GuardianMessages.forReason(decision.reason())));
        }

        // The final validation event now owns the immutable decision, so the per-connection
        // handshake state is no longer needed. PlayerConnectionCloseEvent remains the abort/timeout
        // cleanup path for connections that never reach this point.
        sessions.remove(playerId, session);
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        sessions.remove(event.getPlayerUniqueId());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        // Phase 0A only accepts Cerberus responses before world entry. PLAY-stage responses are ignored.
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection connection, byte @NotNull [] message) {
        if (!GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            return;
        }
        if (!(connection instanceof PlayerConfigurationConnection configurationConnection)) {
            return;
        }

        UUID playerId = requirePlayerId(configurationConnection);
        if (playerId == null) {
            return;
        }
        AdmissionSession session = sessions.get(playerId);
        if (session == null || !session.challengeSent() || session.response().isDone()) {
            return;
        }
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.response().completeExceptionally(new ProtocolException("response exceeds Phase 0A limit"));
            return;
        }

        try {
            session.response().complete(ProtocolCodec.decodeResponse(message));
        } catch (ProtocolException ex) {
            session.response().completeExceptionally(ex);
        }
    }

    private static UUID requirePlayerId(PlayerConfigurationConnection connection) {
        return connection.getProfile().getId();
    }

    private static String displayName(PlayerConfigurationConnection connection) {
        String name = connection.getProfile().getName();
        UUID id = connection.getProfile().getId();
        return name != null ? name : String.valueOf(id);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
