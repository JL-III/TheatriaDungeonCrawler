package com.jliii.theatriadungeoncrawler.util.runnables;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

public class BlockPlacementWorkload implements Workload {

    private final UUID worldID;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final Material material;

    public BlockPlacementWorkload(UUID worldID, int blockX, int blockY, int blockZ, Material material) {
        this.worldID = worldID;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.material = material;
    }

    @Override
    public boolean compute() {
        World world = Bukkit.getWorld(this.worldID);
        Preconditions.checkState(world != null);
        // applyPhysics=false avoids physics/redundant-update cost on each block.
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).setType(this.material, false);
        return true;
    }
}
