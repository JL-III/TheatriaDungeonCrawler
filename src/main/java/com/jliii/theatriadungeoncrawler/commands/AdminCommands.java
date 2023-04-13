package com.jliii.theatriadungeoncrawler.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.logging.Logger;

public class AdminCommands implements CommandExecutor {

    private Plugin plugin;
    private final Logger logger;

    public AdminCommands(Plugin plugin){
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) return false;
        if (!player.hasPermission("theatria.dungeons.admin")) return true;
        if (args.length < 1) return false;


        if (args[0].equalsIgnoreCase("getconfig") && args.length == 2) {
            Object object = plugin.getConfig().get(args[1]);
            if (object != null) {
                player.sendMessage(object.toString());
                ConfigurationSection result = (ConfigurationSection) object;
                Set<String> childrenKeys = result.getKeys(true);
                player.sendMessage("ResultCurrentPath: " + result.getCurrentPath());
                for (String string : childrenKeys) {
                    player.sendMessage("Childkey: " + string);
                }
            } else {
                player.sendMessage("Could not find that object.");
            }
        }

        if (args[0].equalsIgnoreCase("tasks")) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "----------tasks----------");
            for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
                if (task.getOwner().getName().equalsIgnoreCase("theatriadungeoncrawler")) {
                    player.sendMessage(task.getOwner().getName() + task.getTaskId());
                }
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "----------tasks----------");
            return true;
        }
        return true;
    }
}
