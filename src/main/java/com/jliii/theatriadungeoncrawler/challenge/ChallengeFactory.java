package com.jliii.theatriadungeoncrawler.challenge;

/**
 * Creates a fresh {@link RoomChallenge} for a given {@link ChallengeType}. New
 * gated challenges are registered here as they are implemented.
 */
public final class ChallengeFactory {

    public RoomChallenge create(ChallengeType type) {
        switch (type) {
            case REACH_GOAL:
                return new GoalChallenge();
            case EMPTY:
            default:
                return new EmptyChallenge();
        }
    }
}
