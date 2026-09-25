package net.pvpserver.ffa;

/**
 * Session statistics of a player in FFA (reset when leaving).
 */
public final class FfaStats {

    private int kills;
    private int deaths;
    private int streak;
    private long protectedUntil;

    /** @return streak after the kill */
    public int kill() {
        kills++;
        return ++streak;
    }

    /** @return streak before dying */
    public int death() {
        deaths++;
        int old = streak;
        streak = 0;
        return old;
    }

    /** @return session kills */
    public int kills() {
        return kills;
    }

    /** @return session deaths */
    public int deaths() {
        return deaths;
    }

    /** @return current killstreak */
    public int streak() {
        return streak;
    }

    /** @return spawn protection expiry */
    public long protectedUntil() {
        return protectedUntil;
    }

    /** @param until spawn protection expiry */
    public void protectedUntil(long until) {
        this.protectedUntil = until;
    }

    /** @return whether spawn protection is active */
    public boolean isProtected() {
        return System.currentTimeMillis() < protectedUntil;
    }
}
