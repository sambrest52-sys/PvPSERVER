package net.pvpserver.core.state;

/**
 * High level activity of an online player. Exactly one state at a time.
 */
public enum PlayerState {
    /** In the lobby, free to queue or accept requests. */
    LOBBY,
    /** Waiting in a duel queue (still physically in the lobby). */
    QUEUE,
    /** Participating in a duel/party match. */
    MATCH,
    /** Spectating a match. */
    SPECTATING,
    /** Playing in an FFA arena. */
    FFA,
    /** Using the kit editor. */
    EDITING;

    /** @return whether this state is "busy" (cannot accept duels or join queues) */
    public boolean busy() {
        return this != LOBBY;
    }

    /** @return whether combat deaths are intercepted in this state */
    public boolean combat() {
        return this == MATCH || this == FFA;
    }
}
