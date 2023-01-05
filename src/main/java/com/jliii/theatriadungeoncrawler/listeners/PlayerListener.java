package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;

public class PlayerListener implements Listener {


    DungeonMaster dungeonMaster;

    public PlayerListener(DungeonMaster dungeonMaster) {
        this.dungeonMaster = dungeonMaster;
    }

    @EventHandler
    public void OnDeath(PlayerDeathEvent event) {
        event.getPlayer().getInventory().clear();
        event.getDrops().clear();
    }

    @EventHandler
    public void OnRespawn(PlayerRespawnEvent event) {
        for (Dungeon dungeon : dungeonMaster.getDungeons()) {
            if (dungeon.getPlayersFromUUID(dungeon.getAllPlayersInGame()).contains(event.getPlayer())) {
                event.setRespawnLocation(dungeon.getSpawnLocations().get(GeneralUtils.getRandomNumber(0, dungeon.getSpawnLocations().size() - 1)));
                //TODO what is this doing???
                event.getPlayer().getInventory().addItem();

                if (dungeon.getGameState().name().equalsIgnoreCase("active")) {
//                    PlayerKit.GivePlayerKit(event.getPlayer());
                }
            }
        }

    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        for (Dungeon dungeon : dungeonMaster.getDungeons()) {
            for (Room room : dungeon.getRooms()) {
                List<Entity> remainingMobs = room.getSpawnedMobs();
                if (room.getSpawnedMobs() == null) continue;
                player.sendMessage(String.valueOf("Remaining mobs in the room: " + room.getSpawnedMobs().size()));
                if (remainingMobs.contains(event.getEntity())) {
                    player.sendMessage("You killed a mob in a dungeon.");
                    remainingMobs.remove(event.getEntity());
                }
            }
        }
    }

    @EventHandler
    public void OnPlayerDisconnect(PlayerQuitEvent event) {
        if (dungeonMaster.getDungeonPartyManager().getPlayerGameMap().get(event.getPlayer().getUniqueId()) == null) return;
        event.getPlayer().teleport(dungeonMaster.getDungeonByKey(dungeonMaster.getDungeonPartyManager().getPlayerGameMap().get(event.getPlayer().getUniqueId())).getSignLocations().get(0));
        dungeonMaster.getDungeonPartyManager().getPlayerGameMap().remove(event.getPlayer().getUniqueId());
        dungeonMaster.updateSigns();
    }


}
