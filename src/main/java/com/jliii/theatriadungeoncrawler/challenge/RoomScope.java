package com.jliii.theatriadungeoncrawler.challenge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-room registry of everything a challenge spawns or schedules, so it can all
 * be cleaned up in one call when the room is torn down (sliding window) or the
 * instance is disposed.
 *
 * <p>Without this, the sliding-window teardown — which only clears blocks — would
 * orphan live mobs and leave room timers (e.g. a lava wave) running forever.
 * Challenges must spawn entities and schedule tasks <em>through</em> their scope,
 * never directly via {@link World#spawnEntity} or the Bukkit scheduler.</p>
 */
public final class RoomScope {

    private final Plugin plugin;
    private final World world;
    private final List<UUID> entities = new ArrayList<>();
    private final List<Integer> taskIds = new ArrayList<>();
    private boolean disposed;

    public RoomScope(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /**
     * Spawns and tracks an entity. {@code init} runs once on the fresh entity
     * (set name, equipment, AI, PDC tags, …).
     *
     * @return the spawned entity, or {@code null} if the scope is already disposed
     */
    public Entity spawn(Location location, EntityType type, Consumer<Entity> init) {
        if (disposed) {
            return null;
        }
        Entity entity = world.spawnEntity(location, type);
        if (init != null) {
            init.accept(entity);
        }
        entities.add(entity.getUniqueId());
        return entity;
    }

    /**
     * Schedules and tracks a repeating task.
     *
     * @return the Bukkit task id, or {@code -1} if the scope is already disposed
     */
    public int schedule(Runnable task, long delayTicks, long periodTicks) {
        if (disposed) {
            return -1;
        }
        int id = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
        taskIds.add(id);
        return id;
    }

    /** Cancels every tracked task and removes every tracked entity. Idempotent. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (int id : taskIds) {
            Bukkit.getScheduler().cancelTask(id);
        }
        taskIds.clear();
        for (UUID id : entities) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        entities.clear();
    }

    public boolean isDisposed() {
        return disposed;
    }
}
