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

public class SurviveRoom extends Room{

    private boolean isTimerComplete = false;
    private int timer = 60;

    public SurviveRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        super(key, region, parentKey, spawnLocations, exitLocation, mobs);
    }

    @Override
    public boolean isObjectiveCompleted() {
        if (getIsTimerComplete()) {
            setCompleted(true);
            for (Entity entity : getSpawnedMobs()) {
                entity.remove();
            }
            return true;
        }
        return false;
    }

    @Override
    public int runObjective(Player player, Plugin plugin) {
        List<Entity> roomSpawnedMobs = new ArrayList<>();
        return new BukkitRunnable(){
            @Override
            public void run(){
                player.sendActionBar("Survive: " + timer);
                timer --;
                if (timer < 2) {
                    setTimerComplete(true);
                    cancel();
                } else if (timer % 10 == 0) {
                    for (EntityType entityType : getMobs()) {
                        roomSpawnedMobs.add(player.getWorld().spawnEntity(getSpawnLocations().get(GeneralUtils.getRandomNumber(0, getSpawnLocations().size() - 1)), entityType));
                    }
                    setSpawnedMobs(roomSpawnedMobs);
                }
            }
        }.runTaskTimer(plugin, 0, 20).getTaskId();

    }

    @Override
    public void reset() {
        resetDoors();
        setCompleted(false);
        setHasBeenEntered(false);
        setCompleted(false);
        setHasRunCompletedSequence(false);
        setTimerComplete(false);
        setTimer(60);
    }

    private void setTimerComplete(boolean value) { isTimerComplete = value; }

    private boolean getIsTimerComplete() { return isTimerComplete; }

    private void setTimer(int value) { timer = value; }

}
