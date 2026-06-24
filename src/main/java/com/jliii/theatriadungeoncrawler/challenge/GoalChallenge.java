package com.jliii.theatriadungeoncrawler.challenge;

import com.jliii.theatriadungeoncrawler.objects.RoomNode;
import com.jliii.theatriadungeoncrawler.util.runnables.DungeonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The first gated challenge: a gold block sits in the middle of the room and the
 * forward door is sealed (by the generator) until a player stands on it. Reaching
 * the block opens the way forward.
 *
 * <p>Needs no weapons or extra event wiring — completion is detected by polling
 * player positions from {@link #tick}, so it is safe for empty-handed players.</p>
 */
public final class GoalChallenge implements RoomChallenge {

    private static final Material GOAL = Material.GOLD_BLOCK;
    private static final Material FLOOR = Material.STONE_BRICKS;

    private int goalX;
    private int goalY;
    private int goalZ;
    private boolean placed;
    private boolean complete;

    @Override
    public ChallengeType type() {
        return ChallengeType.REACH_GOAL;
    }

    @Override
    public boolean isGated() {
        return true;
    }

    @Override
    public void build(RoomContext ctx) {
        RoomNode room = ctx.room();
        Location min = room.getRoomMin();
        Location max = room.getRoomMax();
        goalX = (min.getBlockX() + max.getBlockX()) / 2;
        goalZ = (min.getBlockZ() + max.getBlockZ()) / 2;
        goalY = min.getBlockY(); // the room floor plane
        setGoalBlock(ctx, GOAL);
        placed = true;
    }

    @Override
    public void activate(RoomContext ctx) {
        announce(ctx, "The way ahead is sealed — step on the gold block to open it.");
    }

    @Override
    public void onPlayerEnter(RoomContext ctx, Player player) {
        // no per-player behaviour
    }

    @Override
    public void onPlayerLeave(RoomContext ctx, Player player) {
        // no per-player behaviour
    }

    @Override
    public void tick(RoomContext ctx) {
        if (complete) {
            return;
        }
        for (UUID id : ctx.dungeon().getPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && isOnGoal(player.getLocation())) {
                complete = true;
                return;
            }
        }
    }

    @Override
    public void reset(RoomContext ctx) {
        // nothing to re-arm; the goal block persists until solved
    }

    @Override
    public boolean isComplete(RoomContext ctx) {
        return complete;
    }

    @Override
    public void onComplete(RoomContext ctx) {
        // Consume the goal block back to floor so it does not linger or re-trigger.
        setGoalBlock(ctx, FLOOR);
    }

    @Override
    public void teardown(RoomContext ctx) {
        if (placed) {
            setGoalBlock(ctx, FLOOR);
        }
    }

    private boolean isOnGoal(Location loc) {
        return loc.getBlockX() == goalX
                && loc.getBlockZ() == goalZ
                && (loc.getBlockY() == goalY || loc.getBlockY() == goalY + 1);
    }

    private void setGoalBlock(RoomContext ctx, Material material) {
        Location at = new Location(ctx.world(), goalX, goalY, goalZ);
        new DungeonBuilder(ctx.queue()).fillSolidBox(at, at, material);
    }

    private void announce(RoomContext ctx, String message) {
        for (UUID id : ctx.dungeon().getPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
