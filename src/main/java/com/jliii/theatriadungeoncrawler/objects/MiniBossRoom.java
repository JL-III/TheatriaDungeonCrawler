package com.jliii.theatriadungeoncrawler.objects;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.List;

public class MiniBossRoom extends Room {

    public MiniBossRoom(String key, List<Location> region, String parentKey, List<Location> spawnLocations, Location exitLocation, List<EntityType> mobs) {
        super(key, region, parentKey, spawnLocations, exitLocation, mobs);
    }

}
