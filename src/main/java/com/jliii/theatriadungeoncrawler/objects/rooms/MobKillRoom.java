package com.jliii.theatriadungeoncrawler.objects.rooms;

import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class MobKillRoom extends Room {

    private boolean allMobsKilled = false;

    public MobKillRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        super(key, region, parentKey, spawnLocations, exitLocation, mobs);
    }

    @Override
    public boolean isObjectiveCompleted() {
        if (getAllMobsKilled()) {
            setCompleted(true);
            return true;
        }
        return false;
    }

    @Override
    public int runObjective(Player player, Plugin plugin) {
        List<Entity> spawnedMobs = new ArrayList<>();
        for (EntityType entityType : getMobs()) {
            spawnedMobs.add(player.getWorld().spawnEntity(getSpawnLocations().get(GeneralUtils.getRandomNumber(0, getSpawnLocations().size() - 1)), entityType));
        }
        setSpawnedMobs(spawnedMobs);

        return new BukkitRunnable(){
            @Override
            public void run(){
                if (spawnedMobs.size() < 1) {
                    setAllMobsKilled(true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 10).getTaskId();

    }

    @Override
    public void reset() {
        resetDoors();
        setCompleted(false);
        setHasBeenEntered(false);
        setSpawnedMobs(null);
        setCompleted(false);
        setHasRunCompletedSequence(false);
        setAllMobsKilled(false);
    }

    private void setAllMobsKilled(Boolean value) { this.allMobsKilled = value; }

    private boolean getAllMobsKilled() { return allMobsKilled; }

}
