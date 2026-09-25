package net.pvpserver.core.arena;

/**
 * Thrown (through a failed future) when no enabled arena matches a request.
 */
public final class NoArenaAvailableException extends RuntimeException {

    /**
     * @param message reason
     */
    public NoArenaAvailableException(String message) {
        super(message, null, false, false);
    }
}
