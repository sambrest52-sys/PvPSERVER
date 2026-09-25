package net.pvpserver.core.stats;

import java.util.Collection;

/**
 * Standard Elo rating maths. Pure and thread safe.
 */
public final class EloCalculator {

    private final int kFactor;
    private final int floor;
    private final int minChange;

    /**
     * @param kFactor maximum rating change per game (typical 32)
     * @param floor lowest possible rating
     * @param minChange minimum change awarded for a win (keeps lopsided wins meaningful)
     */
    public EloCalculator(int kFactor, int floor, int minChange) {
        this.kFactor = Math.max(1, kFactor);
        this.floor = Math.max(0, floor);
        this.minChange = Math.max(0, minChange);
    }

    /**
     * @param rating player rating
     * @param opponent opponent rating
     * @return expected score in [0, 1]
     */
    public static double expected(double rating, double opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent - rating) / 400.0));
    }

    /**
     * Rating points the winner gains (and the loser loses).
     *
     * @param winnerRating winner rating before the game
     * @param loserRating loser rating before the game
     * @return positive change
     */
    public int change(double winnerRating, double loserRating) {
        int change = (int) Math.round(kFactor * (1.0 - expected(winnerRating, loserRating)));
        return Math.max(minChange, change);
    }

    /**
     * @param winnerRating winner rating
     * @param loserRating loser rating
     * @return new ratings for both players
     */
    public Result calculate(int winnerRating, int loserRating) {
        int change = change(winnerRating, loserRating);
        return new Result(winnerRating + change, Math.max(floor, loserRating - change), change);
    }

    /**
     * Team variant: the change is computed from the average rating of each team and applied to every member.
     *
     * @param winners winning team ratings
     * @param losers losing team ratings
     * @return change applied to every member
     */
    public int teamChange(Collection<Integer> winners, Collection<Integer> losers) {
        return change(average(winners), average(losers));
    }

    /**
     * @param rating current rating
     * @param change change (negative for losses)
     * @return new rating respecting the floor
     */
    public int apply(int rating, int change) {
        return Math.max(floor, rating + change);
    }

    private static double average(Collection<Integer> ratings) {
        return ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** @return configured K factor */
    public int kFactor() {
        return kFactor;
    }

    /**
     * @param winner winner's new rating
     * @param loser loser's new rating
     * @param change points exchanged
     */
    public record Result(int winner, int loser, int change) {
    }
}
