package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import com.jliii.theatriadungeoncrawler.util.runnables.DistributedWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadRunnable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a procedural dungeon layout into a void world.
 *
 * <p>Rooms are laid out on a fixed grid. Starting from a single room the
 * generator grows a randomly-shaped, fully connected tree of rooms: each new
 * room is attached to an existing one via a corridor, so every room is
 * reachable and no two rooms overlap.</p>
 *
 * <p>All block placement is queued onto the shared {@link WorkloadRunnable},
 * which spreads the work across ticks to avoid stalling the server. Work is
 * enqueued in three passes — rooms, then corridors, then doorways — so that
 * the doorway carves (which overwrite wall blocks with air) always run after
 * the walls they punch through have been placed.</p>
 */
public class DungeonLayoutGenerator {

    /** Footprint (x and z) of a room, walls included, in blocks. */
    private static final int ROOM_FOOTPRINT = 9;
    /** Interior height of a room, walls included, in blocks. */
    private static final int ROOM_HEIGHT = 6;
    /** Length of the corridor gap between two adjacent rooms, in blocks. */
    private static final int CORRIDOR_GAP = 5;
    /** Width of a corridor (and of the doorways it connects to), in blocks. */
    private static final int CORRIDOR_WIDTH = 3;
    /** Height of a corridor tube, in blocks. */
    private static final int CORRIDOR_HEIGHT = 5;
    /** Centre-to-centre distance between adjacent grid cells, in blocks. */
    private static final int CELL_PITCH = ROOM_FOOTPRINT + CORRIDOR_GAP;

    private static final Material CORRIDOR_MATERIAL = Material.STONE_BRICKS;

    private final World world;
    private final WorkloadRunnable workloadRunnable;
    private final DistributedWorkload workload;

    public DungeonLayoutGenerator(World world, WorkloadRunnable workloadRunnable) {
        this.world = world;
        this.workloadRunnable = workloadRunnable;
        this.workload = new DistributedWorkload(workloadRunnable);
    }

    /**
     * Generates a connected dungeon of {@code roomCount} rooms anchored at
     * {@code origin} (the lowest corner of the starting room).
     *
     * @param origin    lowest (min-x, min-y, min-z) corner of the start room
     * @param roomCount desired number of rooms (at least one)
     * @param theme     the theme to build every room with, or {@code null} to
     *                  pick a fresh random theme per room
     * @param random    randomness source driving layout and themes
     * @return a safe spawn location standing on the floor of the start room
     */
    public Location generate(Location origin, int roomCount, DungeonTemplate.DungeonType theme, Random random) {
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();

        Map<Coord, DungeonTemplate.DungeonType> rooms = new HashMap<>();
        List<Coord[]> corridors = new ArrayList<>();
        growLayout(Math.max(1, roomCount), rooms, corridors, theme, random);

        // Pass 1: rooms.
        for (Map.Entry<Coord, DungeonTemplate.DungeonType> entry : rooms.entrySet()) {
            buildRoom(entry.getKey(), originX, originY, originZ, entry.getValue());
        }
        // Pass 2: corridors connecting adjacent rooms.
        for (Coord[] edge : corridors) {
            buildCorridor(edge[0], edge[1], originX, originY, originZ);
        }
        // Pass 3: doorways carved through the shared walls (air overwrites walls).
        for (Coord[] edge : corridors) {
            carveDoorways(edge[0], edge[1], originX, originY, originZ);
        }

        // Spawn standing on the floor in the centre of the start room.
        double spawnX = originX + ROOM_FOOTPRINT / 2.0;
        double spawnZ = originZ + ROOM_FOOTPRINT / 2.0;
        return new Location(world, spawnX, originY + 1, spawnZ);
    }

    /**
     * Grows a connected tree of grid cells via randomized accretion: repeatedly
     * pick an existing cell and try to attach an un-placed orthogonal neighbour.
     */
    private void growLayout(int roomCount,
                            Map<Coord, DungeonTemplate.DungeonType> rooms,
                            List<Coord[]> corridors,
                            DungeonTemplate.DungeonType theme,
                            Random random) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        Coord start = new Coord(0, 0);
        Set<Coord> placed = new HashSet<>();
        List<Coord> frontier = new ArrayList<>();
        placed.add(start);
        frontier.add(start);
        rooms.put(start, themeFor(theme, random));

        int maxAttempts = roomCount * 50;
        int attempts = 0;
        while (placed.size() < roomCount && attempts < maxAttempts) {
            attempts++;
            Coord from = frontier.get(random.nextInt(frontier.size()));
            int[] dir = directions[random.nextInt(directions.length)];
            Coord to = new Coord(from.getX() + dir[0], from.getZ() + dir[1]);
            if (placed.contains(to)) {
                continue;
            }
            placed.add(to);
            frontier.add(to);
            rooms.put(to, themeFor(theme, random));
            corridors.add(new Coord[]{from, to});
        }
    }

    private DungeonTemplate.DungeonType themeFor(DungeonTemplate.DungeonType theme, Random random) {
        return theme != null ? theme : DungeonTemplate.getRandomTheme();
    }

    private void buildRoom(Coord cell, int originX, int originY, int originZ, DungeonTemplate.DungeonType theme) {
        int minX = originX + cell.getX() * CELL_PITCH;
        int minZ = originZ + cell.getZ() * CELL_PITCH;
        Location cornerA = new Location(world, minX, originY, minZ);
        Location cornerB = new Location(world,
                minX + ROOM_FOOTPRINT - 1,
                originY + ROOM_HEIGHT - 1,
                minZ + ROOM_FOOTPRINT - 1);
        workload.createRoom(cornerA, cornerB, theme);
    }

    /**
     * Builds a hollow corridor tube spanning the gap between two adjacent rooms,
     * butting up against the wall of each.
     */
    private void buildCorridor(Coord a, Coord b, int originX, int originY, int originZ) {
        int aMinX = originX + a.getX() * CELL_PITCH;
        int aMinZ = originZ + a.getZ() * CELL_PITCH;
        int bMinX = originX + b.getX() * CELL_PITCH;
        int bMinZ = originZ + b.getZ() * CELL_PITCH;
        int half = CORRIDOR_WIDTH / 2;

        Location start;
        Location end;
        if (a.getZ() == b.getZ()) {
            // Neighbours along the x axis.
            int lowMinX = Math.min(aMinX, bMinX);
            int highMinX = Math.max(aMinX, bMinX);
            int zCentre = aMinZ + ROOM_FOOTPRINT / 2;
            start = new Location(world, lowMinX + ROOM_FOOTPRINT - 1, originY, zCentre - half);
            end = new Location(world, highMinX, originY + CORRIDOR_HEIGHT - 1, zCentre + half);
        } else {
            // Neighbours along the z axis.
            int lowMinZ = Math.min(aMinZ, bMinZ);
            int highMinZ = Math.max(aMinZ, bMinZ);
            int xCentre = aMinX + ROOM_FOOTPRINT / 2;
            start = new Location(world, xCentre - half, originY, lowMinZ + ROOM_FOOTPRINT - 1);
            end = new Location(world, xCentre + half, originY + CORRIDOR_HEIGHT - 1, highMinZ);
        }
        workload.fillHollowCorridor(start, end, CORRIDOR_MATERIAL);
    }

    /**
     * Carves an air doorway through each room wall (and the corridor end caps)
     * where the corridor meets it, leaving the floor intact.
     */
    private void carveDoorways(Coord a, Coord b, int originX, int originY, int originZ) {
        int aMinX = originX + a.getX() * CELL_PITCH;
        int aMinZ = originZ + a.getZ() * CELL_PITCH;
        int bMinX = originX + b.getX() * CELL_PITCH;
        int bMinZ = originZ + b.getZ() * CELL_PITCH;
        int half = CORRIDOR_WIDTH / 2;
        int doorTop = originY + CORRIDOR_HEIGHT - 2; // leave the corridor ceiling in place

        if (a.getZ() == b.getZ()) {
            int lowMinX = Math.min(aMinX, bMinX);
            int highMinX = Math.max(aMinX, bMinX);
            int zCentre = aMinZ + ROOM_FOOTPRINT / 2;
            int wallA = lowMinX + ROOM_FOOTPRINT - 1; // +x wall of the lower room
            int wallB = highMinX;                     // -x wall of the higher room
            carveAir(wallA, originY + 1, zCentre - half, wallA, doorTop, zCentre + half);
            carveAir(wallB, originY + 1, zCentre - half, wallB, doorTop, zCentre + half);
        } else {
            int lowMinZ = Math.min(aMinZ, bMinZ);
            int highMinZ = Math.max(aMinZ, bMinZ);
            int xCentre = aMinX + ROOM_FOOTPRINT / 2;
            int wallA = lowMinZ + ROOM_FOOTPRINT - 1; // +z wall of the lower room
            int wallB = highMinZ;                     // -z wall of the higher room
            carveAir(xCentre - half, originY + 1, wallA, xCentre + half, doorTop, wallA);
            carveAir(xCentre - half, originY + 1, wallB, xCentre + half, doorTop, wallB);
        }
    }

    private void carveAir(int x1, int y1, int z1, int x2, int y2, int z2) {
        workload.fillSolidBox(
                new Location(world, x1, y1, z1),
                new Location(world, x2, y2, z2),
                Material.AIR);
    }

    public WorkloadRunnable getWorkloadRunnable() {
        return workloadRunnable;
    }
}
