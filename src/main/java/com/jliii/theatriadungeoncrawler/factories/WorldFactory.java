package com.jliii.theatriadungeoncrawler.factories;

import com.jliii.theatriadungeoncrawler.world.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Creates and loads the void world that dungeons are generated into.
 *
 * <p>The dungeon world is a flat, empty void (see {@link VoidChunkGenerator})
 * named {@value #DUNGEON_WORLD_NAME}. It is created lazily the first time it is
 * requested; on subsequent calls the already-loaded world is returned.</p>
 *
 * <p>{@link WorldCreator#createWorld()} must be invoked on the main server
 * thread, so callers should only use this from synchronous contexts
 * (command handlers, {@code onEnable}, scheduled sync tasks).</p>
 */
public final class WorldFactory {

    public static final String DUNGEON_WORLD_NAME = "Labyrinth";

    private WorldFactory() {
    }

    /**
     * Returns the dungeon void world, creating it if it does not yet exist.
     */
    public static World getOrCreateDungeonWorld() {
        return getOrCreateVoidWorld(DUNGEON_WORLD_NAME);
    }

    /**
     * Returns the void world with the given name, creating it if necessary.
     *
     * @return the loaded world, or {@code null} if creation failed
     */
    public static World getOrCreateVoidWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }

        World world = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidChunkGenerator())
                .generateStructures(false)
                .createWorld();

        if (world != null) {
            applyDungeonGameRules(world);
        }
        return world;
    }

    /**
     * Locks the dungeon world to a stable, predictable state: permanent
     * daytime, no weather, and no ambient mob spawning (mobs are placed
     * explicitly by the dungeon logic instead).
     */
    private static void applyDungeonGameRules(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setStorm(false);
        world.setTime(6000);
        world.setSpawnLocation(0, 65, 0);
    }
}
