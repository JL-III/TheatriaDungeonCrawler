package com.jliii.theatriadungeoncrawler.objects.rooms;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public interface RoomInterface {

    void resetDoors();

    void openDoors();

    String getKey();

    List<Location> getRegion();

    void setHasBeenEntered(Boolean value);

    boolean hasBeenEntered();

    boolean isObjectiveCompleted();

    int runObjective(Player player, Plugin plugin);

    void reset();

}
