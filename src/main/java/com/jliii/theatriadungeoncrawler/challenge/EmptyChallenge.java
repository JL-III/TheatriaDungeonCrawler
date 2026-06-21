package com.jliii.theatriadungeoncrawler.challenge;

import org.bukkit.entity.Player;

/**
 * A free room with no objective: every door is open and there is nothing to do.
 * It is "complete" from the moment it exists, so it never gates progression.
 *
 * <p>This is the only challenge in the initial foundation: with every room
 * EMPTY, in-game behaviour is identical to before, but the full lock/challenge
 * machinery is now in place for gated challenges to plug into.</p>
 */
public final class EmptyChallenge implements RoomChallenge {

    @Override
    public ChallengeType type() {
        return ChallengeType.EMPTY;
    }

    @Override
    public boolean isGated() {
        return false;
    }

    @Override
    public void build(RoomContext ctx) {
        // nothing to build
    }

    @Override
    public void activate(RoomContext ctx) {
        // nothing to activate
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
        // nothing to update
    }

    @Override
    public void reset(RoomContext ctx) {
        // nothing to reset
    }

    @Override
    public boolean isComplete(RoomContext ctx) {
        return true; // free rooms are always complete
    }

    @Override
    public void onComplete(RoomContext ctx) {
        // no reward
    }

    @Override
    public void teardown(RoomContext ctx) {
        // no challenge-specific blocks to restore
    }
}
