package com.jliii.theatriadungeoncrawler.challenge;

import org.bukkit.entity.Player;

/**
 * The behaviour (and "lock") attached to a single room. Created at generation,
 * built via the workload queue, activated when players enter, ticked while
 * active, reset on a movement failure, and torn down with the room.
 *
 * <p>Implementations must spawn entities and schedule tasks only through
 * {@link RoomContext#scope()} so the sliding-window teardown can clean them up.
 * The manager drives the lifecycle; challenges never schedule themselves.</p>
 */
public interface RoomChallenge {

    ChallengeType type();

    /**
     * @return {@code true} for a gated room (its forward door stays sealed until
     *         {@link #isComplete}); {@code false} for a free room (door open at
     *         build time).
     */
    boolean isGated();

    /**
     * Places challenge geometry/decor (queued). No live entities yet — the room
     * may be built far ahead of the player.
     */
    void build(RoomContext ctx);

    /**
     * The first player crosses into the room: spawn mobs, start timers, show the
     * objective. Idempotent — called once per activation, not once per player.
     */
    void activate(RoomContext ctx);

    /** A player enters the room's bounds (per-player effects, presence). */
    void onPlayerEnter(RoomContext ctx, Player player);

    /** A player leaves the room's bounds. */
    void onPlayerLeave(RoomContext ctx, Player player);

    /** Periodic update while the room is {@link ChallengeState#ACTIVE}. */
    void tick(RoomContext ctx);

    /**
     * Re-arms the challenge after a movement failure (refill removed blocks,
     * reset a lava timer, clear partial progress). The manager has already
     * teleported the failing player back to the room entrance.
     */
    void reset(RoomContext ctx);

    boolean isComplete(RoomContext ctx);

    /** Runs once on completion: grant rewards; the manager opens the forward door. */
    void onComplete(RoomContext ctx);

    /**
     * Cleans up challenge-specific blocks. Always called on teardown/disposal,
     * even if the room was never activated. Tracked entities and tasks are
     * disposed separately via {@link RoomContext#scope()}.
     */
    void teardown(RoomContext ctx);
}
