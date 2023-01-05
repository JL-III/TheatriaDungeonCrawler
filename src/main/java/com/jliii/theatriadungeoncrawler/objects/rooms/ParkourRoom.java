package com.jliii.theatriadungeoncrawler.objects.rooms;

import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ParkourRoom extends Room {

    private boolean blockReached = false;

    public ParkourRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        super(key, region, parentKey, spawnLocations, exitLocation, mobs);
    }

    @Override
    public boolean isObjectiveCompleted() {
        if (getBlockReached()) {
            setCompleted(true);
            return true;
        }
        return false;
    }

    public int runObjective(Player player, Plugin plugin) {
        return new BukkitRunnable(){
            @Override
            public void run(){
                if (player.getLocation().add(0, -1, 0).getBlock().getType() == Material.GOLD_BLOCK) {
                    setBlockReached(true);
                    cancel();
                }
            }

        }.runTaskTimer(plugin, 0, 10).getTaskId();
    }

    public void setBlockReached(Boolean set) {
        this.blockReached = set;
    }

    public boolean getBlockReached() {
        return blockReached;
    }

}
