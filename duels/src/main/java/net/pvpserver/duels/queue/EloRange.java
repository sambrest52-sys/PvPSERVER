package net.pvpserver.duels.queue;

/**
 * Widening ELO search window: starts at {@code base}, grows by {@code step} every {@code intervalSeconds} and is
 * capped at {@code max}.
 *
 * @param base initial +/- range
 * @param step growth per interval
 * @param intervalSeconds seconds per growth step
 * @param max maximum range
 */
public record EloRange(int base, int step, int intervalSeconds, int max) {

    /**
     * @param base initial +/- range
     * @param step growth per interval
     * @param intervalSeconds seconds per growth step (at least 1)
     * @param max maximum range
     */
    public EloRange {
        if (intervalSeconds < 1) {
            intervalSeconds = 1;
        }
        if (max < base) {
            max = base;
        }
    }

    /**
     * @param waitedMillis time in queue
     * @return current +/- range
     */
    public int range(long waitedMillis) {
        long steps = (waitedMillis / 1000L) / intervalSeconds;
        long value = base + steps * (long) step;
        return (int) Math.min(max, value);
    }
}
