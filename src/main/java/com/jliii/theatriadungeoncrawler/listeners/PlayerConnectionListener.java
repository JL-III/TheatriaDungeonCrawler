package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.factories.WorldFactory;
import com.jliii.theatriadungeoncrawler.managers.DungeonManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps dungeon instances consistent with player connections: removes
 * disconnecting players from their instance, and rescues anyone who logs in
 * inside a dungeon world that no longer exists.
 */
public class PlayerConnectionListener implements Listener {

    private final DungeonManager dungeonManager;

    public PlayerConnectionListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dungeonManager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A player who logged out inside an instance world (since deleted) would
        // otherwise rejoin into the void. Send them to the main world spawn and
        // out of adventure mode.
        Player player = event.getPlayer();
        if (player.getWorld().getName().startsWith(WorldFactory.INSTANCE_WORLD_PREFIX)) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            if (player.getGameMode() == GameMode.ADVENTURE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }
}
