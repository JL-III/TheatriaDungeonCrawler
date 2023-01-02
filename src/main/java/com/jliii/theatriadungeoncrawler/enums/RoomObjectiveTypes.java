package com.jliii.theatriadungeoncrawler.enums;

public enum RoomObjectiveTypes {

    //players must make it to the target block in order to open the door without dying.
    PARKOUR,

    MOB_KILL,
    BOSS_KILL,
    MINI_BOSS_KILL,
    //solve the puzzle to continue
    PUZZLE,
    //Survive is a wave based room where players have to survive a specific time limit.
    SURVIVE,
    //The floor collapses on specific blocks if a player steps on them
    FLOOR_COLLAPSE,
    TRAP_ROOM,
    ESCAPE_ROOM,
    //can we emulate portal with this??
    PORTAL_ROOM,
    //Strange block interaction room, players will have different abilities based on the block they are touching
    FUN_HOUSE

}
