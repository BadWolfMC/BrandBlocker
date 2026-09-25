package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.paper.config.GuardianRuntimeManager;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import com.badwolfmc.guardian.protection.ProtectionDecision;
import com.badwolfmc.guardian.protection.ProtectionEngine;
import com.badwolfmc.guardian.protection.ProtectionPermissionView;
import com.badwolfmc.guardian.protection.ProtectionPermissions;
import com.badwolfmc.guardian.protection.ProtectionPolicy;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Paper adapter for the platform-neutral Guardian Protection domain. */
final class PaperProtectionRuntime implements Listener {
    private final GuardianPaperPlugin plugin;
    private final GuardianRuntimeManager runtimeManager;
    private final GuardianMessageRenderer messageRenderer;
    private final ConcurrentHashMap<UUID, Set<String>> visibilityPermissionCache = new ConcurrentHashMap<>();

    private volatile ProtectionEngine engine;
    private boolean enabled;

    PaperProtectionRuntime(
        GuardianPaperPlugin plugin,
        GuardianRuntimeManager runtimeManager,
        GuardianMessageRenderer messageRenderer
    ) {
        this.plugin = plugin;
        this.runtimeManager = runtimeManager;
        this.messageRenderer = messageRenderer;
        this.engine = new ProtectionEngine(runtimeManager.current().settings().protectionPolicy());
    }

    void enable() {
        if (enabled) {
            return;
        }
        enabled = true;
        engine = new ProtectionEngine(runtimeManager.current().settings().protectionPolicy());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logPolicy("enabled");
        refreshOnlineCommandTrees();
    }

    void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        HandlerList.unregisterAll(this);
        visibilityPermissionCache.clear();
        refreshOnlineCommandTrees();
        plugin.getLogger().info("Guardian Protection domain disabled; online command trees refreshed.");
    }

    void reconfigure(GuardianRuntimeSnapshot previous, GuardianRuntimeSnapshot current) {
        ProtectionPolicy oldPolicy = previous.settings().protectionPolicy();
        ProtectionPolicy newPolicy = current.settings().protectionPolicy();
        engine = new ProtectionEngine(newPolicy);

        boolean visibilityChanged = newPolicy.commandTreeVisibilityDiffersFrom(oldPolicy);
        if (visibilityChanged) {
            visibilityPermissionCache.clear();
            refreshOnlineCommandTrees();
        }
        logPolicy("reconfigured");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // This event is intentionally the only command-execution hook used by Protection.
        // Console, command-block, and ordinary plugin dispatch paths are not intercepted.
        ProtectionEngine activeEngine = engine;
        ProtectionDecision decision = activeEngine
            .evaluateExecution(event.getMessage(), event.getPlayer()::hasPermission)
            .orElse(null);
        if (decision == null || !decision.denied()) {
            return;
        }

        event.setCancelled(true);
        GuardianRuntimeSnapshot snapshot = runtimeManager.current();
        String command = event.getMessage();
        event.getPlayer().sendMessage(messageRenderer.renderProtectionDenial(snapshot, decision, command));
        notifyStaff(snapshot, activeEngine.policy(), event.getPlayer(), decision, command);

        plugin.getLogger().info(() -> "Guardian Protection denied player command: player="
            + event.getPlayer().getName() + " uuid=" + event.getPlayer().getUniqueId()
            + " root=" + decision.root().value() + " reason=" + decision.reason());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandTree(PlayerCommandSendEvent event) {
        ProtectionEngine activeEngine = engine;
        List<String> roots = List.copyOf(event.getCommands());
        Set<String> granted = captureVisibilityPermissions(event.getPlayer(), roots, activeEngine.policy());
        visibilityPermissionCache.put(event.getPlayer().getUniqueId(), granted);
        ProtectionPermissionView permissions = granted::contains;

        event.getCommands().removeIf(root -> activeEngine.evaluateVisibility(root, permissions)
            .map(ProtectionDecision::denied)
            .orElse(false));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || buffer.isBlank()) {
            return;
        }

        // Paper exposes the raw request buffer. The domain normalizer deliberately accepts both
        // slash and no-slash forms, so suggestion suppression does not depend on transport spelling.
        ProtectionEngine activeEngine = engine;
        Set<String> granted = visibilityPermissionCache.get(event.getPlayer().getUniqueId());
        ProtectionPermissionView permissions;
        if (granted != null) {
            permissions = granted::contains;
        } else if (!event.isAsynchronous()) {
            Set<String> captured = captureVisibilityPermissions(
                event.getPlayer(), List.of(buffer), activeEngine.policy());
            permissions = captured::contains;
        } else {
            // Do not make unsynchronised Bukkit permission calls from an asynchronous suggestion event.
            // The command-tree event normally populated the cache first; fail closed for disclosure if not.
            permissions = ignored -> false;
        }

        boolean hidden = activeEngine.evaluateVisibility(buffer, permissions)
            .map(ProtectionDecision::denied)
            .orElse(false);
        if (hidden) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visibilityPermissionCache.remove(event.getPlayer().getUniqueId());
    }

    void refreshOnlineCommandTrees() {
        for (Player player : List.copyOf(plugin.getServer().getOnlinePlayers())) {
            player.updateCommands();
        }
    }

    private Set<String> captureVisibilityPermissions(
        Player player,
        Collection<String> roots,
        ProtectionPolicy policy
    ) {
        HashSet<String> granted = new HashSet<>();
        capture(player, granted, ProtectionPermissions.BYPASS);
        capture(player, granted, ProtectionPermissions.VISIBILITY_BYPASS);
        if (policy.perCommandVisibilityBypass()) {
            for (String rawRoot : roots) {
                try {
                    capture(player, granted, ProtectionPermissions.visibilityBypass(rawRoot));
                } catch (IllegalArgumentException ignored) {
                    // Invalid/non-command buffers are not eligible for dynamic permission leaves.
                }
            }
        }
        return Set.copyOf(granted);
    }

    private static void capture(Player player, Set<String> granted, String permission) {
        if (player.hasPermission(permission)) {
            granted.add(permission);
        }
    }

    private void notifyStaff(
        GuardianRuntimeSnapshot snapshot,
        ProtectionPolicy policy,
        Player actor,
        ProtectionDecision decision,
        String command
    ) {
        if (!policy.notificationsEnabled()) {
            return;
        }
        var message = messageRenderer.renderProtectionNotification(
            snapshot, decision, actor.getName(), command);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.hasPermission(ProtectionPermissions.NOTIFY)) {
                viewer.sendMessage(message);
            }
        }
    }

    private void logPolicy(String action) {
        ProtectionPolicy policy = engine.policy();
        plugin.getLogger().info("Guardian Protection " + action
            + ": execution=" + policy.execution().enabled() + "/" + policy.execution().roots().size()
            + ", visibility=" + policy.visibility().enabled() + "/" + policy.visibility().mode()
            + "/" + policy.visibility().roots().size()
            + ", namespaces=" + policy.namespaces().enabled() + "/" + policy.namespaces().mode()
            + "/" + policy.namespaces().roots().size()
            + ", notifications=" + policy.notificationsEnabled() + ".");
    }
}
