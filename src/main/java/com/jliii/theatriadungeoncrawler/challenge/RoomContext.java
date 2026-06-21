package com.jliii.theatriadungeoncrawler.challenge;

import com.jliii.theatriadungeoncrawler.objects.Dungeon;
import com.jliii.theatriadungeoncrawler.objects.RoomNode;
import com.jliii.theatriadungeoncrawler.util.runnables.WorkloadQueue;
import org.bukkit.World;

import java.util.Random;

/**
 * Everything a {@link RoomChallenge} is allowed to touch, bundled so the
 * challenge stays decoupled from the manager. Derives the world, build queue,
 * RNG and scope from the owning {@link Dungeon} and the {@link RoomNode} it
 * belongs to, so it is cheap to construct on demand per lifecycle call.
 */
public final class RoomContext {

    private final Dungeon dungeon;
    private final RoomNode room;

    public RoomContext(Dungeon dungeon, RoomNode room) {
        this.dungeon = dungeon;
        this.room = room;
    }

    public World world() {
        return dungeon.getWorld();
    }

    public RoomNode room() {
        return room;
    }

    public Dungeon dungeon() {
        return dungeon;
    }

    /** Throttled block placement — challenge geometry must be queued, never bulk-placed. */
    public WorkloadQueue queue() {
        return dungeon.getWorkloadQueue();
    }

    /** This room's entity/task registry (cleaned up on teardown). */
    public RoomScope scope() {
        return room.getScope();
    }

    /** The instance's seeded RNG (per-instance, for reproducible runs). */
    public Random random() {
        return dungeon.getRandom();
    }
}
