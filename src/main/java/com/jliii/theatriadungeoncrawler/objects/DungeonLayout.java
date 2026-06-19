package com.jliii.theatriadungeoncrawler.objects;

import org.bukkit.Location;

/**
 * The result of generating a dungeon: where the player spawns and the interior
 * region of the exit room used to detect completion.
 */
public class DungeonLayout {

    private final Location spawn;
    private final Location exitMin;
    private final Location exitMax;

    public DungeonLayout(Location spawn, Location exitMin, Location exitMax) {
        this.spawn = spawn;
        this.exitMin = exitMin;
        this.exitMax = exitMax;
    }

    public Location getSpawn() {
        return spawn.clone();
    }

    /**
     * @return {@code true} if the location lies within the exit room's interior.
     */
    public boolean isInsideExit(Location loc) {
        if (loc == null || loc.getWorld() == null || exitMin.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals(exitMin.getWorld())) {
            return false;
        }
        return loc.getBlockX() >= exitMin.getBlockX() && loc.getBlockX() <= exitMax.getBlockX()
                && loc.getBlockY() >= exitMin.getBlockY() && loc.getBlockY() <= exitMax.getBlockY()
                && loc.getBlockZ() >= exitMin.getBlockZ() && loc.getBlockZ() <= exitMax.getBlockZ();
    }
}
