package dev.victormenacho.brandblocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Objects;

public class BrandBlocker extends JavaPlugin implements Listener {

    public String prefix;
    public final String version = Bukkit.getBukkitVersion().split("-")[0];

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix"));
        getLogger().info("Server running version 1." + version);

        Bukkit.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();

        getLogger().info(ChatColor.GOLD + "Player '" + p.getName() + "' joined.");

        if (getConfig().getBoolean("geyser-support") && p.getName().contains(Objects.requireNonNull(getConfig().getString("geyser-prefix")))) {
            getLogger().info(ChatColor.GOLD + "Player '" + p.getName() + "' matches Geyser prefix. Skipping checks.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                final String brand = p.getClientBrandName();

                if (brand == null || brand.isEmpty()) {
                    getLogger().warning(ChatColor.RED + "No brand detected for player '" + p.getName() + "'.");
                    return;
                }

                getLogger().info(ChatColor.GOLD + "Player '" + p.getName() + "' is using brand '" + brand + "'.");

                final Iterator<String> iterator = getConfig().getStringList("blocked-brands").iterator();

                switch (getConfig().getString("mode")) {
                    case "blacklist":
                        boolean blacklisted = false;
                        while (iterator.hasNext()) {
                            String str = iterator.next();
                            if (brand.contains(str)) {
                                blacklisted = true;
                                break;
                            }
                        }

                        if (blacklisted) {
                            if (p.hasPermission("brandblocker.bypass")) {
                                getLogger().info(ChatColor.GOLD + "Player '" + p.getName() + "' has bypass permission. Skipping kick.");
                                return;
                            }

                            String kickCmd = getConfig().getString("kick-command");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), kickCmd.replace("%player%", p.getName()).replace("%brand%", brand));
                            getLogger().info(ChatColor.GOLD + getConfig().getString("console-log").replace("%player%", p.getName()).replace("%brand%", brand));
                        }
                        return;

                    case "whitelist":
                        boolean whitelisted = false;
                        while (iterator.hasNext()) {
                            String str = iterator.next();
                            if (brand.contains(str)) {
                                whitelisted = true;
                                break;
                            }
                        }

                        if (!whitelisted) {
                            if (p.hasPermission("brandblocker.bypass")) {
                                getLogger().info("Player '" + p.getName() + "' has bypass permission. Skipping kick.");
                                return;
                            }

                            String kickCmd = getConfig().getString("kick-command");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), kickCmd.replace("%player%", p.getName()).replace("%brand%", brand));
                            getLogger().info(getConfig().getString("console-log").replace("%player%", p.getName()).replace("%brand%", brand));
                        }
                        return;
                }
            }
        }.runTaskLater(this, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("brandblocker")) {
            if (args.length == 0) {
                sender.sendMessage("§4§m--------------------------");
                sender.sendMessage("§c§lBrandBlocker §7v" + this.getDescription().getVersion());
                sender.sendMessage("§7by Menacho");
                sender.sendMessage("§7");
                sender.sendMessage("§cUsage §4»");
                sender.sendMessage("§c§l● check §7(player)");
                sender.sendMessage("§c§l● reload");
                sender.sendMessage("§4§m--------------------------");
            } else {
                if (args[0].equalsIgnoreCase("check")) {
                    if (sender.hasPermission("brandblocker.usage")) {
                        if (args.length <= 1) {
                            sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("specify-player-name")));
                        } else {
                            Player target = Bukkit.getPlayerExact(args[1]);
                            if (target != null && target.isOnline()) {
                                String brand = target.getClientBrandName();
                                String msg = ChatColor.translateAlternateColorCodes('&', getConfig().getString("check-succesful"))
                                    .replace("%player%", args[1])
                                    .replace("%brand%", brand != null ? brand : "unknown");
                                sender.sendMessage(prefix + msg);
                            } else {
                                sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("check-failed")).replace("%player%", args[1]));
                            }
                        }
                    } else {
                        sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("no-permission")));
                    }
                } else if (args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("brandblocker.usage")) {
                        reloadConfig();
                        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix"));
                        sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("config-reload")));
                    } else {
                        sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("no-permission")));
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

}
