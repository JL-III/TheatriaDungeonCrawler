package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.enums.PlayerState;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DungeonPlayer {

    private String name;
    private UUID uuid;
    private PlayerState playerState;
    private String dungeonKey;
    private int lives;
    private boolean isOut = false;

    public DungeonPlayer (Player player, String dungeonKey, int lives) {
        this.name = player.getName();
        this.uuid = player.getUniqueId();
        this.playerState = PlayerState.ALIVE;
        this.dungeonKey = dungeonKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getDungeonKey() {
        return dungeonKey;
    }

    public void setDungeonKey(String dungeonKey) {
        this.dungeonKey = dungeonKey;
    }

    public int getLives() {
        return lives;
    }

    public int setLives(int lives) {
        return this.lives = lives;
    }

    public boolean isOut() {
        return isOut;
    }

    public void setOut(boolean out) {
        isOut = out;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public void setPlayerState(PlayerState playerState) {
        this.playerState = playerState;
    }

}
