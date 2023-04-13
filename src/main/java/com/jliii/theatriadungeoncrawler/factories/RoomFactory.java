package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.util.runnables.DistributedWorkload;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadRunnable;
import com.jliii.theatriadungeoncrawler.templates.DungeonTemplate;

public class RoomFactory {

    private final WorkloadRunnable workloadRunnable;

    public RoomFactory(WorkloadRunnable workloadRunnable) {
        this.workloadRunnable = workloadRunnable;
    }


    //This creates rooms based on dynamic templates
    //TODO where are these locations coming from?

    public void createRoom() {
        new DistributedWorkload(this.workloadRunnable).createRoom(cornerA, cornerB, DungeonTemplate.getRandomTheme());
    }

}
