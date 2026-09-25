package net.pvpserver.duels.match;

/**
 * Origin of a match; decides whether statistics are recorded.
 */
public enum MatchType {
    /** Matched by a queue (ranked or unranked). */
    QUEUE(true),
    /** Accepted /duel request. */
    DUEL(true),
    /** Party split into two teams. */
    PARTY_SPLIT(false),
    /** Two parties fighting each other. */
    PARTY_VS_PARTY(false),
    /** Every party member for themselves. */
    PARTY_FFA(false);

    private final boolean recordsStats;

    MatchType(boolean recordsStats) {
        this.recordsStats = recordsStats;
    }

    /** @return whether wins/losses are recorded */
    public boolean recordsStats() {
        return recordsStats;
    }
}
