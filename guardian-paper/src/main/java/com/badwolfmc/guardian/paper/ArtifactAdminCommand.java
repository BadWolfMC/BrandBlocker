package com.badwolfmc.guardian.paper;

import com.badwolfmc.guardian.core.artifact.ArtifactCatalogException;
import com.badwolfmc.guardian.core.artifact.ArtifactImportResult;
import com.badwolfmc.guardian.core.artifact.ArtifactImportService;
import com.badwolfmc.guardian.paper.locale.GuardianMessageRenderer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class ArtifactAdminCommand implements CommandExecutor, TabCompleter {
    static final String PERMISSION = "guardian.artifacts.scan";

    private final GuardianPaperPlugin plugin;
    private final ArtifactImportService importService;
    private final GuardianMessageRenderer renderer;
    private final AtomicBoolean scanRunning = new AtomicBoolean();

    ArtifactAdminCommand(
        GuardianPaperPlugin plugin,
        ArtifactImportService importService,
        GuardianMessageRenderer renderer
    ) {
        this.plugin = plugin;
        this.importService = importService;
        this.renderer = renderer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(renderer.render(
                plugin.runtimeManager().current(),
                "artifacts.command.no-permission",
                TagResolver.empty()
            ));
            return true;
        }
        if (args.length != 2
            || !"artifacts".equalsIgnoreCase(args[0])
            || !"scan".equalsIgnoreCase(args[1])) {
            sender.sendMessage(renderer.render(
                plugin.runtimeManager().current(),
                "artifacts.command.usage",
                TagResolver.empty()
            ));
            return true;
        }
        if (!scanRunning.compareAndSet(false, true)) {
            sender.sendMessage(renderer.render(
                plugin.runtimeManager().current(),
                "artifacts.scan.already-running",
                TagResolver.empty()
            ));
            return true;
        }

        sender.sendMessage(renderer.render(
            plugin.runtimeManager().current(),
            "artifacts.scan.started",
            TagResolver.empty()
        ));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ArtifactImportResult result = importService.scanAndMerge();
                Bukkit.getScheduler().runTask(plugin, () -> sendSuccess(sender, result));
            } catch (ArtifactCatalogException ex) {
                plugin.getLogger().warning("Artifact scan rejected: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(renderer.render(
                    plugin.runtimeManager().current(),
                    "artifacts.scan.failed",
                    TagResolver.builder()
                        .resolver(Placeholder.unparsed("error", ex.getMessage()))
                        .build()
                )));
            } finally {
                scanRunning.set(false);
            }
        });
        return true;
    }

    private void sendSuccess(CommandSender sender, ArtifactImportResult result) {
        String key = result.catalogChanged() ? "artifacts.scan.success" : "artifacts.scan.unchanged";
        sender.sendMessage(renderer.render(
            plugin.runtimeManager().current(),
            key,
            TagResolver.builder()
                .resolver(Placeholder.unparsed("scanned", Integer.toString(result.scannedJars())))
                .resolver(Placeholder.unparsed("discovered", Integer.toString(result.discoveredArtifacts())))
                .resolver(Placeholder.unparsed("added", Integer.toString(result.addedCatalogEntries())))
                .resolver(Placeholder.unparsed("total", Integer.toString(result.totalCatalogEntries())))
                .build()
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return prefix(List.of("artifacts"), args[0]);
        if (args.length == 2 && "artifacts".equalsIgnoreCase(args[0])) {
            return prefix(List.of("scan"), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> values, String rawPrefix) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
