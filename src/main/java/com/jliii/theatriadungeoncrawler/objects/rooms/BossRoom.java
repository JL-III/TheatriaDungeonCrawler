package com.jliii.theatriadungeoncrawler.objects.rooms;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.List;

public class BossRoom extends Room {

    public BossRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        super(key, region, parentKey, spawnLocations, exitLocation, mobs);
    }

    //                MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob("SkeletalKnight").orElse(null);
//                if (mob != null) {
//                    // spawns mob
//                    ActiveMob knight = mob.spawn(BukkitAdapter.adapt(getSpawnLocations().get(0)),1);
//                    // get mob as bukkit entity
//                    spawnedMobs.add(knight.getEntity().getBukkitEntity());
//                    room.setSpawnedMobs(spawnedMobs);
//                }

}
