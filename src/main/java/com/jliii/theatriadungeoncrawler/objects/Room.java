package com.jliii.theatriadungeoncrawler.objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;

public class Room {

    private final String key;
    private final String parentKey;
    private final String type;
    private final List<Location> region;
    private boolean hasBeenEntered = false;
    private boolean isCompleted = false;
    private boolean hasRunCompletedSequence = false;
    private final List<EntityType> mobs;
    private final List<Location> spawnLocations;
    private List<Entity> spawnedMobs;
    private final Location exitLocation;

    public Room(String key, String type, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        this.key = key;
        this.type = type;
        this.region = region;
        this.parentKey = parentKey;
        this.spawnLocations = spawnLocations;
        this.mobs = mobs;
        this.exitLocation = exitLocation;
    }

    public void openDoors() {
        getExitLocation().getBlock().setType(Material.AIR);
        Location locationAbove = new Location(getExitLocation().getWorld(), getExitLocation().getBlockX(), getExitLocation().getBlockY() + 1, getExitLocation().getBlockZ());
        locationAbove.getBlock().setType(Material.AIR);
    }

    public void resetDoors() {
        getExitLocation().getBlock().setType(Material.IRON_BARS);
        Location locationAbove = new Location(getExitLocation().getWorld(), getExitLocation().getBlockX(), getExitLocation().getBlockY() + 1, getExitLocation().getBlockZ());
        locationAbove.getBlock().setType(Material.IRON_BARS);
    }

    public String getKey() {
        return key;
    }

    public List<Location> getRegion() { return this.region; }

    public String getParentKey() {
        return parentKey;
    }

    public void setHasBeenEntered(Boolean set) { this.hasBeenEntered = set; }

    public boolean isHasBeenEntered() { return hasBeenEntered; }

    public List<EntityType> getMobs() {
        return mobs;
    }

    public List<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }

    public void setSpawnedMobs(List<Entity> spawnedMobs) {
        this.spawnedMobs = spawnedMobs;
    }

    public List<Location> getSpawnLocations() {
        return spawnLocations;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public Location getExitLocation() {
        return exitLocation;
    }

    public String getType() { return type; }

    public boolean hasRunCompletedSequence() {
        return hasRunCompletedSequence;
    }

    public void setHasRunCompletedSequence(boolean hasRunCompletedSequence) {
        this.hasRunCompletedSequence = hasRunCompletedSequence;
    }

}
