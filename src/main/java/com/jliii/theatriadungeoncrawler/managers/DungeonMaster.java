package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.enums.LocationTypes;
import com.jliii.theatriadungeoncrawler.objects.BossRoom;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.MiniBossRoom;
import com.jliii.theatriadungeoncrawler.objects.Room;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import com.jliii.theatriadungeoncrawler.util.ListGenerators;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class DungeonMaster {

    private Plugin plugin;
    private FileConfiguration fileConfiguration;
    private List<Dungeon> dungeons = new ArrayList<>();
    private List<Room> rooms = new ArrayList<>();
    private final HashMap<UUID, String> playerGameMap = new HashMap<>();
    private List<String> dungeonKeys;

    public DungeonMaster(Plugin plugin) {
        this.plugin = plugin;
        this.fileConfiguration = plugin.getConfig();
        load();
    }

    public void reload() {
        plugin.reloadConfig();
        rooms = new ArrayList<>();
        updateSignLocations();
        this.fileConfiguration = plugin.getConfig();
        load();
    }

    public void load() {
        this.dungeonKeys = getDungeonKeysFromConfig();
        for (String dungeonKey : dungeonKeys) {
            String worldKey = getWorldKeyFromConfig(dungeonKey);
            List<Location> signLocations = getDungeonCoordinateLocations(worldKey, dungeonKey, "join-sign-locations");
            List<Location> spawnLocations = getDungeonCoordinateLocations(worldKey, dungeonKey, "spawn-locations");
            try {
                for (String roomKey : getDungeonRoomKeysFromConfig(dungeonKey)) {
                    try {
                        Location pos1 = GeneralUtils.parseLocation(plugin.getConfig(), "dungeons." + dungeonKey + ".rooms." + roomKey + ".region." + ".pos1", Bukkit.getWorld(worldKey));
                        Location pos2 = GeneralUtils.parseLocation(plugin.getConfig(), "dungeons." + dungeonKey + ".rooms." + roomKey + ".region." + ".pos2", Bukkit.getWorld(worldKey));
                        List<Location> regionLocations = ListGenerators.getRegionLocations(pos1, pos2);
                        List<Location> mobSpawnLocations = getMobSpawnLocations(worldKey, dungeonKey, roomKey);
                        Location exitLocation = getExitLocation(worldKey, dungeonKey, roomKey);
                        List<EntityType> entityTypes = ListGenerators.getRandomEntityTypes();
                        String roomType = getRoomType(dungeonKey, roomKey);
                        if (roomKey.equalsIgnoreCase("boss")) {
                            rooms.add(new BossRoom(roomKey, roomType,  regionLocations, dungeonKey, mobSpawnLocations, exitLocation, entityTypes));
                        } else if (roomKey.equalsIgnoreCase("mini-boss")){
                            rooms.add(new MiniBossRoom(roomKey, roomType,  regionLocations, dungeonKey, mobSpawnLocations, exitLocation, entityTypes));
                        } else {
                            rooms.add(new Room(roomKey, roomType, regionLocations, dungeonKey, mobSpawnLocations, exitLocation, entityTypes));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "There is an dungeon without rooms, please check the config.");
            }
            dungeons.add(new Dungeon(plugin, worldKey, dungeonKey, rooms, signLocations, spawnLocations, playerGameMap));
        }
        updateSigns();
    }

    public List<String> getDungeonRoomKeysFromConfig(String dungeonKey) {
        ConfigurationSection results = (ConfigurationSection) fileConfiguration.get("dungeons." + dungeonKey + ".rooms");
        List<String> tempList = new ArrayList<>();
        if (results != null) {
            Set<String> childrenKeys = results.getKeys(false);
            tempList.addAll(childrenKeys);
        }
        return tempList;
    }

    public List<String> getDungeonKeysFromConfig() {
        ConfigurationSection results = (ConfigurationSection) fileConfiguration.get("dungeons");
        List<String> tempList = new ArrayList<>();
        if (results != null) {
            Set<String> childrenKeys = results.getKeys(false);
            tempList.addAll(childrenKeys);
        }
        return tempList;
    }

    public List<Location> getMobSpawnLocations(String worldKey, String dungeonKey, String roomKey) {
        ConfigurationSection results = (ConfigurationSection) fileConfiguration.get("dungeons." + dungeonKey + ".rooms." + roomKey + ".room-coords.mob-spawn");
        List<String> tempList = new ArrayList<>();
        if (results != null) {
            Set<String> childrenKeys = results.getKeys(false);
            tempList.addAll(childrenKeys);
        }
        List<Location> locations = new ArrayList<>();
        for (String mobSpawnLocationKey : tempList) {
            locations.add(GeneralUtils.parseLocation(plugin.getConfig(), "dungeons." + dungeonKey + ".rooms." + roomKey + ".room-coords.mob-spawn." + mobSpawnLocationKey, Bukkit.getWorld(worldKey)));
        }
        return locations;
    }

    public List<Location> getDungeonCoordinateLocations(String worldKey, String dungeonKey, String dungeonCoordinateType) {
        ConfigurationSection results = (ConfigurationSection) fileConfiguration.get("dungeons." + dungeonKey + ".dungeon-coords." + dungeonCoordinateType);
        List<String> tempList = new ArrayList<>();
        if (results != null) {
            Set<String> childrenKeys = results.getKeys(false);
            tempList.addAll(childrenKeys);
        }
        List<Location> locations = new ArrayList<>();
        for (String location : tempList) {
            locations.add(GeneralUtils.parseLocation(plugin.getConfig(), "dungeons." + dungeonKey + ".dungeon-coords." + dungeonCoordinateType + "." + location, Bukkit.getWorld(worldKey)));
        }
        return locations;
    }

    private Location getExitLocation(String worldKey, String dungeonKey, String roomKey) {
        return  GeneralUtils.parseLocation(plugin.getConfig(), "dungeons." + dungeonKey + ".rooms." + roomKey + ".room-coords.exit", Bukkit.getWorld(worldKey));
    }

    private String getRoomType(String dungeonKey, String roomKey) {
        return plugin.getConfig().getString("dungeons." + dungeonKey + ".rooms." + roomKey + ".type");
    }

    public String getWorldKeyFromConfig(String dungeonKey) {
        return plugin.getConfig().getString("dungeons." + dungeonKey + ".world");
    }


    public List<String> getDungeonKeys() {
        return dungeonKeys;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public Dungeon getDungeonByKey(String dungeonKey) {
        for (Dungeon dungeon : dungeons) {
            if (dungeon.getKey().equalsIgnoreCase(dungeonKey)) {
                return dungeon;
            }
        }
        return null;
    }

    public List<Dungeon> getDungeons() {
        return dungeons;
    }

    public void updateSignLocations() {
        plugin.getLogger().info("Updating sign locations.");
        for (Dungeon dungeon : getDungeons()) {
            dungeon.setSignLocations(new ArrayList<>());
            try {
                List<Location> newLocations = new ArrayList<>(getDungeonCoordinateLocations(dungeon.getWorldKey(), dungeon.getKey(), "join-sign-locations"));
                if (newLocations.isEmpty()) throw new RuntimeException("The locations list was empty when trying to update locations.");
                dungeon.setSignLocations(newLocations);
            } catch (Exception ex) {
                plugin.getLogger().warning(ex.getMessage());
            }
        }
    }

    public void updateSigns() {
        plugin.getLogger().info("Updating signs.");
        for (Dungeon dungeon : getDungeons()) {
            for (Location location : dungeon.getSignLocations()) {
                if (location == null) continue;
                Sign sign = (Sign) location.getBlock().getState();
                if (!(PlainTextComponentSerializer.plainText().serialize(sign.line(0)).equals("[Dungeons]")
                        && getDungeonKeysFromConfig().contains(PlainTextComponentSerializer.plainText().serialize(sign.line(1))))) continue;
                if (!sign.getLine(1).equalsIgnoreCase(dungeon.getKey())) continue;
                sign.setLine(2, dungeon.getGameState().name());
                sign.setLine(3, "Players: " + dungeon.getAllPlayersInGame().size());
                sign.update(true);
            }
        }
    }

    public void updateSignsAndLocations() {
        updateSignLocations();
        updateSigns();
    }

    public HashMap<UUID, String> getPlayerGameMap() {
        return playerGameMap;
    }

}
