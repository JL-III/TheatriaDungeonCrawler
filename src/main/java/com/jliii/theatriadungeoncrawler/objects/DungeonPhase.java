package com.jliii.theatriadungeoncrawler.objects;

import com.jliii.theatriadungeoncrawler.objects.rooms.Room;

import java.util.List;
import java.util.UUID;

public class DungeonPhase {

    //Store respawn points for phase
    //Maybe store the number of rooms this phase will contain to track phase progression
    //Or will store the rooms themselves with their properties. Not certain if we want to predetermine the rooms all at phase creation or one room after another
    //will contain a state that will be used to determine if the phase is complete

    private final List<Room> rooms;
    private final SafeRoom safeRoom;
    private final UUID phaseUUID;
    private boolean isComplete;

    public DungeonPhase(List<Room> rooms, SafeRoom safeRoom) {
        this.rooms = rooms;
        this.safeRoom = safeRoom;
        this.isComplete = false;
        phaseUUID = UUID.randomUUID();
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public SafeRoom getSafeRoom() {
        return safeRoom;
    }

    public boolean isComplete() {
        // Check if all rooms in the phase have been completed
        return isComplete;
    }

    public void setComplete() {
        isComplete = true;
    }

    public UUID getPhaseUUID() {
        return phaseUUID;
    }

}
