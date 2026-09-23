package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.BrandClassifier;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionOutcome;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.core.GuardianDecision;
import com.badwolfmc.guardian.core.Phase0ResponseValidator;
import com.badwolfmc.guardian.core.ProxyAdmissionValidator;
import com.badwolfmc.guardian.protocol.Challenge;
import com.badwolfmc.guardian.protocol.ConnectionOrigin;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Presence;
import com.badwolfmc.guardian.protocol.ProtocolCodec;
import com.badwolfmc.guardian.protocol.ProtocolException;
import com.badwolfmc.guardian.protocol.ProxyAdmissionAssertion;
import com.badwolfmc.guardian.protocol.ProxyAdmissionCodec;
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
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guardian-Paper Phase 0 feasibility adapter.
 *
 * <p>In standalone authority mode it preserves the Phase 0A hybrid: CONFIGURATION handles brand
 * and Cerberus presence/protocol, while compatible Fabric clients complete the nonce exchange in
 * immediate quarantined PLAY. In Velocity authority mode Paper does not re-attest the client; it
 * accepts only a short-lived infrastructure-authenticated admission assertion from
 * Guardian-Velocity.</p>
 */
public final class GuardianPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final int DEFAULT_HANDSHAKE_TIMEOUT_SECONDS = 10;
    private static final int MAX_HANDSHAKE_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_CHALLENGE_CHANNEL_WAIT_TICKS = 40;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, AdmissionSession> sessions = new ConcurrentHashMap<>();
    private long handshakeTimeoutTicks;
    private int challengeChannelWaitTicks;
    private PaperAuthorityMode authorityMode = PaperAuthorityMode.STANDALONE;
    private byte[] proxySecret;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPhase0Settings();

        getServer().getMessenger().registerOutgoingPluginChannel(this, GuardianProtocol.CHALLENGE_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.PRESENCE_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.RESPONSE_CHANNEL, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, GuardianProtocol.PROXY_ADMISSION_CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        if (authorityMode == PaperAuthorityMode.VELOCITY) {
            getLogger().info("Guardian Phase 0B.3 Paper backend enabled in VELOCITY authority mode; "
                + "local Cerberus policy evaluation is disabled and a trusted proxy assertion is required.");
        } else {
            getLogger().info("Guardian standalone Paper authority enabled; CONFIGURATION presence + PLAY quarantine "
                + "fallback mode. handshakeTimeout=" + (handshakeTimeoutTicks / 20.0)
                + "s, challengeChannelWait=" + challengeChannelWaitTicks + " ticks.");
        }
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
            return; // Reconfiguration is outside the current feasibility spike.
        }

        if (authorityMode == PaperAuthorityMode.VELOCITY) {
            getLogger().info(() -> "Phase 0B.3 backend configuration for " + displayName(connection)
                + ": authority=VELOCITY, brand=" + String.valueOf(connection.getClientBrandName())
                + ", proxyAssertion=" + (session.proxyAdmission() != null ? "present" : "pending"));
            // Do not wait here. Velocity's PlayerConfigurationEvent is fired after the backend has
            // finished its configuration work; blocking this async Paper event would risk a
            // circular wait. The final PlayerConnectionValidateLoginEvent is the admission gate.
            return;
        }

        evaluateStandaloneConfiguration(connection, session, false);
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

        if (authorityMode == PaperAuthorityMode.VELOCITY) {
            GuardianDecision decision = session.decision();
            if (decision == null) {
                decision = GuardianDecision.deny(
                    proxySecret == null ? DecisionReason.CONFIGURATION_ERROR : DecisionReason.PROXY_ASSERTION_REQUIRED,
                    proxySecret == null
                        ? "Velocity authority configured but GUARDIAN_PHASE0B_PROXY_SECRET is unavailable"
                        : "no valid Guardian-Velocity admission assertion arrived before final validation"
                );
                session.decide(decision);
            }

            GuardianDecision finalDecision = session.decision();
            getLogger().info(() -> "Phase 0B.3 backend pre-world decision for " + displayName(connection) + ": "
                + finalDecision.outcome() + " / " + finalDecision.reason() + " ("
                + finalDecision.detail() + ")");
            if (finalDecision.outcome() == DecisionOutcome.DENY) {
                event.kickMessage(Component.text(GuardianMessages.forReason(finalDecision.reason())));
            }
            sessions.remove(playerId, session);
            return;
        }

        // Velocity currently mirrors the client brand to a backend late in CONFIGURATION. If the
        // async configuration event observed null, retry once here before deciding. This preserves
        // direct standalone behavior while giving transparent Velocity mode a supported late gate.
        if (session.decision() == null && !session.playHandshakeRequired()) {
            evaluateStandaloneConfiguration(connection, session, true);
        }

        if (session.playHandshakeRequired() && session.decision() == null) {
            getLogger().info(() -> "Standalone CONFIGURATION gate passed for " + displayName(connection)
                + ": compatible Cerberus presence detected; nonce handshake deferred to quarantined PLAY.");
            return; // Keep the session for PlayerJoinEvent / PLAY plugin messaging.
        }

        GuardianDecision decision = session.decision();
        if (decision == null) {
            decision = GuardianDecision.deny(DecisionReason.CLIENT_DENIED,
                "client brand remained unavailable through final standalone validation");
            session.decide(decision);
        }

        GuardianDecision finalDecision = session.decision();
        getLogger().info(() -> "Standalone pre-world decision for " + displayName(connection) + ": "
            + finalDecision.outcome() + " / " + finalDecision.reason() + " ("
            + finalDecision.detail() + ")");

        if (finalDecision.outcome() == DecisionOutcome.DENY) {
            event.kickMessage(Component.text(GuardianMessages.forReason(finalDecision.reason())));
        }
        sessions.remove(playerId, session);
    }

    private void evaluateStandaloneConfiguration(
        PlayerConfigurationConnection connection, AdmissionSession session, boolean finalAttempt
    ) {
        if (session.decision() != null || session.playHandshakeRequired()) {
            return;
        }

        String brand = connection.getClientBrandName();
        if (brand == null || brand.isBlank()) {
            getLogger().info(() -> "Standalone configuration for " + displayName(connection)
                + ": brand is not yet available; " + (finalAttempt ? "final validation will deny"
                : "deferring classification to PlayerConnectionValidateLoginEvent")
                + ", cerberusPresent=" + session.cerberusPresent());
            return;
        }

        ClientClassification classification = BrandClassifier.classify(brand);
        getLogger().info(() -> "Standalone configuration evaluation for " + displayName(connection)
            + ": brand=" + brand
            + ", classification=" + classification
            + ", cerberusPresent=" + session.cerberusPresent()
            + ", cerberusProtocol=" + session.cerberusProtocol()
            + ", listeningChannels=" + connection.getListeningPluginChannels());

        if (classification == ClientClassification.JAVA_VANILLA) {
            session.decide(GuardianDecision.allow(DecisionReason.VANILLA_POLICY,
                "vanilla allowed by standalone Paper authority"));
            return;
        }
        if (classification != ClientClassification.JAVA_FABRIC) {
            session.decide(GuardianDecision.deny(DecisionReason.CLIENT_DENIED,
                "unsupported/unknown standalone brand: " + brand));
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

        // Paper's supported CONFIGURATION send path still has the Fabric registration limitation
        // proven in Phase 0A. Defer only the nonce challenge/response to quarantined PLAY.
        session.requirePlayHandshake();
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
            handshakeTimeoutTicks);
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

        if (GuardianProtocol.PROXY_ADMISSION_CHANNEL.equals(channel)) {
            if (authorityMode == PaperAuthorityMode.VELOCITY) {
                handleProxyAdmission(configurationConnection, session, message);
            } else {
                getLogger().warning("Ignoring proxy admission assertion while Paper is in standalone authority mode for "
                    + displayName(configurationConnection));
            }
            return;
        }

        if (authorityMode == PaperAuthorityMode.VELOCITY) {
            // Guardian-Velocity is required to consume the client-facing Cerberus channels. If one
            // reaches the backend, the network trust boundary is not behaving as designed.
            if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)
                || GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
                session.decide(GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                    "client-facing Guardian channel leaked through Velocity to the backend"));
                getLogger().warning("Phase 0B.3 channel-isolation violation for "
                    + displayName(configurationConnection) + ": " + channel);
            }
            return;
        }

        if (GuardianProtocol.PRESENCE_CHANNEL.equals(channel)) {
            handleConfigurationPresence(configurationConnection, session, message);
            return;
        }
        if (GuardianProtocol.RESPONSE_CHANNEL.equals(channel)) {
            // A CONFIGURATION response is not expected in standalone fallback mode.
            session.decide(GuardianDecision.deny(DecisionReason.MANIFEST_INVALID,
                "unexpected Cerberus response during CONFIGURATION fallback mode"));
        }
    }

    private void handleProxyAdmission(
        PlayerConfigurationConnection connection, AdmissionSession session, byte[] message
    ) {
        if (proxySecret == null) {
            session.decide(GuardianDecision.deny(DecisionReason.CONFIGURATION_ERROR,
                "Velocity authority configured without an available shared proxy secret"));
            return;
        }

        final ProxyAdmissionAssertion assertion;
        try {
            assertion = ProxyAdmissionCodec.decodeAndVerify(message, proxySecret);
        } catch (ProtocolException | IllegalArgumentException ex) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "invalid trusted proxy assertion: " + ex.getMessage()));
            getLogger().warning("Rejected Phase 0B.3 proxy assertion for " + displayName(connection)
                + ": " + ex.getMessage());
            return;
        }

        long now = System.currentTimeMillis();
        UUID expectedPlayerId = requirePlayerId(connection);
        if (expectedPlayerId == null) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "authenticated player UUID unavailable while verifying proxy assertion"));
            return;
        }
        GuardianDecision metadataDecision = ProxyAdmissionValidator.validate(assertion, expectedPlayerId, now);
        if (metadataDecision.outcome() == DecisionOutcome.DENY) {
            session.decide(metadataDecision);
            getLogger().warning("Rejected Phase 0B.3 proxy assertion metadata for "
                + displayName(connection) + ": " + metadataDecision.detail());
            return;
        }

        if (!session.recordProxyAdmission(assertion)) {
            session.decide(GuardianDecision.deny(DecisionReason.PROXY_ASSERTION_INVALID,
                "conflicting duplicate proxy admission assertion"));
            return;
        }

        sanityCheckBackendFloodgate(connection, assertion);
        session.decide(metadataDecision);
        getLogger().info(() -> "Phase 0B.3 trusted proxy admission received for " + displayName(connection)
            + ": session=" + HexFormat.of().formatHex(assertion.proxySessionId())
            + ", origin=" + assertion.connectionOrigin()
            + ", expiresInMs=" + Math.max(0L, assertion.expiresAtEpochMillis() - now));
    }

    private void sanityCheckBackendFloodgate(
        PlayerConfigurationConnection connection, ProxyAdmissionAssertion assertion
    ) {
        boolean proxyBedrock = assertion.connectionOrigin() == ConnectionOrigin.BEDROCK;
        org.bukkit.plugin.Plugin floodgate = getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            if (proxyBedrock) {
                getLogger().info(() -> "Phase 0B.3 backend Floodgate sanity check skipped for "
                    + displayName(connection) + ": proxy origin=BEDROCK, backend Floodgate unavailable.");
            }
            return;
        }

        final boolean backendBedrock;
        try {
            backendBedrock = FloodgateBackendLookup.isFloodgatePlayer(assertion.playerId());
        } catch (RuntimeException | LinkageError ex) {
            getLogger().warning("Phase 0B.3 backend Floodgate sanity check failed for "
                + displayName(connection) + ": " + ex.getMessage());
            return;
        }

        if (proxyBedrock == backendBedrock) {
            getLogger().info(() -> "Phase 0B.3 backend Floodgate sanity check agrees for "
                + displayName(connection) + ": proxyOrigin=" + assertion.connectionOrigin()
                + ", backendFloodgate=" + backendBedrock);
            return;
        }

        // Phase 0B.3 deliberately keeps Guardian-Velocity as the sole admission authority.
        // A backend mismatch is observable defense-in-depth evidence, not a second policy engine.
        // Production fail-closed behavior remains a later design decision after live testing.
        getLogger().warning("Phase 0B.3 BACKEND FLOODGATE DISAGREEMENT for " + displayName(connection)
            + ": proxyOrigin=" + assertion.connectionOrigin()
            + ", backendFloodgate=" + backendBedrock
            + ". Trusted proxy admission remains authoritative for this feasibility spike.");
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

        sendPlayChallenge(player, session, 0);
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

    private void sendPlayChallenge(Player player, AdmissionSession session, int waitedTicks) {
        if (session.challengeSent() || session.decision() != null || !player.isOnline()) {
            return;
        }

        Set<String> listeningChannels = player.getListeningPluginChannels();
        if (!listeningChannels.contains(GuardianProtocol.CHALLENGE_CHANNEL)) {
            if (waitedTicks < challengeChannelWaitTicks) {
                if (waitedTicks == 0) {
                    getLogger().info("Cerberus PLAY presence arrived before Paper saw the challenge channel for "
                        + player.getName() + "; waiting up to " + challengeChannelWaitTicks
                        + " ticks for registration. listeningChannels=" + listeningChannels);
                }
                getServer().getScheduler().runTaskLater(
                    this,
                    () -> sendPlayChallenge(player, session, waitedTicks + 1),
                    1L
                );
            } else {
                finishPlayDecision(player, session, GuardianDecision.deny(
                    DecisionReason.CERBERUS_TIMEOUT,
                    "Cerberus PLAY challenge channel was not registered within "
                        + challengeChannelWaitTicks + " ticks"));
            }
            return;
        }

        if (waitedTicks > 0) {
            getLogger().info("Guardian challenge channel became available for " + player.getName()
                + " after " + waitedTicks + " tick(s).");
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


    private void loadPhase0Settings() {
        String configuredAuthority = getConfig().getString("phase0.authority", "standalone");
        try {
            authorityMode = PaperAuthorityMode.parse(configuredAuthority);
        } catch (IllegalArgumentException ex) {
            getLogger().severe(ex.getMessage() + "; failing closed with VELOCITY authority mode.");
            authorityMode = PaperAuthorityMode.VELOCITY;
        }

        proxySecret = null;
        if (authorityMode == PaperAuthorityMode.VELOCITY) {
            try {
                proxySecret = ProxyAdmissionCodec.decodeBase64Secret(
                    System.getenv("GUARDIAN_PHASE0B_PROXY_SECRET"));
            } catch (IllegalArgumentException ex) {
                getLogger().severe("Velocity authority requires GUARDIAN_PHASE0B_PROXY_SECRET to be a "
                    + "Base64-encoded 32-byte secret. Connections will fail closed: " + ex.getMessage());
            }
        }

        int timeoutSeconds = getConfig().getInt(
            "phase0.handshake-timeout-seconds",
            DEFAULT_HANDSHAKE_TIMEOUT_SECONDS
        );
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_HANDSHAKE_TIMEOUT_SECONDS) {
            getLogger().warning("phase0.handshake-timeout-seconds must be between 1 and "
                + MAX_HANDSHAKE_TIMEOUT_SECONDS + "; using default "
                + DEFAULT_HANDSHAKE_TIMEOUT_SECONDS + ".");
            timeoutSeconds = DEFAULT_HANDSHAKE_TIMEOUT_SECONDS;
        }
        handshakeTimeoutTicks = timeoutSeconds * 20L;

        int configuredChannelWait = getConfig().getInt(
            "phase0.challenge-channel-wait-ticks",
            DEFAULT_CHALLENGE_CHANNEL_WAIT_TICKS
        );
        int maxChannelWait = (int) Math.min(Integer.MAX_VALUE, handshakeTimeoutTicks);
        if (configuredChannelWait < 1 || configuredChannelWait > maxChannelWait) {
            int fallback = Math.min(DEFAULT_CHALLENGE_CHANNEL_WAIT_TICKS, maxChannelWait);
            getLogger().warning("phase0.challenge-channel-wait-ticks must be between 1 and "
                + maxChannelWait + " for the configured handshake timeout; using " + fallback + ".");
            configuredChannelWait = fallback;
        }
        challengeChannelWaitTicks = configuredChannelWait;
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
