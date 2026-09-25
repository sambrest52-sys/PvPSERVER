package net.pvpserver.core.stats;

/**
 * Leaderboard-able statistics and the SQL expression that orders them.
 */
public enum StatField {
    ELO("elo", "ELO"),
    WINS("ranked_wins + unranked_wins", "Wins"),
    RANKED_WINS("ranked_wins", "Ranked Wins"),
    BEST_WIN_STREAK("best_win_streak", "Best Win Streak"),
    FFA_KILLS("ffa_kills", "FFA Kills"),
    FFA_ELO("ffa_elo", "FFA Rating"),
    FFA_BEST_STREAK("ffa_best_streak", "Best Killstreak");

    private final String sqlExpression;
    private final String displayName;

    StatField(String sqlExpression, String displayName) {
        this.sqlExpression = sqlExpression;
        this.displayName = displayName;
    }

    /** @return SQL used in ORDER BY and SELECT */
    public String sqlExpression() {
        return sqlExpression;
    }

    /** @return human name */
    public String displayName() {
        return displayName;
    }

    /**
     * Reads the matching value from in-memory stats.
     *
     * @param stats stats
     * @return value
     */
    public int read(KitStats stats) {
        return switch (this) {
            case ELO -> stats.elo();
            case WINS -> stats.wins();
            case RANKED_WINS -> stats.rankedWins();
            case BEST_WIN_STREAK -> stats.bestWinStreak();
            case FFA_KILLS -> stats.ffaKills();
            case FFA_ELO -> stats.ffaElo();
            case FFA_BEST_STREAK -> stats.ffaBestStreak();
        };
    }

    /**
     * @param name case-insensitive name
     * @return field or null
     */
    public static StatField parse(String name) {
        for (StatField field : values()) {
            if (field.name().equalsIgnoreCase(name) || field.name().replace("_", "").equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }
}
