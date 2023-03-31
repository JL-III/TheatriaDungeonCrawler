package com.jliii.theatriadungeoncrawler.objects.rooms;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Room2 {

    private final Location cornerA;
    private final Location cornerB;
    private Location entryPoint;
    private Location corridorConnectionPoint;

    public Room2(Location cornerA, Location cornerB) {
        this.cornerA = cornerA;
        this.cornerB = cornerB;
        generateEntryAndCorridorPoints();
    }

    private void generateEntryAndCorridorPoints() {
        Random random = new Random();

        int x1 = Math.min(cornerA.getBlockX(), cornerB.getBlockX());
        int x2 = Math.max(cornerA.getBlockX(), cornerB.getBlockX());
        int y1 = Math.min(cornerA.getBlockY(), cornerB.getBlockY());
        int y2 = Math.max(cornerA.getBlockY(), cornerB.getBlockY());
        int z1 = Math.min(cornerA.getBlockZ(), cornerB.getBlockZ());
        int z2 = Math.max(cornerA.getBlockZ(), cornerB.getBlockZ());

        World world = cornerA.getWorld();

        List<Location> wallCenters = new ArrayList<>();
        wallCenters.add(new Location(world, x1 + (x2 - x1) / 2, y1, z1)); // Front wall
        wallCenters.add(new Location(world, x1 + (x2 - x1) / 2, y1, z2)); // Back wall
        wallCenters.add(new Location(world, x1, y1, z1 + (z2 - z1) / 2)); // Left wall
        wallCenters.add(new Location(world, x2, y1, z1 + (z2 - z1) / 2)); // Right wall

        // Randomly choose the entry point and remove it from the list
        int entryIndex = random.nextInt(wallCenters.size());
        entryPoint = wallCenters.get(entryIndex);
        wallCenters.remove(entryIndex);

        // Randomly choose the corridor connection point from the remaining walls
        int corridorIndex = random.nextInt(wallCenters.size());
        corridorConnectionPoint = wallCenters.get(corridorIndex);
    }

    public Location getEntryPoint() {
        return entryPoint;
    }

    public Location getCorridorConnectionPoint() {
        return corridorConnectionPoint;
    }

}
