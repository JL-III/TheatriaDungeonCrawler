package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.enums.GameState;
import com.jliii.theatriadungeoncrawler.tasks.GameRun;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class Dungeon {

    private String key;
    private String worldKey;
    private boolean isComplete;
    List<Room> rooms;
    List<MiniBossRoom> miniBossRooms;
    List<BossRoom> bossRooms;
    GameState gameState = GameState.OFF;
    GameRun game;
    private HashMap<UUID,String> playerGameMap;
    private final List<Location> spawnLocations;
    private List<Location> signLocations;


    public Dungeon(Plugin plugin, String worldKey, String key, List<Room> rooms, List<Location> signLocations, List<Location> spawnLocations, HashMap<UUID, String> playerGameMap) {
        this.rooms = rooms;
        this.worldKey = worldKey;
        this.signLocations = signLocations;
        this.spawnLocations = spawnLocations;
        this.playerGameMap = playerGameMap;
        this.key = key;
        this.miniBossRooms = getMiniBossRooms();
        this.bossRooms = getBossRooms();
        game = new GameRun(plugin, this);
    }

    public void setGameState(GameState gameState) {
        if ((this.gameState.order > gameState.order) && gameState != GameState.OFF) return;
        if (this.gameState == gameState) return;
        this.gameState = gameState;
        switch (gameState) {
            case OFF:
                //give access to dungeon master to update signs on state changes.
                reset();
                break;
            case LOBBY:
                break;
            case STARTING:
                break;
            case ACTIVE:
                for (Room room : getRooms()) {
                    room.resetDoors();
                }
                for (UUID uuid : playerGameMap.keySet()) {
                    if (playerGameMap.get(uuid).equalsIgnoreCase(key)) {
                        if (Bukkit.getPlayer(uuid) != null) {
                            Bukkit.getPlayer(uuid).teleport(spawnLocations.get(GeneralUtils.getRandomNumber(0, spawnLocations.size() - 1)));
                        }
                    }
                }
                game.Run();
                break;
            case WON:
                break;
            default:
                break;
        }
    }

    public List<Player> getPlayersFromUUID(List<Map.Entry<UUID, String>> playerGameMap) {
        return playerGameMap.stream().map(x -> Bukkit.getPlayer(x.getKey())).collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, String>> getAllPlayersInGame() {
        return playerGameMap.entrySet().stream().filter(x -> x.getValue().equalsIgnoreCase(key)).collect(Collectors.toList());
    }

    public List<BossRoom> getBossRooms() {
        List<BossRoom> bossRooms = new ArrayList<>();
        for (Room room : rooms) {
            if (room instanceof BossRoom bossRoom) {
                bossRooms.add(bossRoom);
            }
        }
        return bossRooms;
    }

    public List<MiniBossRoom> getMiniBossRooms() {
        List<MiniBossRoom> miniBossRooms = new ArrayList<>();
        for (Room room : rooms) {
            if (room instanceof MiniBossRoom miniBossRoom) {
                miniBossRooms.add(miniBossRoom);
            }
        }
        return miniBossRooms;
    }

    public void reset() {
        for (Room room : getRooms()) {
            room.setCompleted(false);
            room.setHasBeenEntered(false);
            room.setSpawnedMobs(null);
        }
    }

    public String getKey() {
        return key;
    }

    public List<Room> getRooms() {
        return this.rooms;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void addToPlayersInGame(Player playerToAdd) {
        playerGameMap.put(playerToAdd.getUniqueId(), this.key);
    }

    public List<Location> getSpawnLocations() {
        return spawnLocations;
    }

    public List<Location> getSignLocations() {
        return signLocations;
    }

    public void setSignLocations(List<Location> signLocations) {
        this.signLocations = signLocations;
    }

    public String getWorldKey() {
        return worldKey;
    }

    public GameRun getGame() {
        return game;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

}
