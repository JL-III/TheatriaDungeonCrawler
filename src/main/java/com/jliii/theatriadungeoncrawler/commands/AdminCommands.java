package com.jliii.theatriadungeoncrawler.commands;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.enums.ObjectiveTypes;
import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.rooms.BossRoom;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.rooms.MiniBossRoom;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
    private DungeonMaster dungeonMaster;

    public AdminCommands(Plugin plugin, DungeonMaster dungeonMaster){
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dungeonMaster = dungeonMaster;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) return false;
        if (!player.hasPermission("theatria.dungeons.admin")) return true;
        if (args.length < 1) return false;

        if (args[0].equalsIgnoreCase("start") && player.hasPermission("theatria.dungeons.admin.start") && args.length == 2) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            } else {
                if (dungeonMaster.getDungeonByKey(args[1]).getGameState().name().equalsIgnoreCase("off")) {
                    dungeonMaster.getDungeonByKey(args[1]).setGameState(State.ACTIVE);
                    dungeonMaster.updateSigns();
                    logger.info("Dungeon [" + args[1] + "] is starting.");
                    player.sendMessage("Dungeon " + args[1] + " is starting!");
                    return true;
                } else {
                    player.sendMessage("The current state of the dungeon is " + dungeonMaster.getDungeonByKey(args[1]).getGameState().name());
                }
            }

        }

        if (args[0].equalsIgnoreCase("stop") && player.hasPermission("theatria.dungeons.admin.start") && args.length == 2) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
            } else {
                if (dungeonMaster.getDungeonByKey(args[1]).getGameState().name().equalsIgnoreCase("active")) {
                    dungeonMaster.getDungeonByKey(args[1]).setGameState(State.OFF);
                    dungeonMaster.updateSigns();
                    logger.info("Dungeon [" + args[1] + "] is stopping.");
                    player.sendMessage("Dungeon " + args[1] + " is stopping!");
                } else {
                    player.sendMessage("The current state of the dungeon is " + dungeonMaster.getDungeonByKey(args[1]).getGameState().name());
                }
            }
        }

        if (args[0].equalsIgnoreCase("leave") && player.hasPermission("theatria.dungeons.leave")) {
            String dungeonKey = dungeonMaster.getDungeonPartyManager().getPlayerGameMap().get(player.getUniqueId());
            if (dungeonKey == null) {
                player.sendMessage("You are not in a dungeon.");
                return true;
            }
            dungeonMaster.getDungeonPartyManager().getPlayerGameMap().remove(player.getUniqueId());
            player.sendMessage("You have left the dungeon.");
            if (dungeonMaster.getDungeonByKey(dungeonKey).getSignLocations().size() > 0) {
                player.teleport(dungeonMaster.getDungeonByKey(dungeonKey).getSignLocations().get(0));
            } else {
                player.teleport(new Location(Bukkit.getWorld("world"), 61, 72, -2));
                logger.warning("There was no sign location to teleport the player back to. Please fix this by adding a join-sign-location to the config.");
            }
            dungeonMaster.updateSigns();
        }

        if (args[0].equalsIgnoreCase("reload") && player.hasPermission("theatria.dungeons.admin.reload")) {
            dungeonMaster.reload();
            player.sendMessage("Reloaded Dungeons");
        }

        if (args[0].equalsIgnoreCase("debug")) {
            player.sendMessage(ChatColor.GREEN + "----------Debug info----------");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Dungeons: ");
            for (Dungeon dungeon : dungeonMaster.getDungeons()) {
                player.sendMessage("name: " + dungeon.getKey() + " state: " + dungeon.getGameState() + " #rooms: " + dungeon.getRooms().size());
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Rooms info: ");
            for (Room room : dungeonMaster.getRooms()) {
                String roomInstanceType;
                if (room instanceof BossRoom) {
                    roomInstanceType = "b";
                } else if (room instanceof MiniBossRoom){
                    roomInstanceType = "m-b";
                } else {
                    roomInstanceType = "room";
                }
                player.sendMessage(ChatColor.YELLOW + "Dungeon: " + ChatColor.GREEN + room.getParentKey() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + roomInstanceType + " " + ChatColor.GREEN + room.getKey() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + " complete: " + ChatColor.GREEN + room.isCompleted() + ChatColor.DARK_PURPLE
                        + " || " + ChatColor.YELLOW + " exit: " + ChatColor.GREEN + (room.getExitLocation() != null));
            }
        }

        if (args[0].equalsIgnoreCase("setregion") && args.length == 4) {
            if (isDungeonKeyInvalid(args[1])) {
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
            plugin.saveConfig();
            player.sendMessage("Successfully set " + args[3] + " for " + args[2] + " in " + args[1]);

        }

        if (args[0].equalsIgnoreCase("setexit") && args.length == 3) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.exit", player.getLocation());
            plugin.saveConfig();
            player.sendMessage("Successfully set an exit.");
        }

        if (args[0].equalsIgnoreCase("setentrance") && args.length == 3) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.entrance", player.getLocation());
            plugin.saveConfig();
            player.sendMessage("Successfully set an entrance.");
        }

        if (args[0].equalsIgnoreCase("setmob") && args.length == 3) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".rooms." + args[2] + ".room-coords.mob-spawn." + GeneralUtils.getLocationKey(player.getLocation()) , player.getLocation());
            plugin.saveConfig();
            player.sendMessage("Successfully set a mob spawn point.");
        }

        if (args[0].equalsIgnoreCase("settype") && args.length == 4) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            boolean foundValue = false;
            for (ObjectiveTypes objectiveTypes : ObjectiveTypes.values()) {
                if (args[3].equalsIgnoreCase(objectiveTypes.name())) {
                    foundValue = true;
                    break;
                }
            }
            if (foundValue) {
                plugin.getConfig().set("dungeons." + args[1] + ".rooms." + args[2] + ".type" , args[3]);
                plugin.saveConfig();
                player.sendMessage("Successfully set room type to " + args[3] + " for room " + args[2]);
            } else {
                player.sendMessage(ChatColor.RED + "That is not a valid room type.");
                StringBuilder sb = new StringBuilder();
                for (ObjectiveTypes objectiveTypes : ObjectiveTypes.values()) {
                    sb.append(objectiveTypes.name().toLowerCase() + " ");
                }
                player.sendMessage(ChatColor.YELLOW + "List of possible room types: " + sb);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("setspawn") && args.length == 2) {
            if (isDungeonKeyInvalid(args[1])) {
                player.sendMessage("There is no dungeon with that name.");
                return true;
            }
            GeneralUtils.setLocation(plugin.getConfig(), "dungeons." + args[1] + ".dungeon-coords." + ".spawn-locations." + GeneralUtils.getLocationKey(player.getLocation()), player.getLocation());
            plugin.saveConfig();
            player.sendMessage("Successfully set a spawn point.");
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

    private boolean isDungeonKeyInvalid(String key) {
        return !dungeonMaster.getDungeonKeys().contains(key);
    }
}
