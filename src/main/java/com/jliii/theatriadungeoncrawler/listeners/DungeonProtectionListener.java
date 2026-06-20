package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Protects players while they are inside a dungeon: they keep their items and
 * experience on death (the run ends and they return to the main world with
 * their loot), and they cannot break or place the dungeon's blocks.
 */
public class DungeonProtectionListener implements Listener {

    private final DungeonManager dungeonManager;

    public DungeonProtectionListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!dungeonManager.isParticipant(event.getEntity())) {
            return;
        }
        // Keep everything: no dropped items, no lost levels.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location respawn = dungeonManager.handleDeath(event.getPlayer());
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (dungeonManager.isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (dungeonManager.isParticipant(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
