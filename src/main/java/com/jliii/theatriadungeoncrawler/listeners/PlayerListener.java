package com.jliii.theatriadungeoncrawler.listeners;

import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.Room;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

public class PlayerListener implements Listener {


    DungeonMaster dungeonMaster;

    public PlayerListener(DungeonMaster dungeonMaster) {
        this.dungeonMaster = dungeonMaster;
    }


    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        for (Dungeon dungeon : dungeonMaster.getDungeons()) {
            for (Room room : dungeon.getRooms()) {
                List<Entity> remainingMobs = room.getSpawnedMobs();
                if (room.getSpawnedMobs() == null) continue;
                player.sendMessage(String.valueOf(room.getSpawnedMobs().size()));
                if (remainingMobs.contains(event.getEntity())) {
                    player.sendMessage("You killed a mob in a dungeon.");
                    remainingMobs.remove(event.getEntity());
                }
            }
        }
    }


}
