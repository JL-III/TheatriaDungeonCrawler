package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadQueue;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A single, isolated dungeon instance: its own void world, generated layout,
 * the players inside it, and the lifecycle {@link State} it is in.
 *
 * <p>Each instance owns a {@link WorkloadQueue} so its world can build
 * independently of every other instance, and remembers where each player came
 * from so they can be returned when the instance is disposed.</p>
 */
public class Dungeon {

    /** Shared lives the party starts a run with. */
    public static final int LIVES_PER_RUN = 3;

    private final UUID dungeonUUID = UUID.randomUUID();
    private final WorkloadQueue workloadQueue;
    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    private final World world;
    private final int fixedSegmentLength;
    private final DungeonTemplate.DungeonType theme;

    /** Per-instance seeded RNG: isolates instances and enables shareable seeds. */
    private final long seed = new Random().nextLong();
    private final Random random = new Random(seed);

    private final long runStartMillis = System.currentTimeMillis();
    /** Shared life pool for the run (co-op); the run ends when it hits zero. */
    private int livesRemaining = LIVES_PER_RUN;
    /** Rooms entered so far — the dominant term in the run score. */
    private int depth = 0;
    /** Running score bonus from rewards (depth + time are computed at run end). */
    private long scoreBonus = 0;

    private DungeonGrid grid;
    private State state = State.STARTING;
    private int buildTaskId = -1;
    private boolean extending = false;

    public Dungeon(World world, int fixedSegmentLength, DungeonTemplate.DungeonType theme) {
        this(world, fixedSegmentLength, theme, new WorkloadQueue());
    }

    /** Lets a caller (e.g. the debug {@code /box} command) supply a shared queue. */
    public Dungeon(World world, int fixedSegmentLength, DungeonTemplate.DungeonType theme, WorkloadQueue workloadQueue) {
        this.world = world;
        this.fixedSegmentLength = fixedSegmentLength;
        this.theme = theme;
        this.workloadQueue = workloadQueue;
    }

    public void addPlayer(UUID uuid, Location returnLocation) {
        if (!players.contains(uuid)) {
            players.add(uuid);
        }
        returnLocations.put(uuid, returnLocation);
    }

    /**
     * Removes a player from this instance.
     *
     * @return the location the player should be returned to, or {@code null}
     *         if they were not in this instance
     */
    public Location removePlayer(UUID uuid) {
        players.remove(uuid);
        return returnLocations.remove(uuid);
    }

    public List<UUID> getPlayers() {
        return new ArrayList<>(players);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public Location getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    public UUID getUUID() {
        return dungeonUUID;
    }

    public World getWorld() {
        return world;
    }

    public int getFixedSegmentLength() {
        return fixedSegmentLength;
    }

    public DungeonTemplate.DungeonType getTheme() {
        return theme;
    }

    public WorkloadQueue getWorkloadQueue() {
        return workloadQueue;
    }

    public Random getRandom() {
        return random;
    }

    public long getSeed() {
        return seed;
    }

    public long getRunStartMillis() {
        return runStartMillis;
    }

    public int getLivesRemaining() {
        return livesRemaining;
    }

    /** Spends one shared life. @return the lives left after spending. */
    public int decrementLife() {
        if (livesRemaining > 0) {
            livesRemaining--;
        }
        return livesRemaining;
    }

    public int getDepth() {
        return depth;
    }

    public void incrementDepth() {
        depth++;
    }

    public long getScoreBonus() {
        return scoreBonus;
    }

    public void addScoreBonus(long amount) {
        scoreBonus += amount;
    }

    public DungeonGrid getGrid() {
        return grid;
    }

    public void setGrid(DungeonGrid grid) {
        this.grid = grid;
    }

    public boolean isExtending() {
        return extending;
    }

    public void setExtending(boolean extending) {
        this.extending = extending;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getBuildTaskId() {
        return buildTaskId;
    }

    public void setBuildTaskId(int buildTaskId) {
        this.buildTaskId = buildTaskId;
    }
}
