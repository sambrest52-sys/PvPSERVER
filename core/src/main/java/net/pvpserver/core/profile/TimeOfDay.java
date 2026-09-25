package net.pvpserver.core.profile;

/**
 * Client-side time of day preference, applied with {@code Player#setPlayerTime}.
 */
public enum TimeOfDay {
    DAY(6000L),
    SUNSET(12500L),
    NIGHT(18000L);

    private final long ticks;

    TimeOfDay(long ticks) {
        this.ticks = ticks;
    }

    /** @return world time in ticks */
    public long ticks() {
        return ticks;
    }

    /** @return the next value, cycling */
    public TimeOfDay next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
