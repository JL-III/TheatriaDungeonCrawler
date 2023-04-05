package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.runnables.WorkloadRunnable;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Dungeon2 {

    private final UUID dungeonUUID = UUID.randomUUID();
    private final WorkloadRunnable workloadRunnable = new WorkloadRunnable();
    private int taskId;
    private List<UUID> playersInDungeon = new ArrayList<>();


    public Dungeon2() {
    }

    public void addPlayer(UUID uuid) {
        playersInDungeon.add(uuid);
    }

    public List<UUID> removePlayer(UUID uuid) {
        if (uuid == null) {
            Bukkit.getLogger().warning("UUID is null in Dungeon.removePlayer");
            return new ArrayList<>(playersInDungeon);
        }

        return playersInDungeon.stream()
                .filter(playerUUID -> !playerUUID.equals(uuid))
                .collect(Collectors.toList());
    }

    public UUID getUUID() {
        return dungeonUUID;
    }

    public WorkloadRunnable getWorkloadRunnable() {
        return workloadRunnable;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getTaskId() {
        return taskId;
    }
}
