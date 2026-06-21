package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.challenge.ChallengeState;
import com.jliii.theatriadungeoncrawler.challenge.RoomChallenge;
import com.jliii.theatriadungeoncrawler.challenge.RoomScope;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * One room in the active dungeon path (the "snake body").
 *
 * <p>A room occupies a rectangle of one or more chunk cells (1x1, 1x2, 2x1 or
 * 2x2). It holds those cells (for freeing occupancy on teardown) and the
 * world-space boxes needed to tear it down: the room itself, the bridge that
 * connects it to its predecessor, and the doorway slice in this room's wall on
 * the incoming side (so it can be sealed when the player passes through).</p>
 */
public class RoomNode {

    private final List<Coord> cells;
    private final DungeonTemplate.DungeonType theme;
    private final Location roomMin;
    private final Location roomMax;

    private Location corridorMin;
    private Location corridorMax;

    private final Location doorMin;
    private final Location doorMax;

    /** Direction the corridor entered this room from ({@code null} for spawn). */
    private final int[] entryDir;

    // --- challenge / lock state (set by the generator after construction) ---

    /** Stable id for this room within its run (used to tag spawned entities). */
    private int roomId;
    /** This room's entity/task registry, disposed on teardown. */
    private RoomScope scope;
    /** The behaviour/lock attached to this room. */
    private RoomChallenge challenge;
    /** Lock lifecycle state. */
    private ChallengeState state = ChallengeState.PENDING;
    /** The forward-door box to carve open when a gated room is solved. */
    private Location lockDoorMin;
    private Location lockDoorMax;

    public RoomNode(List<Coord> cells, DungeonTemplate.DungeonType theme,
                    Location roomMin, Location roomMax,
                    Location corridorMin, Location corridorMax,
                    Location doorMin, Location doorMax,
                    int[] entryDir) {
        this.cells = new ArrayList<>(cells);
        this.theme = theme;
        this.roomMin = roomMin;
        this.roomMax = roomMax;
        this.corridorMin = corridorMin;
        this.corridorMax = corridorMax;
        this.doorMin = doorMin;
        this.doorMax = doorMax;
        this.entryDir = entryDir;
    }

    public List<Coord> getCells() {
        return cells;
    }

    public DungeonTemplate.DungeonType getTheme() {
        return theme;
    }

    public Location getRoomMin() {
        return roomMin;
    }

    public Location getRoomMax() {
        return roomMax;
    }

    public Location getCorridorMin() {
        return corridorMin;
    }

    public Location getCorridorMax() {
        return corridorMax;
    }

    public boolean hasCorridor() {
        return corridorMin != null && corridorMax != null;
    }

    /** Forgets this node's incoming corridor (after it has been cleared). */
    public void clearCorridor() {
        this.corridorMin = null;
        this.corridorMax = null;
    }

    public Location getDoorMin() {
        return doorMin;
    }

    public Location getDoorMax() {
        return doorMax;
    }

    public boolean hasDoor() {
        return doorMin != null && doorMax != null;
    }

    public int[] getEntryDir() {
        return entryDir;
    }

    // --- challenge / lock accessors ---------------------------------------

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public RoomScope getScope() {
        return scope;
    }

    public void setScope(RoomScope scope) {
        this.scope = scope;
    }

    public RoomChallenge getChallenge() {
        return challenge;
    }

    public void setChallenge(RoomChallenge challenge) {
        this.challenge = challenge;
    }

    public ChallengeState getState() {
        return state;
    }

    public void setState(ChallengeState state) {
        this.state = state;
    }

    public Location getLockDoorMin() {
        return lockDoorMin;
    }

    public Location getLockDoorMax() {
        return lockDoorMax;
    }

    public void setLockDoor(Location lockDoorMin, Location lockDoorMax) {
        this.lockDoorMin = lockDoorMin;
        this.lockDoorMax = lockDoorMax;
    }

    public boolean hasLockDoor() {
        return lockDoorMin != null && lockDoorMax != null;
    }
}
