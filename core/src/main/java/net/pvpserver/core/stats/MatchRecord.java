package net.pvpserver.core.stats;

/**
 * Finished match summary for the history table.
 *
 * @param kit kit id
 * @param type match type name
 * @param ranked ranked flag
 * @param winners comma separated winner names
 * @param losers comma separated loser names
 * @param eloChange ELO gained by the winners (0 if unranked)
 * @param durationMillis match duration
 * @param endedAt epoch millis
 */
public record MatchRecord(String kit, String type, boolean ranked, String winners, String losers, int eloChange,
                          long durationMillis, long endedAt) {
}
