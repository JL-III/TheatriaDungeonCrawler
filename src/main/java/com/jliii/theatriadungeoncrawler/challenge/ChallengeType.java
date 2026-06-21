package com.jliii.theatriadungeoncrawler.challenge;

/**
 * The kind of challenge attached to a room. Drives both behaviour (via
 * {@link ChallengeFactory}) and generation weighting (free vs gated).
 *
 * <p>Only {@link #EMPTY} exists today; gated types (e.g. {@code CLEAR_MOBS},
 * {@code FIND_KEY}, {@code FLOOR_IS_LAVA}, parkour, button sequences) are added
 * as their {@link RoomChallenge} implementations land.</p>
 */
public enum ChallengeType {
    /** A free room with no objective: the forward door is open from the start. */
    EMPTY
}
