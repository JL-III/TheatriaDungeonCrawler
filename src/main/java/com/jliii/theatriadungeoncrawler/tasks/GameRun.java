package com.jliii.theatriadungeoncrawler.tasks;

import com.jliii.theatriadungeoncrawler.enums.GameState;
import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.rooms.Room;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class GameRun {

    private final Dungeon dungeon;
    private Plugin plugin;
    private List<Integer> runnables = new ArrayList<>();
    public GameRun(Plugin plugin, Dungeon dungeon) {
        this.plugin = plugin;
        this.dungeon = dungeon;
    }

    public void Run() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dungeon.getGameState().name().equalsIgnoreCase("OFF") || dungeon.getAllPlayersInGame().isEmpty()) {
                    cancel();
                    runnables.forEach(x -> Bukkit.getScheduler().cancelTask(x));
                    plugin.getLogger().info("Cancelling runnables.");
//                    for (Integer id : runnables) {
//                        Bukkit.getScheduler().cancelTask(id);
//                        plugin.getLogger().info("Runnable with id " + id + " was stopped.");
//                    }
                    runnables = new ArrayList<>();
                    dungeon.setGameState(GameState.OFF);
                    return;
                }
                for (Player player : dungeon.getPlayersFromUUID(dungeon.getAllPlayersInGame())) {
                    for (Room room : dungeon.getRooms()) {
                        if (room.getRegion().contains(player.getLocation().getBlock().getLocation()) && !room.hasBeenEntered() && !room.isCompleted()) {
                            room.setHasBeenEntered(true);
                            runnables.add(room.runObjective(player, plugin));
                        } else if (room.hasBeenEntered() && !room.isCompleted()){
                            //if the room hasnt already been completed by some other check, like an entity death or interaction then we will check again based on
                            //a condition here, then we will mark the room as completed so that it can move on in the code

                            room.isObjectiveCompleted();
                        } else if (room.hasBeenEntered() && room.isCompleted() && !room.hasRunCompletedSequence()) {
                            player.sendMessage("This room is complete.");
                            room.setHasRunCompletedSequence(true);
                            room.openDoors();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

}
