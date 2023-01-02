package com.jliii.theatriadungeoncrawler.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class ListGenerators {

    public static List<Location> getRegionLocations(Location location1, Location location2) {
        List<Location> regionLocations = new ArrayList<>();

        int minX = Math.min(location1.getBlockX(), location2.getBlockX());
        int minY = Math.min(location1.getBlockY(), location2.getBlockY());
        int minZ = Math.min(location1.getBlockZ(), location2.getBlockZ());
        int maxX = Math.max(location1.getBlockX(), location2.getBlockX());
        int maxY = Math.max(location1.getBlockY(), location2.getBlockY());
        int maxZ = Math.max(location1.getBlockZ(), location2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location current = new Location(location1.getWorld(), x, y, z);
                    regionLocations.add(current);
                }
            }
        }
        return regionLocations;
    }


    public static List<EntityType> getRandomEntityTypes() {
        List<EntityType> entities = new ArrayList<>();
        List<EntityType> types = new ArrayList<>();
        types.add(EntityType.ZOMBIE);
        types.add(EntityType.SKELETON);
        types.add(EntityType.CAVE_SPIDER);
        for (int i = 0; i < getRandomNumber(1, 10); i++) {
            entities.add(types.get(getRandomNumber(0,2)));
        }
        return entities;
    }

    private static int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

}
