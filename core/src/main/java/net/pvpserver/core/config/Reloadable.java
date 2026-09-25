package net.pvpserver.core.config;

/**
 * Anything that re-reads configuration on {@code /pvpadmin reload}.
 */
public interface Reloadable {

    /**
     * Re-reads configuration. Always called on the main thread.
     */
    void reload();
}
