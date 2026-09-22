package com.badwolfmc.guardian.paper;

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
import com.badwolfmc.guardian.protocol.Response;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 0A fallback feasibility spike.
 *
 * <p>CONFIGURATION is retained for brand classification and Cerberus presence/protocol detection.
 * Paper 26.2 cannot send Guardian's CONFIGURATION challenge to a stock Fabric client through the
 * supported plugin-messaging API because the client channel registration never reaches Paper.
 * Compatible Cerberus clients therefore cross into PLAY in an immediate quarantine, complete the
 * nonce challenge/response there, and are released or disconnected.</p>
 */
public final class GuardianPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);
    private static final long HANDSHAKE_TIMEOUT_TICKS = HANDSHAKE_TIMEOUT.toSeconds() * 20L;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, AdmissionSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, GuardianProtocol.CHALLENGE_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.PRESENCE_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.RESPONSE_CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Guardian Phase 0A enabled; CONFIGURATION presence + PLAY quarantine fallback feasibility mode.");
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
        getLogger().info(() -> "Phase 0A initial configuration: " + displayName(connection)
            + " brand=" + String.valueOf(connection.getClientBrandName())
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
            return; // Reconfiguration is outside the Phase 0A spike.
        }

        ClientClassification classification = BrandClassifier.classify(connection.getClientBrandName());
        getLogger().info(() -> "Phase 0A configuration finalization for " + displayName(connection)
            + ": classification=" + classification
            + ", cerberusPresent=" + session.cerberusPresent()
            + ", cerberusProtocol=" + session.cerberusProtocol()
            + ", listeningChannels=" + connection.getListeningPluginChannels());

        if (classification == ClientClassification.JAVA_VANILLA) {
            session.decide(GuardianDecision.allow(DecisionReason.VANILLA_POLICY, "vanilla allowed by Phase 0A"));
            return;
        }
        if (classification != ClientClassification.JAVA_FABRIC) {
            session.decide(GuardianDecision.deny(DecisionReason.CLIENT_DENIED,
                "unsupported/unknown Phase 0A brand: " + String.valueOf(connection.getClientBrandName())));
            return;
        }

        // Malformed presence or incompatible protocol may already have produced a structured denial.
        if (session.decision() != null) {
            return;
        }

        if (!session.cerberusPresent()) {
            session.decide(GuardianDecision.deny(DecisionReason.CERBERUS_REQUIRED,
                "Fabric client did not send the Cerberus CONFIGURATION presence payload"));
            return;
        }

        // Bidirectional CONFIGURATION plugin messaging is blocked by channel-registration
        // interoperability on Paper 26.2/Fabric 26.2. Preserve the useful pre-world Cerberus
        // presence check, then defer only the nonce challenge/response to quarantined PLAY.
        session.requirePlayHandshake();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!(event.getConnection() instanceof PlayerConfigurationConnection connection)) {
            return;
        }

        UUID playerId = requirePlayerId(connection);
        if (playerId == null) {
            return;
        }

        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (session.playHandshakeRequired() && session.decision() == null) {
            getLogger().info(() -> "Phase 0A CONFIGURATION gate passed for " + displayName(connection)
                + ": compatible Cerberus presence detected; nonce handshake deferred to quarantined PLAY.");
            return; // Keep the session for PlayerJoinEvent / PLAY plugin messaging.
        }

        GuardianDecision decision = session.decision();
        if (decision == null) {
            decision = GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                "configuration reached validation without a final Phase 0A decision");
        }

        GuardianDecision finalDecision = decision;
        getLogger().info(() -> "Phase 0A pre-world decision for " + displayName(connection) + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " (" + finalDecision.detail() + ")");

        if (decision.outcome() == DecisionOutcome.DENY) {
            event.kickMessage(Component.text(GuardianMessages.forReason(decision.reason())));
        }
        sessions.remove(playerId, session);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AdmissionSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.playHandshakeRequired()) {
            return;
        }

        session.setQuarantined(true);
        getLogger().info(() -> "Phase 0A PLAY quarantine active for " + player.getName()
            + "; listeningChannels=" + player.getListeningPluginChannels());

        getServer().getScheduler().runTaskLater(this,
            () -> handleHandshakeTimeout(player.getUniqueId()),
            HANDSHAKE_TIMEOUT_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AdmissionSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.response().completeExceptionally(new IllegalStateException("player quit"));
        }
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        AdmissionSession session = sessions.remove(event.getPlayerUniqueId());
        if (session != null) {
            session.response().completeExceptionally(new IllegalStateException("connection closed"));
        }
    }

    // --- Minimal PLAY quarantine for the feasibility spike ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isQuarantined(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) event.setCancelled(true);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        AdmissionSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.playHandshakeRequired()) {
            return;
        }

        if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)) {
            handlePlayPresence(player, session, message);
            return;
        }
        if (GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            handlePlayResponse(player, session, message);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection connection, byte @NotNull [] message) {
        if (!(connection instanceof PlayerConfigurationConnection configurationConnection)) {
            return;
        }

        UUID playerId = requirePlayerId(configurationConnection);
        if (playerId == null) {
            return;
        }
        AdmissionSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)) {
            handleConfigurationPresence(configurationConnection, session, message);
            return;
        }
        if (GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            // A CONFIGURATION response is not expected in fallback mode, but keep malformed/stray
            // data from being silently mistaken for a successful PLAY handshake.
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "unexpected Cerberus response during CONFIGURATION fallback mode"));
        }
    }

    private void handleConfigurationPresence(PlayerConfigurationConnection connection, AdmissionSession session, byte[] message) {
        Presence presence = decodePresence(session, message, "CONFIGURATION");
        if (presence == null) {
            return;
        }

        getLogger().info(() -> "Phase 0A Cerberus CONFIGURATION presence from " + displayName(connection)
            + ": protocol=" + presence.protocolVersion()
            + ", brand=" + String.valueOf(connection.getClientBrandName())
            + ", listeningChannels=" + connection.getListeningPluginChannels());

        if (presence.protocolVersion() != GuardianProtocol.VERSION) {
            session.decide(GuardianDecision.deny(DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol " + presence.protocolVersion()
                    + ", Guardian supports " + GuardianProtocol.VERSION));
        }
    }

    private void handlePlayPresence(Player player, AdmissionSession session, byte[] message) {
        Presence presence = decodePresence(session, message, "PLAY");
        if (presence == null) {
            GuardianDecision decision = session.decision();
            if (decision != null) {
                finishPlayDecision(player, session, decision);
            }
            return;
        }
        if (session.decision() != null) {
            finishPlayDecision(player, session, session.decision());
            return;
        }

        getLogger().info(() -> "Phase 0A Cerberus PLAY presence from " + player.getName()
            + ": protocol=" + presence.protocolVersion()
            + ", listeningChannels=" + player.getListeningPluginChannels());

        if (presence.protocolVersion() != GuardianProtocol.VERSION) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.CERBERUS_PROTOCOL_UNSUPPORTED,
                "Cerberus announced protocol " + presence.protocolVersion()
                    + ", Guardian supports " + GuardianProtocol.VERSION));
            return;
        }

        sendPlayChallenge(player, session, false);
    }

    private Presence decodePresence(AdmissionSession session, byte[] message, String phase) {
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                phase + " presence exceeds Phase 0A limit"));
            return null;
        }

        final Presence presence;
        try {
            presence = ProtocolCodec.decodePresence(message);
        } catch (ProtocolException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "invalid Cerberus " + phase + " presence: " + ex.getMessage()));
            return null;
        }

        if (!session.recordPresence(presence.protocolVersion())) {
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "conflicting duplicate Cerberus presence"));
            return null;
        }
        return presence;
    }

    private void sendPlayChallenge(Player player, AdmissionSession session, boolean retry) {
        if (session.challengeSent() || session.decision() != null || !player.isOnline()) {
            return;
        }

        Set<String> listeningChannels = player.getListeningPluginChannels();
        if (!listeningChannels.contains(GuardianProtocol.CHALLENGE_CHANNEL)) {
            if (!retry) {
                getLogger().warning("Cerberus PLAY presence arrived before Paper saw the challenge channel for "
                    + player.getName() + "; retrying once next tick. listeningChannels=" + listeningChannels);
                getServer().getScheduler().runTask(this, () -> sendPlayChallenge(player, session, true));
            } else {
                finishPlayDecision(player, session, GuardianDecision.deny(
                    DecisionReason.CONFIGURATION_ERROR,
                    "PLAY challenge channel was not registered by the Cerberus client"));
            }
            return;
        }

        if (!session.tryMarkChallengeSent()) {
            return;
        }

        byte[] nonce = new byte[GuardianProtocol.NONCE_BYTES];
        random.nextBytes(nonce);
        session.setNonce(nonce);

        try {
            player.sendPluginMessage(
                this,
                GuardianProtocol.CHALLENGE_CHANNEL,
                ProtocolCodec.encodeChallenge(new Challenge(GuardianProtocol.VERSION, nonce))
            );
            getLogger().info(() -> "Phase 0A PLAY challenge sent to " + player.getName()
                + "; listeningChannels=" + player.getListeningPluginChannels());
        } catch (RuntimeException ex) {
            getLogger().warning("Could not send Phase 0A PLAY challenge to " + player.getName() + ": " + ex);
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.CONFIGURATION_ERROR, "PLAY challenge send failed"));
        }
    }

    private void handlePlayResponse(Player player, AdmissionSession session, byte[] message) {
        if (!session.challengeSent() || session.decision() != null) {
            return;
        }
        if (message.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "PLAY response exceeds Phase 0A limit"));
            return;
        }

        final Response response;
        try {
            response = ProtocolCodec.decodeResponse(message);
        } catch (ProtocolException ex) {
            finishPlayDecision(player, session, GuardianDecision.deny(
                DecisionReason.MANIFEST_INVALID, "invalid PLAY response: " + ex.getMessage()));
            return;
        }

        session.response().complete(response);
        GuardianDecision decision = Phase0ResponseValidator.validate(session.nonce(), response);
        finishPlayDecision(player, session, decision);
    }

    private void handleHandshakeTimeout(UUID playerId) {
        AdmissionSession session = sessions.get(playerId);
        if (session == null || !session.playHandshakeRequired() || session.decision() != null) {
            return;
        }

        Player player = getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            sessions.remove(playerId, session);
            return;
        }

        GuardianDecision decision = session.challengeSent()
            ? GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT,
                "Cerberus entered PLAY and received/was sent a challenge, but no valid response arrived in time")
            : GuardianDecision.deny(DecisionReason.CERBERUS_TIMEOUT,
                "Cerberus was detected during CONFIGURATION but did not complete PLAY handshake startup in time");
        finishPlayDecision(player, session, decision);
    }

    private void finishPlayDecision(Player player, AdmissionSession session, GuardianDecision decision) {
        session.decide(decision);
        GuardianDecision finalDecision = session.decision();
        if (finalDecision == null) {
            return;
        }

        getLogger().info(() -> "Phase 0A PLAY decision for " + player.getName() + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " (" + finalDecision.detail() + ")");

        if (finalDecision.outcome() == DecisionOutcome.ALLOW) {
            session.setQuarantined(false);
            sessions.remove(player.getUniqueId(), session);
            getLogger().info(() -> "Phase 0A PLAY quarantine released for " + player.getName());
        } else {
            player.kick(Component.text(GuardianMessages.forReason(finalDecision.reason())));
            sessions.remove(player.getUniqueId(), session);
        }
    }

    private boolean isQuarantined(Player player) {
        AdmissionSession session = sessions.get(player.getUniqueId());
        return session != null && session.quarantined();
    }

    private static UUID requirePlayerId(PlayerConfigurationConnection connection) {
        return connection.getProfile().getId();
    }

    private static String displayName(PlayerConfigurationConnection connection) {
        String name = connection.getProfile().getName();
        UUID id = connection.getProfile().getId();
        return name != null ? name : String.valueOf(id);
    }
}
