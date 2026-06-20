package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.enums.State;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadRunnable;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single, isolated dungeon instance: its own void world, generated layout,
 * the players inside it, and the lifecycle {@link State} it is in.
 *
 * <p>Each instance owns a {@link WorkloadRunnable} so its world can build
 * independently of every other instance, and remembers where each player came
 * from so they can be returned when the instance is disposed.</p>
 */
public class Dungeon {

    private final UUID dungeonUUID = UUID.randomUUID();
    private final WorkloadRunnable workloadRunnable = new WorkloadRunnable();
    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    private final World world;
    private final int fixedSegmentLength;
    private final DungeonTemplate.DungeonType theme;

    private DungeonGrid grid;
    private State state = State.STARTING;
    private int buildTaskId = -1;
    private boolean extending = false;

    public Dungeon(World world, int fixedSegmentLength, DungeonTemplate.DungeonType theme) {
        this.world = world;
        this.fixedSegmentLength = fixedSegmentLength;
        this.theme = theme;
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

    public WorkloadRunnable getWorkloadRunnable() {
        return workloadRunnable;
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
