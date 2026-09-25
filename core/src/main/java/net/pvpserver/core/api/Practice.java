package net.pvpserver.core.api;

/**
 * Static accessor for the {@link PracticeApi}.
 */
public final class Practice {

    private static PracticeApi api;

    private Practice() {
    }

    /**
     * @return the API
     * @throws IllegalStateException when PvPCore is not enabled
     */
    public static PracticeApi api() {
        if (api == null) {
            throw new IllegalStateException("PvPCore is not enabled");
        }
        return api;
    }

    /**
     * Internal: set by PvPCore.
     *
     * @param instance api or null on disable
     */
    public static void set(PracticeApi instance) {
        api = instance;
    }
}
