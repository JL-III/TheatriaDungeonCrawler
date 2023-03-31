package com.jliii.theatriadungeoncrawler.objects.rooms;

import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class BossRoom extends Room {

    private boolean allMobsKilled = false;

    public BossRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
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
        MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob("SkeletalKnight").orElse(null);
        if (mob != null) {
            // spawns mob
            ActiveMob knight = mob.spawn(BukkitAdapter.adapt(getSpawnLocations().get(0)),1);
            // get mob as bukkit entity
            spawnedMobs.add(knight.getEntity().getBukkitEntity());
            setSpawnedMobs(spawnedMobs);
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
