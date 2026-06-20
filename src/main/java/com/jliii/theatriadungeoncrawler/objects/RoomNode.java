package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import org.bukkit.Location;

/**
 * One room in the active dungeon path (the "snake body").
 *
 * <p>Holds the chunk cell the room occupies and the world-space boxes needed to
 * tear it down later: the room itself, the corridor that connects it to its
 * predecessor, and the doorway carved into this room's wall on the incoming
 * side (so it can be sealed when the player passes through).</p>
 */
public class RoomNode {

    private final Coord chunk;
    private final DungeonTemplate.DungeonType theme;
    private final Location roomMin;
    private final Location roomMax;

    private Location corridorMin;
    private Location corridorMax;

    private final Location doorMin;
    private final Location doorMax;

    public RoomNode(Coord chunk, DungeonTemplate.DungeonType theme,
                    Location roomMin, Location roomMax,
                    Location corridorMin, Location corridorMax,
                    Location doorMin, Location doorMax) {
        this.chunk = chunk;
        this.theme = theme;
        this.roomMin = roomMin;
        this.roomMax = roomMax;
        this.corridorMin = corridorMin;
        this.corridorMax = corridorMax;
        this.doorMin = doorMin;
        this.doorMax = doorMax;
    }

    public Coord getChunk() {
        return chunk;
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
}
