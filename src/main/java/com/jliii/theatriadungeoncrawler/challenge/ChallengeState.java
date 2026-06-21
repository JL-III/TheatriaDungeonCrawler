package com.jliii.theatriadungeoncrawler.challenge;

/**
 * The lifecycle state of a room's challenge (its "lock").
 *
 * <pre>
 * PENDING --activate()--> ACTIVE --isComplete--> COMPLETE
 *                           ^ |
 *                   reset() | | fail (movement)
 *                           +-+
 * </pre>
 *
 * <p>Free (un-gated) rooms are created already {@link #COMPLETE} for door
 * purposes — their forward door is open from the start — even though they may
 * still run enter/leave behaviour. {@link #COMPLETE} is sticky and shared across
 * all players in a co-op run, so a room solved once stays open (e.g. when the
 * party re-traverses it after a checkpoint respawn).</p>
 */
public enum ChallengeState {
    /** Built but not yet entered/activated. */
    PENDING,
    /** A player is inside; the objective is in progress. */
    ACTIVE,
    /** Objective met (or a free room); the forward door is open. */
    COMPLETE
}
