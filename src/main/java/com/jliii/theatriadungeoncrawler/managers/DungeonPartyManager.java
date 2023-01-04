package com.jliii.theatriadungeoncrawler.managers;

import com.jliii.theatriadungeoncrawler.objects.DungeonPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DungeonPartyManager {

    private List<DungeonPlayer> playersInParty = new ArrayList<>();
    private int numberOfPlayersInParty;
    private HashMap<UUID, String> playerGameMap = new HashMap<>();

    public DungeonPartyManager() {
        numberOfPlayersInParty = 0;
    }

    private String dungeonKey;

    public String getDungeonKey() {
        return dungeonKey;
    }

    public void setDungeonKey(String dungeonKey) {
        this.dungeonKey = dungeonKey;
    }

    public List<DungeonPlayer> getPlayersInParty() {
        return playersInParty;
    }

    public void setPlayersInParty(List<DungeonPlayer> playersInParty) {
        this.playersInParty = playersInParty;
    }

    public int getNumberOfPlayersInParty() {
        return numberOfPlayersInParty;
    }

    public void setNumberOfPlayersInParty(int numberOfPlayersInParty) {
        this.numberOfPlayersInParty = numberOfPlayersInParty;
    }

    public DungeonPlayer getPlayerByUUID(UUID uuid) {
        for (DungeonPlayer dungeonPlayer : playersInParty) {
            if (dungeonPlayer.getUuid() == uuid) {
                return dungeonPlayer;
            }
        }
        return null;
    }

    public void add(Player player) {
        playersInParty.add(new DungeonPlayer(player, dungeonKey, 3));
    }

    public void remove(UUID uuid) {
        playersInParty.remove(getPlayerByUUID(uuid));
    }

    public HashMap<UUID, String> getPlayerGameMap() {
        return playerGameMap;
    }

    public void setPlayerGameMap(HashMap<UUID, String> playerGameMap) {
        this.playerGameMap = playerGameMap;
    }

}
