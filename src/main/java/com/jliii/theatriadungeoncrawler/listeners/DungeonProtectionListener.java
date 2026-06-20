package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Protects players while they are inside a dungeon: they cannot break or place
 * the dungeon's blocks, and a death ends the run by respawning them out in the
 * main world. Items and experience are kept by the world's keep-inventory game
 * rule (see {@code WorldFactory}), so no per-death handling is needed here.
 */
public class DungeonProtectionListener implements Listener {

    private final DungeonManager dungeonManager;

    public DungeonProtectionListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
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
