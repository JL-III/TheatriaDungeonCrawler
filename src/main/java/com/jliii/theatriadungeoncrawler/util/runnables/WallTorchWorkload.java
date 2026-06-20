package com.jliii.theatriadungeoncrawler.util.runnables;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import java.util.UUID;

/**
 * Places a wall torch facing a given direction. Physics are disabled so the
 * torch does not pop off if its supporting wall is built in the same batch.
 */
public class WallTorchWorkload implements Workload {

    private final UUID worldID;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final BlockFace facing;

    public WallTorchWorkload(UUID worldID, int blockX, int blockY, int blockZ, BlockFace facing) {
        this.worldID = worldID;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.facing = facing;
    }

    @Override
    public boolean compute() {
        World world = Bukkit.getWorld(this.worldID);
        Preconditions.checkState(world != null);
        Block block = world.getBlockAt(this.blockX, this.blockY, this.blockZ);
        block.setType(Material.WALL_TORCH, false);
        BlockData data = block.getBlockData();
        if (data instanceof Directional) {
            ((Directional) data).setFacing(this.facing);
            block.setBlockData(data, false);
        }
        return true;
    }
}
