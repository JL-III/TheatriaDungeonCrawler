package com.jliii.theatriadungeoncrawler.tasks;

import com.jliii.theatriadungeoncrawler.enums.RoomObjectiveTypes;
import com.jliii.theatriadungeoncrawler.managers.DungeonMaster;
import com.jliii.theatriadungeoncrawler.objects.BossRoom;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.Room;
import com.jliii.theatriadungeoncrawler.util.GeneralUtils;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class GameRun {

    private final Dungeon dungeon;
    private Plugin plugin;
    List<Entity> spawnedMobs = new ArrayList<>();

    public GameRun(Plugin plugin, Dungeon dungeon) {
        this.plugin = plugin;
        this.dungeon = dungeon;
    }

    public void Run() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dungeon.getGameState().name().equalsIgnoreCase("ACTIVE")) {
                    cancel();
                    return;
                }
                for (Player player : dungeon.getPlayersFromUUID(dungeon.getAllPlayersInGame())) {
                    for (Room room : dungeon.getRooms()) {
                        if (room.getRegion().contains(player.getLocation().getBlock().getLocation()) && !room.isHasBeenEntered() && !room.isCompleted()) {
                            player.sendMessage("You are in the room named: " + room.getKey());
                            room.setHasBeenEntered(true);
                            switch (room.getType()) {
                                case "mob_kill": {
                                    runMobKillRoom(room, player);
                                }
                                default : {}
                            }

                        } else if (room.isHasBeenEntered() && room.getSpawnedMobs().size() < 1 && !room.isCompleted()) {
                            player.sendMessage("All the mobs have been killed in this room!");
                            room.setCompleted(true);
                            room.openDoors();

                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void runMobKillRoom(Room room, Player player) {
        if (room instanceof BossRoom) {
            MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob("SkeletalKnight").orElse(null);
            if (mob != null) {
                // spawns mob
                ActiveMob knight = mob.spawn(BukkitAdapter.adapt(room.getSpawnLocations().get(0)),1);
                // get mob as bukkit entity
                spawnedMobs.add(knight.getEntity().getBukkitEntity());
                room.setSpawnedMobs(spawnedMobs);
            }
        } else {
            for (EntityType entityType : room.getMobs()) {
                spawnedMobs.add(player.getWorld().spawnEntity(room.getSpawnLocations().get(GeneralUtils.getRandomNumber(0, room.getSpawnLocations().size() - 1)), entityType));
            }
            room.setSpawnedMobs(spawnedMobs);
        }
    }

}
