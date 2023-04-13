package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.util.runnables.TaskScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DungeonFactory {

    //This creates dungeons that will have a dungeon state
    //things like current room, current floor, current level, etc.
    //will be stored in the dungeon state
    //this will also be where the dungeon state will be created
    //state should be accessible from other parts of the plugin to determine behavior

    //dungeon factory -> dungeon & dungeon-state
    //should dungeons have access to the rooms they contain?
    //should the information be obtainable by the dungeon?

    private final TaskScheduler taskScheduler;
    private List<Dungeon> dungeons = new ArrayList<>();

    public DungeonFactory(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public UUID CreateDungeon() {
        Dungeon dungeon = new Dungeon();
        BukkitTask task = taskScheduler.scheduleTask(dungeon.getWorkloadRunnable(), 1, 1);
        dungeon.setTaskId(task.getTaskId());
        dungeons.add(dungeon);
        return dungeon.getUUID();
    }

}
