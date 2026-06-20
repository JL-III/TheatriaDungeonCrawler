package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.objects.DungeonGrid;
import com.jliii.theatriadungeoncrawler.objects.RoomNode;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import com.jliii.theatriadungeoncrawler.util.runnables.BlockPlacementWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.DistributedWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.WallTorchWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadRunnable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Generates and grows an infinite dungeon into a void world, one segment at a time.
 *
 * <p>Rooms are aligned one-per-chunk on a single Y plane. The dungeon is made of
 * <em>segments</em>: a run of 7-15 rooms (the {@link #advance} builds one whole
 * segment) ending in a single emerald <em>checkpoint</em> room. Doors within a
 * segment are open, so the player walks the stretch freely.</p>
 *
 * <h2>Checkpoint ("loading room") mechanic</h2>
 * Only the last room of a segment holds the emerald. When the player steps on it,
 * {@link #advance}:
 * <ol>
 *   <li>seals the door behind the player,</li>
 *   <li>builds the next whole segment, opening each forward door <em>last</em>
 *       so — because the build queue is FIFO — a door only opens once its room
 *       has finished building,</li>
 *   <li>moves the emerald into the new segment's checkpoint, and</li>
 *   <li>removes every room behind the checkpoint, freeing those chunks.</li>
 * </ol>
 *
 * <h2>Dead-end avoidance &amp; wayfinding</h2>
 * The growth direction is chosen like a snake game: a bounded flood fill over
 * free chunks rejects directions that would box the dungeon into a dead end.
 * Door openings are lit with wall torches, and each checkpoint records the
 * cardinal direction it opened toward (announced to the player).
 *
 * <p>All block edits are queued onto the instance's {@link WorkloadRunnable},
 * which spreads them across ticks to avoid stalling the server.</p>
 */
public class DungeonLayoutGenerator {

    /** Chunk size in blocks; each room occupies one chunk cell. */
    private static final int CHUNK = 16;
    /** Empty border between a room's walls and the chunk edge. */
    private static final int MARGIN = 2;
    /** Room footprint (x and z), walls included. */
    private static final int FOOT = CHUNK - 2 * MARGIN; // 12
    /** Room height, walls included. */
    private static final int HEIGHT = 6;
    /** Corridor tube height. */
    private static final int CORRIDOR_HEIGHT = 5;
    /** Door / corridor width (must be odd so it centres on a wall). */
    private static final int DOOR_WIDTH = 3;
    /** Door opening height. */
    private static final int DOOR_HEIGHT = 3;
    /** Minimum free chunks reachable from a candidate for it to be "safe". */
    private static final int SAFETY_CELLS = 16;
    /** A segment is this many rooms (inclusive bounds) ending in a checkpoint. */
    private static final int MIN_SEGMENT = 7;
    private static final int MAX_SEGMENT = 15;

    private static final Material CORRIDOR_MATERIAL = Material.STONE_BRICKS;
    private static final Material GOAL_MARKER = Material.EMERALD_BLOCK;
    /** Block that replaces a consumed emerald checkpoint. */
    private static final Material FLOOR_MATERIAL = Material.STONE_BRICKS;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final World world;
    private final WorkloadRunnable workloadRunnable;
    private final DistributedWorkload workload;

    public DungeonLayoutGenerator(World world, WorkloadRunnable workloadRunnable) {
        this.world = world;
        this.workloadRunnable = workloadRunnable;
        this.workload = new DistributedWorkload(workloadRunnable);
    }

    /**
     * Builds the spawn room and the first segment, ending in the emerald
     * checkpoint (loading) room.
     *
     * @param origin             lowest corner of the start chunk (its Y is the floor)
     * @param fixedSegmentLength forced segment length, or {@code <= 0} for random
     * @param theme              fixed theme for every room, or {@code null} for random
     * @return the dungeon's growing layout state
     */
    public DungeonGrid generateInitial(Location origin, int fixedSegmentLength, DungeonTemplate.DungeonType theme, Random random) {
        DungeonGrid grid = new DungeonGrid(world, origin.getBlockY(), theme, fixedSegmentLength);

        Coord start = new Coord(0, 0);
        DungeonTemplate.DungeonType startTheme = themeFor(theme, random);
        Location[] startBox = roomBox(grid, start);
        workload.createRoom(startBox[0], startBox[1], startTheme);
        grid.markOccupied(start);
        RoomNode startNode = new RoomNode(start, startTheme, startBox[0], startBox[1], null, null, null, null);
        grid.getPath().addLast(startNode);
        grid.setSpawn(spawnLocation(grid, start));

        RoomNode checkpoint = growSegment(grid, startNode, segmentLength(grid, random), random);
        placeEmerald(grid, checkpoint.getChunk());
        return grid;
    }

    /**
     * Advances the dungeon a whole segment when the player reaches a checkpoint:
     * seals the door behind, builds the next 7-15 room segment ending in a new
     * checkpoint, then removes every room behind the checkpoint the player is
     * standing in.
     *
     * @return {@code true} if the dungeon was extended; {@code false} only if the
     *         checkpoint was somehow boxed in (effectively never on an open plane)
     */
    public boolean advance(DungeonGrid grid, Random random) {
        RoomNode checkpoint = grid.getPath().peekLast();
        if (checkpoint == null) {
            return false;
        }
        Coord checkpointChunk = checkpoint.getChunk();

        // Seal the door behind the player and consume the emerald.
        if (checkpoint.hasDoor()) {
            closeFill(checkpoint.getDoorMin(), checkpoint.getDoorMax(), doorMaterial(checkpoint.getTheme()));
        }
        removeEmerald(grid, checkpointChunk);

        // Build the next whole segment. The door out of the checkpoint is carved
        // last (inside extendRoom), so it only opens once its room has built.
        RoomNode nextCheckpoint = growSegment(grid, checkpoint, segmentLength(grid, random), random);
        if (nextCheckpoint == checkpoint) {
            // Could not extend at all (effectively impossible); undo the seal.
            if (checkpoint.hasDoor()) {
                carveOpen(checkpoint.getDoorMin(), checkpoint.getDoorMax());
            }
            placeEmerald(grid, checkpointChunk);
            return false;
        }
        placeEmerald(grid, nextCheckpoint.getChunk());

        // Remove every room behind the checkpoint the player just stepped on.
        while (grid.getPath().peekFirst() != checkpoint) {
            removeTail(grid);
        }
        return true;
    }

    /**
     * Grows a connected run of {@code rooms} rooms branching from {@code start};
     * the last room is the segment's checkpoint. Doors within the segment are
     * left open, and each room is given a floor trail pointing to its exit.
     *
     * @return the last room built (the checkpoint), or {@code start} if none
     *         could be placed
     */
    private RoomNode growSegment(DungeonGrid grid, RoomNode start, int rooms, Random random) {
        RoomNode cur = start;
        for (int i = 0; i < rooms; i++) {
            int[] dir = pickDirection(grid, cur.getChunk(), random);
            if (dir == null) {
                break;
            }
            RoomNode node = extendRoom(grid, cur.getChunk(), dir, themeFor(grid.getTheme(), random));
            if (cur == start) {
                grid.setLastExitDirection(cardinal(dir));
            }
            grid.getPath().addLast(node);
            cur = node;
        }
        return cur;
    }

    private int segmentLength(DungeonGrid grid, Random random) {
        int fixed = grid.getFixedSegmentLength();
        if (fixed > 0) {
            return fixed;
        }
        return MIN_SEGMENT + random.nextInt(MAX_SEGMENT - MIN_SEGMENT + 1);
    }

    // --- room / corridor construction -------------------------------------

    /**
     * Builds the room adjacent to {@code from} in direction {@code dir}, the
     * corridor between them, and carves both doorways (the new room's incoming
     * door, then the {@code from} room's outgoing door last). Marks the new
     * chunk occupied and returns its node.
     */
    private RoomNode extendRoom(DungeonGrid grid, Coord from, int[] dir, DungeonTemplate.DungeonType theme) {
        Connection c = connection(grid, from, dir[0], dir[1]);
        Location[] box = roomBox(grid, c.to);

        workload.createRoom(box[0], box[1], theme);
        workload.fillHollowCorridor(c.corridorMin, c.corridorMax, CORRIDOR_MATERIAL);
        carveOpen(c.toDoorMin, c.toDoorMax);     // new room's incoming door
        carveOpen(c.fromDoorMin, c.fromDoorMax); // from room's outgoing door (gated last)
        placeCorridorTorches(grid, c);           // light the connecting corridor

        grid.markOccupied(c.to);
        return new RoomNode(c.to, theme, box[0], box[1],
                c.corridorMin, c.corridorMax, c.toDoorMin, c.toDoorMax);
    }

    /** Clears the tail room (and its corridors), freeing its chunk for reuse. */
    private void removeTail(DungeonGrid grid) {
        RoomNode tail = grid.getPath().pollFirst();
        if (tail == null) {
            return;
        }
        clearBox(tail.getRoomMin(), tail.getRoomMax());
        if (tail.hasCorridor()) {
            clearCorridorInterior(tail.getCorridorMin(), tail.getCorridorMax());
        }
        // Also clear the corridor stub joining the removed tail to the new tail.
        // Only the interior is cleared so the kept room's wall (and its sealed
        // door) is never punched out — that left holes in the map.
        RoomNode successor = grid.getPath().peekFirst();
        if (successor != null && successor.hasCorridor()) {
            clearCorridorInterior(successor.getCorridorMin(), successor.getCorridorMax());
            successor.clearCorridor();
        }
        grid.freeChunk(tail.getChunk());
    }

    // --- direction choice (snake dead-end avoidance) ----------------------

    /**
     * Chooses a free neighbouring chunk to grow into, preferring directions with
     * enough open space ahead (flood fill) so the dungeon cannot trap itself.
     *
     * @return the chosen direction, or {@code null} if every neighbour is occupied
     */
    private int[] pickDirection(DungeonGrid grid, Coord head, Random random) {
        List<int[]> dirs = new ArrayList<>();
        Collections.addAll(dirs, DIRS);
        Collections.shuffle(dirs, random);

        int[] best = null;
        int bestReach = -1;
        for (int[] d : dirs) {
            Coord candidate = new Coord(head.getX() + d[0], head.getZ() + d[1]);
            if (grid.isOccupied(candidate)) {
                continue;
            }
            int reach = floodReach(grid, candidate);
            if (reach >= SAFETY_CELLS) {
                return d; // safe enough; random order keeps it varied
            }
            if (reach > bestReach) {
                bestReach = reach;
                best = d;
            }
        }
        return best; // least-bad free direction, or null if fully boxed in
    }

    /**
     * Counts free chunks reachable from {@code start} (capped at
     * {@link #SAFETY_CELLS}), treating currently-occupied chunks as walls.
     */
    private int floodReach(DungeonGrid grid, Coord start) {
        Set<Coord> blocked = grid.getOccupiedChunks();

        Set<Coord> visited = new HashSet<>();
        Deque<Coord> queue = new ArrayDeque<>();
        queue.add(start);
        int count = 0;
        while (!queue.isEmpty() && count < SAFETY_CELLS) {
            Coord c = queue.poll();
            if (visited.contains(c) || blocked.contains(c)) {
                continue;
            }
            visited.add(c);
            count++;
            for (int[] d : DIRS) {
                Coord n = new Coord(c.getX() + d[0], c.getZ() + d[1]);
                if (!visited.contains(n) && !blocked.contains(n)) {
                    queue.add(n);
                }
            }
        }
        return count;
    }

    // --- geometry ---------------------------------------------------------

    private int roomMinX(Coord cell) {
        return cell.getX() * CHUNK + MARGIN;
    }

    private int roomMinZ(Coord cell) {
        return cell.getZ() * CHUNK + MARGIN;
    }

    private int centerX(Coord cell) {
        return roomMinX(cell) + FOOT / 2;
    }

    private int centerZ(Coord cell) {
        return roomMinZ(cell) + FOOT / 2;
    }

    private Location[] roomBox(DungeonGrid grid, Coord cell) {
        int oy = grid.getOriginY();
        int minX = roomMinX(cell);
        int minZ = roomMinZ(cell);
        Location min = new Location(world, minX, oy, minZ);
        Location max = new Location(world, minX + FOOT - 1, oy + HEIGHT - 1, minZ + FOOT - 1);
        return new Location[]{min, max};
    }

    private Location spawnLocation(DungeonGrid grid, Coord cell) {
        return new Location(world, centerX(cell) + 0.5, grid.getOriginY() + 1, centerZ(cell) + 0.5);
    }

    /**
     * Computes the corridor box and the two doorway boxes between {@code from}
     * and its neighbour in direction (dx, dz).
     */
    private Connection connection(DungeonGrid grid, Coord from, int dx, int dz) {
        int oy = grid.getOriginY();
        Coord to = new Coord(from.getX() + dx, from.getZ() + dz);

        int fMinX = roomMinX(from);
        int fMaxX = fMinX + FOOT - 1;
        int fMinZ = roomMinZ(from);
        int fMaxZ = fMinZ + FOOT - 1;
        int tMinX = roomMinX(to);
        int tMaxX = tMinX + FOOT - 1;
        int tMinZ = roomMinZ(to);
        int tMaxZ = tMinZ + FOOT - 1;

        int half = DOOR_WIDTH / 2;
        int doorTop = oy + DOOR_HEIGHT;        // door spans oy+1 .. oy+DOOR_HEIGHT
        int corrTop = oy + CORRIDOR_HEIGHT - 1;

        if (dz == 0) {
            // East/West: corridor along X, centred on Z.
            int zc = fMinZ + FOOT / 2;
            int fWallX = (dx == 1) ? fMaxX : fMinX;
            int tWallX = (dx == 1) ? tMinX : tMaxX;
            int loX = Math.min(fWallX, tWallX);
            int hiX = Math.max(fWallX, tWallX);
            return new Connection(to,
                    new Location(world, loX, oy, zc - half),
                    new Location(world, hiX, corrTop, zc + half),
                    new Location(world, fWallX, oy + 1, zc - half),
                    new Location(world, fWallX, doorTop, zc + half),
                    new Location(world, tWallX, oy + 1, zc - half),
                    new Location(world, tWallX, doorTop, zc + half));
        } else {
            // North/South: corridor along Z, centred on X.
            int xc = fMinX + FOOT / 2;
            int fWallZ = (dz == 1) ? fMaxZ : fMinZ;
            int tWallZ = (dz == 1) ? tMinZ : tMaxZ;
            int loZ = Math.min(fWallZ, tWallZ);
            int hiZ = Math.max(fWallZ, tWallZ);
            return new Connection(to,
                    new Location(world, xc - half, oy, loZ),
                    new Location(world, xc + half, corrTop, hiZ),
                    new Location(world, xc - half, oy + 1, fWallZ),
                    new Location(world, xc + half, doorTop, fWallZ),
                    new Location(world, xc - half, oy + 1, tWallZ),
                    new Location(world, xc + half, doorTop, tWallZ));
        }
    }

    // --- block operations (queued) ----------------------------------------

    private void placeEmerald(DungeonGrid grid, Coord cell) {
        int cx = centerX(cell);
        int cz = centerZ(cell);
        int oy = grid.getOriginY();
        workloadRunnable.addWorkload(new BlockPlacementWorkload(world.getUID(), cx, oy, cz, GOAL_MARKER));
        grid.setEmeraldLocation(new Location(world, cx, oy, cz));
    }

    private void removeEmerald(DungeonGrid grid, Coord cell) {
        int cx = centerX(cell);
        int cz = centerZ(cell);
        workloadRunnable.addWorkload(new BlockPlacementWorkload(world.getUID(), cx, grid.getOriginY(), cz, FLOOR_MATERIAL));
    }

    /**
     * Mounts a wall torch near each end of the corridor (one by each doorway) on
     * the corridor's stone-brick side wall, so the connectors between rooms are
     * lit. The torches sit on the corridor wall, never on a room wall, so they
     * are removed cleanly with the corridor and never left floating.
     */
    private void placeCorridorTorches(DungeonGrid grid, Connection c) {
        int minX = c.corridorMin.getBlockX();
        int maxX = c.corridorMax.getBlockX();
        int minZ = c.corridorMin.getBlockZ();
        int maxZ = c.corridorMax.getBlockZ();
        int y = c.corridorMin.getBlockY() + 2;
        UUID w = world.getUID();

        if (maxX - minX >= maxZ - minZ) {
            // East/West corridor: mount on the north side wall, facing into it.
            int torchZ = minZ + 1;
            workloadRunnable.addWorkload(new WallTorchWorkload(w, minX + 1, y, torchZ, BlockFace.SOUTH));
            workloadRunnable.addWorkload(new WallTorchWorkload(w, maxX - 1, y, torchZ, BlockFace.SOUTH));
        } else {
            // North/South corridor: mount on the west side wall.
            int torchX = minX + 1;
            workloadRunnable.addWorkload(new WallTorchWorkload(w, torchX, y, minZ + 1, BlockFace.EAST));
            workloadRunnable.addWorkload(new WallTorchWorkload(w, torchX, y, maxZ - 1, BlockFace.EAST));
        }
    }

    /**
     * Clears a corridor's interior to air, excluding the wall planes at its two
     * ends so an adjacent kept room's wall is never punched out.
     */
    private void clearCorridorInterior(Location min, Location max) {
        int minX = min.getBlockX();
        int maxX = max.getBlockX();
        int minZ = min.getBlockZ();
        int maxZ = max.getBlockZ();
        int minY = min.getBlockY();
        int maxY = max.getBlockY();
        if (maxX - minX >= maxZ - minZ) {
            workload.fillSolidBox(
                    new Location(world, minX + 1, minY, minZ),
                    new Location(world, maxX - 1, maxY, maxZ),
                    Material.AIR);
        } else {
            workload.fillSolidBox(
                    new Location(world, minX, minY, minZ + 1),
                    new Location(world, maxX, maxY, maxZ - 1),
                    Material.AIR);
        }
    }

    private void carveOpen(Location min, Location max) {
        workload.fillSolidBox(min, max, Material.AIR);
    }

    private void closeFill(Location min, Location max, Material material) {
        workload.fillSolidBox(min, max, material);
    }

    private void clearBox(Location min, Location max) {
        workload.fillSolidBox(min, max, Material.AIR);
    }

    // --- helpers ----------------------------------------------------------

    private DungeonTemplate.DungeonType themeFor(DungeonTemplate.DungeonType theme, Random random) {
        return theme != null ? theme : DungeonTemplate.getRandomTheme();
    }

    private Material doorMaterial(DungeonTemplate.DungeonType theme) {
        DungeonTemplate.DungeonType t = theme != null ? theme : DungeonTemplate.getRandomTheme();
        return DungeonTemplate.getRandomMaterial(t);
    }

    /** Maps a grid direction to a cardinal name (Minecraft: +X east, +Z south). */
    private String cardinal(int[] dir) {
        if (dir[0] == 1) {
            return "East";
        }
        if (dir[0] == -1) {
            return "West";
        }
        if (dir[1] == 1) {
            return "South";
        }
        return "North";
    }

    /** Immutable bundle of the boxes that make up a room-to-room connection. */
    private static final class Connection {
        private final Coord to;
        private final Location corridorMin;
        private final Location corridorMax;
        private final Location fromDoorMin;
        private final Location fromDoorMax;
        private final Location toDoorMin;
        private final Location toDoorMax;

        private Connection(Coord to,
                           Location corridorMin, Location corridorMax,
                           Location fromDoorMin, Location fromDoorMax,
                           Location toDoorMin, Location toDoorMax) {
            this.to = to;
            this.corridorMin = corridorMin;
            this.corridorMax = corridorMax;
            this.fromDoorMin = fromDoorMin;
            this.fromDoorMax = fromDoorMax;
            this.toDoorMin = toDoorMin;
            this.toDoorMax = toDoorMax;
        }
    }
}
