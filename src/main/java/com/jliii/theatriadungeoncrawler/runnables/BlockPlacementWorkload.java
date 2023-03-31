package com.jliii.theatriadungeoncrawler.runnables;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

@AllArgsConstructor
public class BlockPlacementWorkload implements Workload {

    private final UUID worldID;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final Material material;

    /*
     * This method is called when the workload is executed - specifically when setting blocks.
     */

    @Override
    public void compute() {
        World world = Bukkit.getWorld(this.worldID);
        Preconditions.checkState(world != null);
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).setType(this.material);
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).getState().update(true, false);
    }


}
