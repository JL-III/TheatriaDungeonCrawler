package com.jliii.theatriadungeoncrawler.runnables;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

@AllArgsConstructor
public class DistributedFiller {

    private final WorkloadRunnable workloadRunnable;


    public void fillHollowBox(Location cornerA, Location cornerB, Material material) {
        Preconditions.checkArgument(cornerA.getWorld() == cornerB.getWorld() && cornerA.getWorld() != null);
        BoundingBox box = BoundingBox.of(cornerA.getBlock(), cornerB.getBlock());
        Vector max = box.getMax();
        Vector min = box.getMin();
        Bukkit.getConsoleSender().sendMessage("inside fill hollow box");

        World world = cornerA.getWorld();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    boolean isEdge =
                            x == min.getBlockX() || x == max.getBlockX()
                                    || y == min.getBlockY() || y == max.getBlockY()
                                    || z == min.getBlockZ() || z == max.getBlockZ();

                    if (isEdge) {
                        BlockPlacementWorkload blockPlacementWorkload = new BlockPlacementWorkload(world.getUID(), x, y, z, material);
                        this.workloadRunnable.addWorkload(blockPlacementWorkload);
                    }
                }
            }
        }
    }

}
