package com.jliii.theatriadungeoncrawler.util.runnables;

import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

/**
 * Fills a box of blocks a batch at a time, resuming across ticks until done.
 *
 * <p>Enqueuing one of these per region (a room, a corridor, a teardown) is far
 * cheaper than pre-creating one {@link BlockPlacementWorkload} per block, which
 * spiked the main thread when a whole segment was built or removed at once.</p>
 */
public class BatchedRegionWorkload implements Workload {

    /** Block positions visited per {@link #compute()} call. Kept small so a
     *  single compute stays well under the per-tick budget for fine control. */
    private static final int BATCH = 256;

    private final UUID worldID;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    /** When true only the outer shell is placed; otherwise the whole volume. */
    private final boolean shellOnly;
    /** Fixed material, or {@code null} to pick a random material from {@link #theme}. */
    private final Material material;
    private final DungeonTemplate.DungeonType theme;

    private int x;
    private int y;
    private int z;
    private boolean started;
    private boolean done;

    public BatchedRegionWorkload(UUID worldID, Location a, Location b, boolean shellOnly,
                                 Material material, DungeonTemplate.DungeonType theme) {
        this.worldID = worldID;
        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        this.shellOnly = shellOnly;
        this.material = material;
        this.theme = theme;
    }

    @Override
    public boolean compute() {
        World world = Bukkit.getWorld(this.worldID);
        if (world == null) {
            return true; // world disposed; nothing left to do
        }
        if (!started) {
            x = minX;
            y = minY;
            z = minZ;
            started = true;
        }

        int processed = 0;
        while (!done && processed < BATCH) {
            boolean edge = x == minX || x == maxX
                    || y == minY || y == maxY
                    || z == minZ || z == maxZ;
            if (!shellOnly || edge) {
                Material mat = material != null ? material : DungeonTemplate.getRandomMaterial(theme);
                // applyPhysics=true so lighting recalculates (rooms stay lit).
                world.getBlockAt(x, y, z).setType(mat, true);
            }
            processed++;
            advanceCursor();
        }
        return done;
    }

    private void advanceCursor() {
        z++;
        if (z > maxZ) {
            z = minZ;
            y++;
            if (y > maxY) {
                y = minY;
                x++;
                if (x > maxX) {
                    done = true;
                }
            }
        }
    }
}
