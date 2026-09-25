package net.pvpserver.duels.match;

/**
 * Match lifecycle.
 */
public enum MatchState {
    /** Waiting for an arena instance. */
    STARTING,
    /** Players frozen at spawns, counting down. */
    COUNTDOWN,
    /** Fighting. */
    FIGHTING,
    /** Round finished, next round pending. */
    ROUND_END,
    /** Winner decided, showing results. */
    ENDING,
    /** Finished and cleaned up. */
    ENDED
}
