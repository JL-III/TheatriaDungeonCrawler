package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.challenge.ChallengeFactory;
import com.jliii.theatriadungeoncrawler.challenge.ChallengeState;
import com.jliii.theatriadungeoncrawler.challenge.ChallengeType;
import com.jliii.theatriadungeoncrawler.challenge.RoomChallenge;
import com.jliii.theatriadungeoncrawler.challenge.RoomContext;
import com.jliii.theatriadungeoncrawler.challenge.RoomScope;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.DungeonGrid;
import com.jliii.theatriadungeoncrawler.objects.RoomNode;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import com.jliii.theatriadungeoncrawler.util.runnables.DungeonBuilder;
import com.jliii.theatriadungeoncrawler.util.runnables.WallTorchWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadQueue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

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
 * <p>Rooms are chunk-aligned on a single Y plane: each room occupies a small
 * rectangle of chunk cells (1x1, 1x2, 2x1 or 2x2) for added size variation. The
 * dungeon is made of
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
 * <p>All block edits are queued onto the instance's {@link WorkloadQueue},
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
    /** Bridge tube height (floor + door + ceiling). */
    private static final int CORRIDOR_HEIGHT = 4;
    /** Outer bridge width (box); its interior is two narrower than this. */
    private static final int CORRIDOR_WIDTH = 3;
    /** Door opening width (odd, centred). Matches the bridge interior
     *  ({@code CORRIDOR_WIDTH - 2}) so the doorway is uniform. */
    private static final int DOOR_WIDTH = 1;
    /** Door opening height. */
    private static final int DOOR_HEIGHT = 2;
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

    private final Plugin plugin;
    private final Dungeon dungeon;
    private final World world;
    private final WorkloadQueue workloadQueue;
    private final DungeonBuilder workload;
    private final ChallengeFactory challengeFactory = new ChallengeFactory();

    public DungeonLayoutGenerator(Plugin plugin, Dungeon dungeon) {
        this.plugin = plugin;
        this.dungeon = dungeon;
        this.world = dungeon.getWorld();
        this.workloadQueue = dungeon.getWorkloadQueue();
        this.workload = new DungeonBuilder(workloadQueue);
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

        // The spawn room is always a single cell so the player starts centred.
        List<Coord> startCells = new ArrayList<>();
        startCells.add(new Coord(0, 0));
        DungeonTemplate.DungeonType startTheme = themeFor(theme, random);
        Location[] startBox = boxFromCells(grid, startCells);
        workload.createRoom(startBox[0], startBox[1], startTheme);
        for (Coord cell : startCells) {
            grid.markOccupied(cell);
        }
        RoomNode startNode = new RoomNode(startCells, startTheme, startBox[0], startBox[1],
                null, null, null, null, null);
        attachChallenge(grid, startNode, ChallengeType.EMPTY);
        grid.getPath().addLast(startNode);
        grid.setSpawn(boxCenter(grid, startNode, 1));
        // Until the first checkpoint is reached, a death respawns at the start room.
        grid.setCheckpointSpawn(boxCenter(grid, startNode, 1));

        RoomNode checkpoint = growSegment(grid, startNode, segmentLength(grid, random), random);
        placeEmerald(grid, checkpoint);
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

        // Seal the door behind the player and consume the emerald.
        if (checkpoint.hasDoor()) {
            closeFill(checkpoint.getDoorMin(), checkpoint.getDoorMax(), CORRIDOR_MATERIAL);
        }
        removeEmerald(grid, checkpoint);

        // Build the next whole segment. The door out of the checkpoint is carved
        // last (inside buildRoom), so it only opens once its room has built.
        RoomNode nextCheckpoint = growSegment(grid, checkpoint, segmentLength(grid, random), random);
        if (nextCheckpoint == checkpoint) {
            // Could not extend at all (effectively impossible); undo the seal.
            if (checkpoint.hasDoor()) {
                carveOpen(checkpoint.getDoorMin(), checkpoint.getDoorMax());
            }
            placeEmerald(grid, checkpoint);
            return false;
        }
        placeEmerald(grid, nextCheckpoint);

        // The checkpoint the player reached is now the respawn anchor; it becomes
        // the head of the snake once the rooms behind it are removed.
        grid.setCheckpointSpawn(boxCenter(grid, checkpoint, 1));

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
            // The last room is the checkpoint and is never gated; never gate two
            // rooms in a row either, so the player always gets a breather.
            boolean last = (i == rooms - 1);
            boolean allowGated = !last && !isGated(cur);
            ChallengeType type = pickChallengeType(random, allowGated);

            RoomNode node = tryExtend(grid, cur, random, type);
            if (node == null) {
                break;
            }
            if (cur == start) {
                // Growth root (spawn, or the checkpoint when advancing): never seal
                // its forward door — that would soft-lock the segment.
                grid.setLastExitDirection(cardinal(node.getEntryDir()));
            } else {
                lockIfGated(cur, node);
            }
            grid.getPath().addLast(node);
            cur = node;
        }
        ensureFreeCheckpoint(cur, start);
        return cur;
    }

    /**
     * If {@code predecessor} is an unsolved gated room, fills the entire passage
     * into {@code next} (the predecessor's forward connector) so it reads as a
     * solid wall, and records it as the predecessor's lock. The manager carves
     * the whole passage back open when the gate is solved.
     */
    private void lockIfGated(RoomNode predecessor, RoomNode next) {
        RoomChallenge challenge = predecessor.getChallenge();
        if (challenge != null && challenge.isGated()
                && predecessor.getState() != ChallengeState.COMPLETE
                && next.hasTunnel()) {
            closeFill(next.getTunnelMin(), next.getTunnelMax(), CORRIDOR_MATERIAL);
            predecessor.setLockDoor(next.getTunnelMin(), next.getTunnelMax());
        }
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
     * Grows a new room off {@code cur} in a safe direction, preferring openings
     * with enough free space ahead (flood fill) so the dungeon cannot trap
     * itself. The new room may span 1-2 cells in each axis for size variation.
     *
     * <p>The growth always bridges from a <em>boundary cell</em> of {@code cur}
     * (one whose neighbour in the chosen direction is outside the room) to the
     * <em>entry cell</em> of the new room. Because that boundary cell's outward
     * wall is the room's actual wall, the cell-to-cell {@link #connection} stays
     * correct for multi-cell rooms.</p>
     *
     * @return the new room, or {@code null} if {@code cur} is fully boxed in
     */
    private RoomNode tryExtend(DungeonGrid grid, RoomNode cur, Random random, ChallengeType type) {
        List<int[]> dirs = new ArrayList<>();
        Collections.addAll(dirs, DIRS);
        Collections.shuffle(dirs, random);

        int[] bestDir = null;
        Coord bestBoundary = null;
        List<Coord> bestCells = null;
        int bestReach = -1;

        for (int[] dir : dirs) {
            List<Coord> boundary = boundaryCells(cur, dir);
            Collections.shuffle(boundary, random);
            for (Coord b : boundary) {
                Coord entry = new Coord(b.getX() + dir[0], b.getZ() + dir[1]);
                if (grid.isOccupied(entry)) {
                    continue;
                }
                List<Coord> cells = expandFootprint(grid, entry, dir, random);
                int reach = floodReach(grid, entry);
                if (reach >= SAFETY_CELLS) {
                    return buildRoom(grid, b, dir, cells, themeFor(grid.getTheme(), random), type);
                }
                if (reach > bestReach) {
                    bestReach = reach;
                    bestDir = dir;
                    bestBoundary = b;
                    bestCells = cells;
                }
            }
        }
        if (bestCells == null) {
            return null; // fully boxed in
        }
        return buildRoom(grid, bestBoundary, bestDir, bestCells, themeFor(grid.getTheme(), random), type);
    }

    /**
     * Builds a new room occupying {@code cells}, bridged to the predecessor from
     * its boundary cell {@code b} in direction {@code dir}. Lays the room shell,
     * a solid stone-brick bridge spanning both shared walls, then carves one
     * continuous tunnel straight through it (queued last so nothing re-seals it).
     * Marks the new cells occupied and returns the node.
     */
    private RoomNode buildRoom(DungeonGrid grid, Coord b, int[] dir, List<Coord> cells,
                               DungeonTemplate.DungeonType theme, ChallengeType type) {
        Connection c = connection(grid, b, dir[0], dir[1]);
        Coord entry = c.to;
        Location[] box = boxFromCells(grid, cells);

        workload.createRoom(box[0], box[1], theme);
        workload.fillSolidBox(c.bridgeMin, c.bridgeMax, CORRIDOR_MATERIAL);
        carveOpen(c.tunnelMin, c.tunnelMax);
        placeDoorTorches(grid, b, dir);                              // light the exit doorway
        placeDoorTorches(grid, entry, new int[]{-dir[0], -dir[1]});  // light the entrance doorway

        for (Coord cell : cells) {
            grid.markOccupied(cell);
        }
        RoomNode node = new RoomNode(cells, theme, box[0], box[1],
                c.bridgeMin, c.bridgeMax, c.toDoorMin, c.toDoorMax, dir);
        node.setTunnel(c.tunnelMin, c.tunnelMax);
        attachChallenge(grid, node, type);
        return node;
    }

    /**
     * Assigns a fresh challenge (and its id + resource scope) to a room and lets
     * it build any challenge geometry. Free rooms start {@code COMPLETE} (their
     * forward door is already open); gated rooms start {@code PENDING}.
     */
    private void attachChallenge(DungeonGrid grid, RoomNode node, ChallengeType type) {
        node.setRoomId(grid.nextRoomId());
        node.setScope(new RoomScope(plugin, world));
        RoomChallenge challenge = challengeFactory.create(type);
        node.setChallenge(challenge);
        node.setState(challenge.isGated() ? ChallengeState.PENDING : ChallengeState.COMPLETE);
        challenge.build(new RoomContext(dungeon, node));
    }

    /** Chooses the challenge for a freshly grown room: ~1 in 4 gated when allowed. */
    private ChallengeType pickChallengeType(Random random, boolean allowGated) {
        if (allowGated && random.nextInt(4) == 0) {
            return ChallengeType.REACH_GOAL;
        }
        return ChallengeType.EMPTY;
    }

    private boolean isGated(RoomNode node) {
        return node.getChallenge() != null && node.getChallenge().isGated();
    }

    /**
     * Safety net for the early-break case: if growth dead-ended and left a gated
     * room as the checkpoint, demote it to a free room (reverting its challenge
     * blocks) so its forward door is never sealed when the next segment is built.
     */
    private void ensureFreeCheckpoint(RoomNode checkpoint, RoomNode start) {
        if (checkpoint != start && isGated(checkpoint)) {
            checkpoint.getChallenge().teardown(new RoomContext(dungeon, checkpoint));
            checkpoint.setChallenge(challengeFactory.create(ChallengeType.EMPTY));
            checkpoint.setState(ChallengeState.COMPLETE);
            checkpoint.setLockDoor(null, null);
        }
    }

    /** Runs a room's challenge teardown and disposes its tracked entities/tasks. */
    private void teardownRoom(RoomNode node) {
        if (node.getChallenge() != null) {
            node.getChallenge().teardown(new RoomContext(dungeon, node));
        }
        if (node.getScope() != null) {
            node.getScope().dispose();
        }
    }

    /** Clears the tail room (and its corridors), freeing its cells for reuse. */
    private void removeTail(DungeonGrid grid) {
        RoomNode tail = grid.getPath().pollFirst();
        if (tail == null) {
            return;
        }
        teardownRoom(tail);
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
        for (Coord cell : tail.getCells()) {
            grid.freeChunk(cell);
        }
    }

    // --- footprint choice (snake dead-end avoidance + size variation) -----

    /** @return the cells of {@code room} whose neighbour in {@code dir} is outside it. */
    private List<Coord> boundaryCells(RoomNode room, int[] dir) {
        Set<Coord> cellSet = new HashSet<>(room.getCells());
        List<Coord> result = new ArrayList<>();
        for (Coord c : room.getCells()) {
            Coord n = new Coord(c.getX() + dir[0], c.getZ() + dir[1]);
            if (!cellSet.contains(n)) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * Chooses the new room's footprint anchored at its entry cell {@code entry}:
     * 1-2 cells along {@code dir} and 1-2 cells along one perpendicular axis, all
     * of which must be free. Falls back to a smaller rectangle (ultimately just
     * {@code entry}) when the larger choices would overlap occupied cells.
     *
     * <p>{@code entry} is always the {@code -dir} corner of the result, so its
     * outward wall coincides with the room's wall and the doorway lands cleanly.</p>
     */
    private List<Coord> expandFootprint(DungeonGrid grid, Coord entry, int[] dir, Random random) {
        int[] perpDir = perpDirection(dir, random);
        int along = 1 + random.nextInt(2);
        int perp = 1 + random.nextInt(2);
        int[][] combos = {{along, perp}, {along, 1}, {1, perp}, {1, 1}};
        for (int[] combo : combos) {
            List<Coord> cells = rect(entry, dir, combo[0], perpDir, combo[1]);
            if (allFree(grid, cells)) {
                return cells;
            }
        }
        return rect(entry, dir, 1, perpDir, 1); // entry alone (already known free)
    }

    /** @return one of the two axes perpendicular to {@code dir}, chosen at random. */
    private int[] perpDirection(int[] dir, Random random) {
        if (dir[1] == 0) {
            return random.nextBoolean() ? new int[]{0, 1} : new int[]{0, -1};
        }
        return random.nextBoolean() ? new int[]{1, 0} : new int[]{-1, 0};
    }

    /** Builds an {@code along x perp} block of cells anchored at {@code entry}. */
    private List<Coord> rect(Coord entry, int[] dir, int along, int[] perpDir, int perp) {
        List<Coord> cells = new ArrayList<>();
        for (int a = 0; a < along; a++) {
            for (int p = 0; p < perp; p++) {
                cells.add(new Coord(
                        entry.getX() + a * dir[0] + p * perpDir[0],
                        entry.getZ() + a * dir[1] + p * perpDir[1]));
            }
        }
        return cells;
    }

    private boolean allFree(DungeonGrid grid, List<Coord> cells) {
        for (Coord c : cells) {
            if (grid.isOccupied(c)) {
                return false;
            }
        }
        return true;
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

    /**
     * @return the world-space {@code [min, max]} corners of the room spanning all
     *         of {@code cells} (a 1x1..2x2 rectangle), walls included.
     */
    private Location[] boxFromCells(DungeonGrid grid, List<Coord> cells) {
        int minCx = Integer.MAX_VALUE;
        int maxCx = Integer.MIN_VALUE;
        int minCz = Integer.MAX_VALUE;
        int maxCz = Integer.MIN_VALUE;
        for (Coord c : cells) {
            minCx = Math.min(minCx, c.getX());
            maxCx = Math.max(maxCx, c.getX());
            minCz = Math.min(minCz, c.getZ());
            maxCz = Math.max(maxCz, c.getZ());
        }
        int oy = grid.getOriginY();
        int minX = minCx * CHUNK + MARGIN;
        int minZ = minCz * CHUNK + MARGIN;
        int maxX = maxCx * CHUNK + CHUNK - 1 - MARGIN;
        int maxZ = maxCz * CHUNK + CHUNK - 1 - MARGIN;
        Location min = new Location(world, minX, oy, minZ);
        Location max = new Location(world, maxX, oy + HEIGHT - 1, maxZ);
        return new Location[]{min, max};
    }

    /** @return the world centre of {@code room}'s floor, lifted by {@code yOffset}. */
    private Location boxCenter(DungeonGrid grid, RoomNode room, int yOffset) {
        int cx = (room.getRoomMin().getBlockX() + room.getRoomMax().getBlockX()) / 2;
        int cz = (room.getRoomMin().getBlockZ() + room.getRoomMax().getBlockZ()) / 2;
        return new Location(world, cx + 0.5, grid.getOriginY() + yOffset, cz + 0.5);
    }

    /**
     * Computes the connection boxes between {@code from} and its neighbour in
     * direction (dx, dz): a solid bridge spanning both shared walls, the tunnel
     * carved straight through it (through both walls and the gap), and the
     * tunnel slice in the new room's wall (for sealing it later).
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

        int corrHalf = CORRIDOR_WIDTH / 2;     // bridge half-width (2)
        int doorHalf = DOOR_WIDTH / 2;         // tunnel/door half-width (1)
        int bridgeTop = oy + CORRIDOR_HEIGHT - 1;
        int tunnelTop = oy + DOOR_HEIGHT;      // tunnel spans oy+1 .. oy+DOOR_HEIGHT

        if (dz == 0) {
            // East/West: span along X, centred on Z.
            int zc = fMinZ + FOOT / 2;
            int fWallX = (dx == 1) ? fMaxX : fMinX;
            int tWallX = (dx == 1) ? tMinX : tMaxX;
            int loX = Math.min(fWallX, tWallX);
            int hiX = Math.max(fWallX, tWallX);
            return new Connection(to,
                    new Location(world, loX, oy, zc - corrHalf),
                    new Location(world, hiX, bridgeTop, zc + corrHalf),
                    new Location(world, loX, oy + 1, zc - doorHalf),
                    new Location(world, hiX, tunnelTop, zc + doorHalf),
                    new Location(world, tWallX, oy + 1, zc - doorHalf),
                    new Location(world, tWallX, tunnelTop, zc + doorHalf));
        } else {
            // North/South: span along Z, centred on X.
            int xc = fMinX + FOOT / 2;
            int fWallZ = (dz == 1) ? fMaxZ : fMinZ;
            int tWallZ = (dz == 1) ? tMinZ : tMaxZ;
            int loZ = Math.min(fWallZ, tWallZ);
            int hiZ = Math.max(fWallZ, tWallZ);
            return new Connection(to,
                    new Location(world, xc - corrHalf, oy, loZ),
                    new Location(world, xc + corrHalf, bridgeTop, hiZ),
                    new Location(world, xc - doorHalf, oy + 1, loZ),
                    new Location(world, xc + doorHalf, tunnelTop, hiZ),
                    new Location(world, xc - doorHalf, oy + 1, tWallZ),
                    new Location(world, xc + doorHalf, tunnelTop, tWallZ));
        }
    }

    // --- block operations (queued) ----------------------------------------

    /** Lays a 3x3 emerald checkpoint pad at {@code room}'s floor centre. */
    private void placeEmerald(DungeonGrid grid, RoomNode room) {
        int cx = (room.getRoomMin().getBlockX() + room.getRoomMax().getBlockX()) / 2;
        int cz = (room.getRoomMin().getBlockZ() + room.getRoomMax().getBlockZ()) / 2;
        int oy = grid.getOriginY();
        workload.fillSolidBox(
                new Location(world, cx - 1, oy, cz - 1),
                new Location(world, cx + 1, oy, cz + 1),
                GOAL_MARKER);
        grid.setEmeraldLocation(new Location(world, cx, oy, cz));
    }

    /** Restores the 3x3 emerald pad back to floor once a checkpoint is consumed. */
    private void removeEmerald(DungeonGrid grid, RoomNode room) {
        int cx = (room.getRoomMin().getBlockX() + room.getRoomMax().getBlockX()) / 2;
        int cz = (room.getRoomMin().getBlockZ() + room.getRoomMax().getBlockZ()) / 2;
        int oy = grid.getOriginY();
        workload.fillSolidBox(
                new Location(world, cx - 1, oy, cz - 1),
                new Location(world, cx + 1, oy, cz + 1),
                FLOOR_MATERIAL);
    }

    /**
     * Mounts a pair of wall torches against the room wall, flanking the doorway
     * on the {@code exitDir} side and facing into the room.
     */
    private void placeDoorTorches(DungeonGrid grid, Coord cell, int[] exitDir) {
        int torchY = grid.getOriginY() + 2;
        int flank = CORRIDOR_WIDTH / 2; // sit on the stone-brick door-frame edge
        int dx = exitDir[0];
        int dz = exitDir[1];
        int minX = roomMinX(cell);
        int maxX = minX + FOOT - 1;
        int minZ = roomMinZ(cell);
        int maxZ = minZ + FOOT - 1;
        UUID w = world.getUID();

        if (dx != 0) {
            int wallX = dx > 0 ? maxX : minX;
            int torchX = wallX - dx; // one block into the room, against the wall
            int zc = centerZ(cell);
            BlockFace facing = dx > 0 ? BlockFace.WEST : BlockFace.EAST;
            workloadQueue.addWorkload(new WallTorchWorkload(w, torchX, torchY, zc - flank, facing));
            workloadQueue.addWorkload(new WallTorchWorkload(w, torchX, torchY, zc + flank, facing));
        } else {
            int wallZ = dz > 0 ? maxZ : minZ;
            int torchZ = wallZ - dz;
            int xc = centerX(cell);
            BlockFace facing = dz > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            workloadQueue.addWorkload(new WallTorchWorkload(w, xc - flank, torchY, torchZ, facing));
            workloadQueue.addWorkload(new WallTorchWorkload(w, xc + flank, torchY, torchZ, facing));
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

    /**
     * Immutable bundle of the boxes that make up a room-to-room connection: a
     * solid stone-brick {@code bridge} spanning both rooms' shared walls, the
     * {@code tunnel} carved straight through it, and {@code toDoor} (the tunnel
     * slice in the new room's wall, used to seal it later).
     */
    private static final class Connection {
        private final Coord to;
        private final Location bridgeMin;
        private final Location bridgeMax;
        private final Location tunnelMin;
        private final Location tunnelMax;
        private final Location toDoorMin;
        private final Location toDoorMax;

        private Connection(Coord to,
                           Location bridgeMin, Location bridgeMax,
                           Location tunnelMin, Location tunnelMax,
                           Location toDoorMin, Location toDoorMax) {
            this.to = to;
            this.bridgeMin = bridgeMin;
            this.bridgeMax = bridgeMax;
            this.tunnelMin = tunnelMin;
            this.tunnelMax = tunnelMax;
            this.toDoorMin = toDoorMin;
            this.toDoorMax = toDoorMax;
        }
    }
}
