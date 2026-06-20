package com.jliii.theatriadungeoncrawler.commands;

import com.jliii.theatriadungeoncrawler.TheatriaDungeonCrawler;
import com.jliii.theatriadungeoncrawler.managers.DungeonManager;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the {@code /dungeons} command. Player-facing subcommands
 * ({@code start}, {@code leave}) drive the {@link DungeonManager}; anything
 * else is delegated to {@link AdminCommands} (e.g. {@code getconfig}, {@code tasks}).
 */
public class DungeonCommands implements CommandExecutor {

    private final DungeonManager dungeonManager;
    private final AdminCommands adminCommands;

    public DungeonCommands(TheatriaDungeonCrawler plugin, DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
        this.adminCommands = new AdminCommands(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /dungeons <start|leave> [segment-length] [theme]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender, args);
            case "leave":
                return handleLeave(sender);
            default:
                // Admin/utility subcommands (getconfig, tasks, ...).
                return adminCommands.onCommand(sender, command, label, args);
        }
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can start a dungeon.");
            return true;
        }

        int segmentLength = 0; // 0 => random 7-15 rooms per segment
        if (args.length >= 2) {
            try {
                segmentLength = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("Segment length must be a number.");
                return true;
            }
            if (segmentLength < 1) {
                player.sendMessage("Segment length must be at least 1.");
                return true;
            }
        }

        DungeonTemplate.DungeonType theme = null; // null => random theme per room
        if (args.length >= 3 && !args[2].equalsIgnoreCase("random")) {
            try {
                theme = DungeonTemplate.DungeonType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid theme. Make sure it is a valid theme type.");
                return true;
            }
        }

        dungeonManager.startDungeon(player, segmentLength, theme);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can leave a dungeon.");
            return true;
        }
        dungeonManager.leaveDungeon(player);
        return true;
    }
}
