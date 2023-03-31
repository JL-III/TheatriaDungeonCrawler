package com.jliii.theatriadungeoncrawler.runnables;

import com.google.common.base.Preconditions;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

@AllArgsConstructor
public class DistributedFiller {

    private final WorkloadRunnable workloadRunnable;


    public void fillHollowBox(Location cornerA, Location cornerB, DungeonTemplate.DungeonType dungeonType) {
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
        Set<Pair<Integer, Integer>> blacklistedCoordinates = new HashSet<>();

        while (!goldBlockReached) {
            List<Location> potentialLocations = getPossibleNextLocations(currentPoint, cornerA, cornerB, world, blacklistedCoordinates);

            if (potentialLocations.isEmpty()) {
                goldBlockLocation = moveGoldBlockToCurrentPoint(currentPoint);
                break;
            }

            Location nextPoint = potentialLocations.get(random.nextInt(potentialLocations.size()));
            placePlatform(world, nextPoint, dungeonType, blacklistedCoordinates, currentPoint);

            currentPoint = nextPoint;

            goldBlockReached = checkGoldBlockReachability(goldBlockLocation, currentPoint);
        }

        world.getBlockAt(goldBlockLocation).setType(Material.GOLD_BLOCK);
        fillHollowBox(cornerA, cornerB, dungeonType);
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

    private List<Location> getPossibleNextLocations(Location currentPoint, Location cornerA, Location cornerB, World world, Set<Pair<Integer, Integer>> blacklistedCoordinates) {
        int maxHorizontalDistance = 3;
        int minHeightDifference = 1;
        int minHeight = cornerA.getBlockY() + 2;
        int maxHeight = cornerB.getBlockY() - 3;

        List<Location> potentialLocations = new ArrayList<>();

        for (int x = -maxHorizontalDistance; x <= maxHorizontalDistance; x++) {
            for (int z = -maxHorizontalDistance; z <= maxHorizontalDistance; z++) {
                for (int y = 0; y <= minHeightDifference; y++) {
                    Location potentialLocation = currentPoint.clone().add(x, y, z);

                    // Check if the potential location is within bounds
                    if (potentialLocation.getBlockX() < cornerA.getBlockX() + 1 || potentialLocation.getBlockX() > cornerB.getBlockX() - 1
                            || potentialLocation.getBlockY() < minHeight || potentialLocation.getBlockY() > maxHeight
                            || potentialLocation.getBlockZ() < cornerA.getBlockZ() + 1 || potentialLocation.getBlockZ() > cornerB.getBlockZ() - 1) {
                        continue;
                    }

                    // Check if there's enough space above the potential location
                    boolean hasEnoughSpace = true;
                    for (int i = 1; i <= 4; i++) {
                        if (world.getBlockAt(potentialLocation.clone().add(0, i, 0)).getType() != Material.AIR) {
                            hasEnoughSpace = false;
                            break;
                        }
                    }

                    if (!hasEnoughSpace) {
                        continue;
                    }


                    // Check if the potential location shares the same (x, z) coordinates with any of the blacklisted points
                    Pair<Integer, Integer> potentialCoord = Pair.of(potentialLocation.getBlockX(), potentialLocation.getBlockZ());
                    if (blacklistedCoordinates.contains(potentialCoord)) {
                        continue;
                    }

                    potentialLocations.add(potentialLocation);
                }
            }
        }

        return potentialLocations;
    }


    private Location moveGoldBlockToCurrentPoint(Location currentPoint) {
        return currentPoint.clone().add(0, 1, 0);
    }

    private void placePlatform(World world, Location nextPoint, DungeonTemplate.DungeonType dungeonType, Set<Pair<Integer, Integer>> blacklistedCoordinates, Location currentPoint) {
        Material platformMaterial = DungeonTemplate.getRandomMaterial(dungeonType);
        world.getBlockAt(nextPoint).setType(platformMaterial);
        blacklistedCoordinates.add(Pair.of(currentPoint.getBlockX(), currentPoint.getBlockZ()));
    }

    private boolean checkGoldBlockReachability(Location goldBlockLocation, Location currentPoint) {
        int xDifference = Math.abs(goldBlockLocation.getBlockX() - currentPoint.getBlockX());
        int yDifference = Math.abs(goldBlockLocation.getBlockY() - currentPoint.getBlockY());
        int zDifference = Math.abs(goldBlockLocation.getBlockZ() - currentPoint.getBlockZ());

        return xDifference <= 3 && yDifference <= 1 && zDifference <= 3;
    }


}
