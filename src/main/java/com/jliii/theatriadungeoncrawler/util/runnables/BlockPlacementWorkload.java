package com.jliii.theatriadungeoncrawler.util.runnables;

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
//    private Player player;

    /*
     * This method is called when the workload is executed - specifically when setting blocks.
     */

    @Override
    public void compute() {
        World world = Bukkit.getWorld(this.worldID);
        Preconditions.checkState(world != null);
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).setType(this.material);
        world.getBlockAt(this.blockX, this.blockY, this.blockZ).getState().update(true, false);
//        player.sendMessage("Block placed at " + this.blockX + ", " + this.blockY + ", " + this.blockZ);
//        Location nextPoint = new Location(world, this.blockX, this.blockY, this.blockZ);
//
//        for (int i = 0; i <= 4; i++) {
//            for (int dx = -1; dx <= 1; dx++) {
//                for (int dz = -1; dz <= 1; dz++) {
//                    Location loc = nextPoint.clone().add(dx, i, dz);
//                    player.sendMessage("Blacklisted x:" + loc.getX() + " y:" + loc.getY() + " z:" + loc.getZ());
//                }
//            }
//        }
    }


}
