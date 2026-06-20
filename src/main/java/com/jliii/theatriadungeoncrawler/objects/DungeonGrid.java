package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import com.jliii.theatriadungeoncrawler.util.Coord;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent, sliding state of an infinite dungeon.
 *
 * <p>Rooms are aligned to Minecraft chunks: one room per chunk cell. The grid
 * remembers which chunk cells are currently occupied (so new rooms never
 * overlap existing ones or each other), the ordered path of active rooms (the
 * "snake body" that grows at the head and is trimmed at the tail), and the
 * world location of the current emerald checkpoint.</p>
 *
 * <p>Occupancy is tracked per chunk on a single Y plane ({@link #getOriginY()}).
 * The dungeon never grows without bound: when the player passes a checkpoint the
 * rooms behind it are removed and their chunks freed for reuse.</p>
 */
public class DungeonGrid {

    private final World world;
    private final int originY;
    private final DungeonTemplate.DungeonType theme;
    private final int fixedSegmentLength;

    private final Set<Coord> occupiedChunks = new HashSet<>();
    private final Deque<RoomNode> path = new ArrayDeque<>();

    private Location spawn;
    private Location emeraldLocation;
    private String lastExitDirection;

    public DungeonGrid(World world, int originY, DungeonTemplate.DungeonType theme, int fixedSegmentLength) {
        this.world = world;
        this.originY = originY;
        this.theme = theme;
        this.fixedSegmentLength = fixedSegmentLength;
    }

    public World getWorld() {
        return world;
    }

    public int getOriginY() {
        return originY;
    }

    /** @return the fixed theme, or {@code null} to use a random theme per room. */
    public DungeonTemplate.DungeonType getTheme() {
        return theme;
    }

    /** @return a forced segment length, or {@code <= 0} to use a random 7-15. */
    public int getFixedSegmentLength() {
        return fixedSegmentLength;
    }

    public boolean isOccupied(Coord chunk) {
        return occupiedChunks.contains(chunk);
    }

    public void markOccupied(Coord chunk) {
        occupiedChunks.add(chunk);
    }

    public void freeChunk(Coord chunk) {
        occupiedChunks.remove(chunk);
    }

    public Set<Coord> getOccupiedChunks() {
        return occupiedChunks;
    }

    public Deque<RoomNode> getPath() {
        return path;
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public Location getEmeraldLocation() {
        return emeraldLocation == null ? null : emeraldLocation.clone();
    }

    public void setEmeraldLocation(Location emeraldLocation) {
        this.emeraldLocation = emeraldLocation;
    }

    /** @return the cardinal direction the most recent checkpoint opened toward. */
    public String getLastExitDirection() {
        return lastExitDirection;
    }

    public void setLastExitDirection(String lastExitDirection) {
        this.lastExitDirection = lastExitDirection;
    }

    /**
     * @return {@code true} if the player is standing on (or in) the current
     *         emerald checkpoint block.
     */
    public boolean isOnEmerald(Location loc) {
        if (loc == null || emeraldLocation == null || loc.getWorld() == null) {
            return false;
        }
        if (emeraldLocation.getWorld() == null || !loc.getWorld().equals(emeraldLocation.getWorld())) {
            return false;
        }
        int ey = emeraldLocation.getBlockY();
        return loc.getBlockX() == emeraldLocation.getBlockX()
                && loc.getBlockZ() == emeraldLocation.getBlockZ()
                && (loc.getBlockY() == ey || loc.getBlockY() == ey + 1);
    }
}
