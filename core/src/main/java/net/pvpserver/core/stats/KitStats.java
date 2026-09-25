package net.pvpserver.core.stats;

/**
 * Mutable per-kit statistics for one player. Instances are owned by a {@link net.pvpserver.core.profile.PlayerProfile}
 * and mutated on the main thread; persistence copies them via {@link #copy()}.
 */
public final class KitStats {

    private final String kit;
    private int elo;
    private int rankedWins;
    private int rankedLosses;
    private int unrankedWins;
    private int unrankedLosses;
    private int winStreak;
    private int bestWinStreak;
    private int ffaKills;
    private int ffaDeaths;
    private int ffaBestStreak;
    private int ffaElo;

    /**
     * @param kit kit id
     * @param startingElo initial ELO for duels and FFA
     */
    public KitStats(String kit, int startingElo) {
        this.kit = kit;
        this.elo = startingElo;
        this.ffaElo = startingElo;
    }

    /**
     * Full constructor used by storage.
     */
    public KitStats(String kit, int elo, int rankedWins, int rankedLosses, int unrankedWins, int unrankedLosses,
                    int winStreak, int bestWinStreak, int ffaKills, int ffaDeaths, int ffaBestStreak, int ffaElo) {
        this.kit = kit;
        this.elo = elo;
        this.rankedWins = rankedWins;
        this.rankedLosses = rankedLosses;
        this.unrankedWins = unrankedWins;
        this.unrankedLosses = unrankedLosses;
        this.winStreak = winStreak;
        this.bestWinStreak = bestWinStreak;
        this.ffaKills = ffaKills;
        this.ffaDeaths = ffaDeaths;
        this.ffaBestStreak = ffaBestStreak;
        this.ffaElo = ffaElo;
    }

    /** @return defensive copy for async persistence */
    public KitStats copy() {
        return new KitStats(kit, elo, rankedWins, rankedLosses, unrankedWins, unrankedLosses, winStreak, bestWinStreak,
                ffaKills, ffaDeaths, ffaBestStreak, ffaElo);
    }

    /**
     * Records a duel result.
     *
     * @param ranked whether the match was ranked
     * @param won whether this player won
     */
    public void recordDuel(boolean ranked, boolean won) {
        if (won) {
            if (ranked) rankedWins++; else unrankedWins++;
            winStreak++;
            bestWinStreak = Math.max(bestWinStreak, winStreak);
        } else {
            if (ranked) rankedLosses++; else unrankedLosses++;
            winStreak = 0;
        }
    }

    /**
     * Records an FFA kill.
     *
     * @param currentStreak streak after the kill
     */
    public void recordFfaKill(int currentStreak) {
        ffaKills++;
        ffaBestStreak = Math.max(ffaBestStreak, currentStreak);
    }

    /** Records an FFA death. */
    public void recordFfaDeath() {
        ffaDeaths++;
    }

    /** @return kit id */
    public String kit() {
        return kit;
    }

    /** @return duel ELO */
    public int elo() {
        return elo;
    }

    /** @param elo new duel ELO */
    public void elo(int elo) {
        this.elo = Math.max(0, elo);
    }

    /** @return ranked wins */
    public int rankedWins() {
        return rankedWins;
    }

    /** @return ranked losses */
    public int rankedLosses() {
        return rankedLosses;
    }

    /** @return unranked wins */
    public int unrankedWins() {
        return unrankedWins;
    }

    /** @return unranked losses */
    public int unrankedLosses() {
        return unrankedLosses;
    }

    /** @return total wins */
    public int wins() {
        return rankedWins + unrankedWins;
    }

    /** @return total losses */
    public int losses() {
        return rankedLosses + unrankedLosses;
    }

    /** @return current duel win streak */
    public int winStreak() {
        return winStreak;
    }

    /** @return best duel win streak */
    public int bestWinStreak() {
        return bestWinStreak;
    }

    /** @return FFA kills */
    public int ffaKills() {
        return ffaKills;
    }

    /** @return FFA deaths */
    public int ffaDeaths() {
        return ffaDeaths;
    }

    /** @return best FFA killstreak */
    public int ffaBestStreak() {
        return ffaBestStreak;
    }

    /** @return FFA rating */
    public int ffaElo() {
        return ffaElo;
    }

    /** @param ffaElo new FFA rating */
    public void ffaElo(int ffaElo) {
        this.ffaElo = Math.max(0, ffaElo);
    }

    /** @return kills / deaths ratio with two decimals */
    public String kdr() {
        double kdr = ffaDeaths == 0 ? ffaKills : (double) ffaKills / ffaDeaths;
        return String.format(java.util.Locale.ROOT, "%.2f", kdr);
    }

    /** @return win ratio percent string */
    public String winRate() {
        int total = wins() + losses();
        return total == 0 ? "0%" : Math.round(wins() * 100.0 / total) + "%";
    }
}
