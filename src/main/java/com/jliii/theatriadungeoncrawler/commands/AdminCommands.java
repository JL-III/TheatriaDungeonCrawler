package com.jliii.theatriadungeoncrawler.commands;

import com.jliii.theatriadungeoncrawler.enums.GameState;
import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.BossRoom;
import com.jliii.theatriadungeoncrawler.objects.MiniBossRoom;
import com.jliii.theatriadungeoncrawler.objects.Room;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AdminCommands implements CommandExecutor {

    private Plugin plugin;
    private DungeonMaster dungeonMaster;

    public AdminCommands(Plugin plugin, DungeonMaster dungeonMaster){
        this.plugin = plugin;
        this.dungeonMaster = dungeonMaster;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) return false;
        if (!player.hasPermission("theatria.dungeons.admin")) return true;

        if (args[0].equalsIgnoreCase("start") && player.hasPermission("theatria.dungeons.admin.start") && args.length == 2) {
            if (dungeonMaster.getDungeonKeys().contains(args[1])) {
                if (dungeonMaster.getDungeonByKey(args[1]).getGameState().name().equalsIgnoreCase("off")) {
                    dungeonMaster.getDungeonByKey(args[1]).setGameState(GameState.ACTIVE);
                    dungeonMaster.updateSigns();
                }
            }
        }

        if (args[0].equalsIgnoreCase("stop") && player.hasPermission("theatria.dungeons.admin.start") && args.length == 2) {
            if (dungeonMaster.getDungeonKeys().contains(args[1])) {
                if (dungeonMaster.getDungeonByKey(args[1]).getGameState().name().equalsIgnoreCase("active")) {
                    dungeonMaster.getDungeonByKey(args[1]).setGameState(GameState.OFF);
                    dungeonMaster.updateSigns();
                }
            }
        }

        if (args[0].equalsIgnoreCase("reload") && player.hasPermission("theatria.dungeons.reload")) {
            dungeonMaster.reload();
            player.sendMessage("Reloaded Dungeons");
        }

        if (args[0].equalsIgnoreCase("getrooms")) {
            player.sendMessage(ChatColor.GREEN + "Rooms that currently exist.");
            for (Room room : dungeonMaster.getRooms()) {
                String roomtype;
                if (room instanceof BossRoom) {
                    roomtype = "b";
                } else if (room instanceof MiniBossRoom){
                    roomtype = "m-b";
                } else {
                    roomtype = "room";
                }
                player.sendMessage(ChatColor.YELLOW + "Dungeon: " + ChatColor.GREEN + room.getParentKey() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + roomtype + " " + ChatColor.GREEN + room.getKey() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + " complete: " + ChatColor.GREEN + room.isCompleted() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + " exit: " + ChatColor.GREEN + (room.getExitLocation() != null));
            }
        }

        if (args[0].equalsIgnoreCase("setregion") && args.length == 4) {
            if (!dungeonMaster.getDungeonKeys().contains(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            if (!args[3].equalsIgnoreCase("pos1") && !args[3].equalsIgnoreCase("pos2")) {
                player.sendMessage("You must use the following format: ");
                player.sendMessage("/setregion [dungeon-name] [room-name] pos1");
                player.sendMessage("or");
                player.sendMessage("/setregion [dungeon-name] [room-name] pos2");
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".region." + args[3], player.getLocation());
//            plugin.getConfig().set("dungeons." + args[1] + ".rooms." + args[2] + ".region." + args[3], player.getLocation());
            plugin.saveConfig();
//            dungeonMaster.reload();
        }

        if (args[0].equalsIgnoreCase("setexit") && args.length == 3) {
            if (!dungeonMaster.getDungeonKeys().contains(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.exit", player.getLocation());
            plugin.saveConfig();
//            dungeonMaster.reload();
        }

        if (args[0].equalsIgnoreCase("setmob") && args.length == 3) {
            if (!dungeonMaster.getDungeonKeys().contains(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.mob-spawn." + GeneralUtils.getLocationKey(player.getLocation()) , player.getLocation());
            plugin.saveConfig();
//            dungeonMaster.reload();
        }
        if (args[0].equalsIgnoreCase("setmob") && args.length == 4) {
            if (args[3].equalsIgnoreCase("clear")) {
                plugin.getConfig().set("dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.mob-spawn", new ArrayList<>());
                plugin.saveConfig();
//                dungeonMaster.reload();
                return true;
            }
        }
        if (args[0].equalsIgnoreCase("setspawn") && args.length == 2) {
            if (!dungeonMaster.getDungeonKeys().contains(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            String path = "dungeons." + args[1] + ".dungeon-coords.spawn-locations";
            List<Location> mobspawns = (List<Location>) plugin.getConfig().getList(path);
            if (mobspawns == null) {
                mobspawns = new ArrayList<>();
            }
            mobspawns.add(player.getLocation());
            plugin.getConfig().set(path, mobspawns);
            plugin.saveConfig();
        }

        if (args[0].equalsIgnoreCase("setspawn") && args.length == 3) {
            if (args[2].equalsIgnoreCase("clear")) {
                plugin.getConfig().set("dungeons." + args[1] + ".dungeon-coords.spawn-locations", new ArrayList<>());
                plugin.saveConfig();
//                dungeonMaster.reload();
                return true;
            }
        }


        if (args[0].equalsIgnoreCase("setentrance") && args.length == 3) {
            if (!dungeonMaster.getDungeonKeys().contains(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            plugin.getConfig().set("dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.entrance", player.getLocation());
            plugin.saveConfig();
//          dungeonMaster.reload();
        }

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
        return false;
    }
}
