package com.jliii.theatriadungeoncrawler.util.runnables;

import com.google.common.base.Preconditions;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import lombok.AllArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

@AllArgsConstructor
public class DistributedWorkload {

    private final WorkloadRunnable workloadRunnable;
//    private Player player;

    public void createRoom(Location cornerA, Location cornerB, DungeonTemplate.DungeonType dungeonType) {
        Preconditions.checkArgument(cornerA.getWorld() == cornerB.getWorld() && cornerA.getWorld() != null);
        BoundingBox box = BoundingBox.of(cornerA.getBlock(), cornerB.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();

        World world = cornerA.getWorld();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    boolean isEdge =
                            x == min.getBlockX() || x == max.getBlockX()
                                    || y == min.getBlockY() || y == max.getBlockY()
                                    || z == min.getBlockZ() || z == max.getBlockZ();

                    if (isEdge) {
                        Material material = DungeonTemplate.getRandomMaterial(dungeonType);
                        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), x, y, z, material);
                        this.workloadRunnable.addWorkload(blockPlacementWorkload);
                    }
                }
            }
        }
    }


    public void fillHollowCorridor(Location start, Location end, Material material) {
        Preconditions.checkArgument(start.getWorld() == end.getWorld() && start.getWorld() != null);
        BoundingBox box = BoundingBox.of(start.getBlock(), end.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();

        World world = start.getWorld();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    boolean isEdge =
                            x == min.getBlockX() || x == max.getBlockX()
                                    || y == min.getBlockY() || y == max.getBlockY()
                                    || z == min.getBlockZ() || z == max.getBlockZ();

                    boolean isOpenEnd = (x == min.getBlockX() && x == max.getBlockX())
                            || (z == min.getBlockZ() && z == max.getBlockZ());

                    if (isEdge && !isOpenEnd) {
                        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), x, y, z, material);
                        this.workloadRunnable.addWorkload(blockPlacementWorkload);
                    }
                }
            }
        }
    }

    public void fillSolidBox(Location cornerA, Location cornerB, Material material) {
        Preconditions.checkArgument(cornerA.getWorld() == cornerB.getWorld() && cornerA.getWorld() != null);
        BoundingBox box = BoundingBox.of(cornerA.getBlock(), cornerB.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();

        World world = cornerA.getWorld();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), x, y, z, material);
                    this.workloadRunnable.addWorkload(blockPlacementWorkload);
                }
            }
        }
    }

    public void fillBox(Location cornerA, Location cornerB, DungeonTemplate.DungeonType dungeonType, boolean isSolid) {
        Preconditions.checkArgument(cornerA.getWorld() == cornerB.getWorld() && cornerA.getWorld() != null);
        BoundingBox box = BoundingBox.of(cornerA.getBlock(), cornerB.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();

        World world = cornerA.getWorld();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    boolean isEdge =
                            x == min.getBlockX() || x == max.getBlockX()
                                    || y == min.getBlockY() || y == max.getBlockY()
                                    || z == min.getBlockZ() || z == max.getBlockZ();

                    if (isSolid || isEdge) {
                        Material material = DungeonTemplate.getRandomMaterial(dungeonType);
                        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), x, y, z, material);
                        this.workloadRunnable.addWorkload(blockPlacementWorkload);
                    }
                }
            }
        }
    }

    public void fillObstacleCourse(Location cornerA, Location cornerB, DungeonTemplate.DungeonType dungeonType) {
        Preconditions.checkArgument(cornerA.getWorld() == cornerB.getWorld() && cornerA.getWorld() != null);

        World world = cornerA.getWorld();
        Random random = new Random();

        Location goldBlockLocation = generateGoldBlockLocation(cornerB, world, random);
        Location startPoint = generateStartPoint(cornerA, world, random);

        Location currentPoint = startPoint.clone();
        boolean goldBlockReached = false;
        List<Location> blacklistedCoordinates = new ArrayList<>();
        int yLevelMarkers = 0;
        while (!goldBlockReached || yLevelMarkers < 4){
            List<Location> potentialLocations = getPossibleNextLocations(currentPoint, cornerA, cornerB, world, blacklistedCoordinates);

            if (potentialLocations.isEmpty()) {
                goldBlockLocation = moveGoldBlockToCurrentPoint(currentPoint);
                break;
            }

            Location nextPoint = potentialLocations.get(random.nextInt(potentialLocations.size()));
            placePlatform(world, nextPoint, dungeonType, blacklistedCoordinates);
            if (currentPoint.getY() == nextPoint.getY()) {
                yLevelMarkers++;
            }
            currentPoint = nextPoint;

            goldBlockReached = checkGoldBlockReachability(goldBlockLocation, currentPoint);
        }

        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), goldBlockLocation.getBlockX(), goldBlockLocation.getBlockY(), goldBlockLocation.getBlockZ(), Material.GOLD_BLOCK);
        this.workloadRunnable.addWorkload(blockPlacementWorkload);
        createRoom(cornerA, cornerB, dungeonType);
    }

    private Location generateGoldBlockLocation(Location cornerB, World world, Random random) {
        return new Location(
                world,
                cornerB.getBlockX() - random.nextInt(3) - 3,
                cornerB.getBlockY() - random.nextInt(3) - 1,
                cornerB.getBlockZ() - random.nextInt(3) - 3
        );
    }

    private Location generateStartPoint(Location cornerA, World world, Random random) {
        return new Location(
                world,
                cornerA.getBlockX() + random.nextInt(3) + 2,
                cornerA.getBlockY() + 1,
                cornerA.getBlockZ() + random.nextInt(3) + 2
        );
    }

    private List<Location> getPossibleNextLocations(Location currentPoint, Location cornerA, Location cornerB, World world, List<Location> blacklistedCoordinates) {
        int maxHorizontalDistance = 3;
        int minHeightDifference = 1;
        int minHeight = cornerA.getBlockY() + 2;
        int maxHeight = cornerB.getBlockY() - 3;

        List<Location> potentialLocations = new ArrayList<>();

        for (int x = -maxHorizontalDistance; x <= maxHorizontalDistance; x++) {
            for (int z = -maxHorizontalDistance; z <= maxHorizontalDistance; z++) {
                for (int y = 0; y <= minHeightDifference; y++) {
                    Location potentialLocation = currentPoint.clone().add(x, y, z);
                    if (potentialLocation.getBlockX() < cornerA.getBlockX() + 1 || potentialLocation.getBlockX() > cornerB.getBlockX() - 1
                            || potentialLocation.getBlockY() < minHeight || potentialLocation.getBlockY() > maxHeight
                            || potentialLocation.getBlockZ() < cornerA.getBlockZ() + 1 || potentialLocation.getBlockZ() > cornerB.getBlockZ() - 1) {
                        continue;
                    }
                    boolean hasEnoughIntermediateSpace = true;
                    boolean hasAdjacentBlock = false;
                    int maxSteps = Math.max(Math.abs(x), Math.abs(z));
                    for (int step = 0; step <= maxSteps; step++) {
                        double t = (double) step / maxSteps;
                        Location intermediatePoint = currentPoint.clone().add(
                                (potentialLocation.getBlockX() - currentPoint.getBlockX()) * t,
                                0, // Keep the same y-coordinate for intermediate points
                                (potentialLocation.getBlockZ() - currentPoint.getBlockZ()) * t
                        );
                        boolean hasEnoughVerticalSpace = true;
                        for (int i = -4; i <= 4; i++) {
                            if (i == 0) continue; // Skip the current block itself
                            if (world.getBlockAt(intermediatePoint.clone().add(0, i, 0)).getType() != Material.AIR) {
                                hasEnoughVerticalSpace = false;
                                break;
                            }
                        }

                        if (!hasEnoughVerticalSpace) {
                            hasEnoughIntermediateSpace = false;
                            break;
                        }
                    }

                    // Check for adjacent blocks
                    int[] dx = {-1, 1, 0, 0, 1, -1, -1, 1};
                    int[] dz = {0, 0, -1, 1, 1, -1, 1, -1};

                    for (int i = 0; i < 4; i++) {
                        Location adjacentLocation = potentialLocation.clone().add(dx[i], 0, dz[i]);
                        if (world.getBlockAt(adjacentLocation).getType() != Material.AIR) {
                            hasAdjacentBlock = true;
                            break;
                        }
                    }
                    if (blacklistedCoordinates.contains(potentialLocation)) {
                        continue;
                    }
                    if (hasEnoughIntermediateSpace && !hasAdjacentBlock) {
                        potentialLocations.add(potentialLocation);
                    }
                }
            }
        }

        return potentialLocations;
    }


    private Location moveGoldBlockToCurrentPoint(Location currentPoint) {
        return currentPoint.clone().add(0, 0, 0);
    }

    private void placePlatform(World world, Location nextPoint, DungeonTemplate.DungeonType dungeonType, List<Location> blacklistedCoordinates) {
        if (blacklistedCoordinates.contains(nextPoint)) return;
        Material platformMaterial = DungeonTemplate.getRandomMaterial(dungeonType);
        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), nextPoint.getBlockX(), nextPoint.getBlockY(), nextPoint.getBlockZ(), platformMaterial);
        this.workloadRunnable.addWorkload(blockPlacementWorkload);

        for (int ex = -2; ex <= 2; ex++) {
            for (int ez = -2; ez <= 2; ez++) {
                blacklistedCoordinates.add(nextPoint.clone().add(ex, 2, ez));
            }
        }

        for (int i = 0; i <= 4; i++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blacklistedCoordinates.add(nextPoint.clone().add(dx, i, dz));
//                    Bukkit.getConsoleSender().sendMessage("Blacklisted: " + nextPoint.clone().add(dx, i, dz).toString());
                }
            }
        }
    }

    private boolean checkGoldBlockReachability(Location goldBlockLocation, Location currentPoint) {
        int xDifference = Math.abs(goldBlockLocation.getBlockX() - currentPoint.getBlockX());
        int yDifference = Math.abs(goldBlockLocation.getBlockY() - currentPoint.getBlockY());
        int zDifference = Math.abs(goldBlockLocation.getBlockZ() - currentPoint.getBlockZ());

        return xDifference <= 3 && yDifference <= 1 && zDifference <= 3;
    }


}
