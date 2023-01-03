package com.jliii.theatriadungeoncrawler.tasks;

import com.jliii.theatriadungeoncrawler.enums.GameState;
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
    public GameRun(Plugin plugin, Dungeon dungeon) {
        this.plugin = plugin;
        this.dungeon = dungeon;
    }

    public void Run() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dungeon.getGameState().name().equalsIgnoreCase("ACTIVE") || dungeon.getAllPlayersInGame().isEmpty()) {
                    cancel();
                    dungeon.setGameState(GameState.OFF);
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
                                case "parkour": {
                                    parkourRunnable(player, room);
                                }
                                default : {}
                            }

                        } else if (room.isHasBeenEntered() && !room.isCompleted()){
                            checkIfObjectiveIsCompleted(room);
                        } else if (room.isHasBeenEntered() && room.isCompleted() && !room.hasRunCompletedSequence()) {
                            player.sendMessage("This room is complete.");
                            room.setHasRunCompletedSequence(true);
                            room.openDoors();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void checkIfObjectiveIsCompleted(Room room) {

        switch (room.getType()) {
            case "mob_kill": {
                //Condition to check when determining if the rooms objective has been completed
                if (room.getSpawnedMobs().size() < 1) {
                    room.setCompleted(true);
                }
            }
            case "parkour": {
                if (room.getBlockReached()) {
                    room.setCompleted(true);
                }
            }
            default: {

            }
        }

    }

    public void parkourRunnable(Player player, Room room) {
        new BukkitRunnable(){

            @Override
            public void run(){
                if (player.getLocation().add(0, -1, 0).getBlock().getType() == Material.GOLD_BLOCK) {
                    room.setBlockReached(true);
                    cancel();
                }
            }

        }.runTaskTimer(plugin, 0, 10);
    }

    private void runMobKillRoom(Room room, Player player) {
        List<Entity> spawnedMobs = new ArrayList<>();
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
