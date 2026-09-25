package net.pvpserver.core.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloCalculatorTest {

    private final EloCalculator elo = new EloCalculator(32, 0, 1);

    @Test
    void expectedScoreIsHalfForEqualRatings() {
        assertEquals(0.5, EloCalculator.expected(1000, 1000), 1e-9);
    }

    @Test
    void expectedScoresAreComplementary() {
        double a = EloCalculator.expected(1200, 1000);
        double b = EloCalculator.expected(1000, 1200);
        assertEquals(1.0, a + b, 1e-9);
        assertTrue(a > 0.75 && a < 0.77, "400-point rule: 200 points gives ~0.76");
    }

    @Test
    void equalRatingsExchangeHalfTheKFactor() {
        EloCalculator.Result result = elo.calculate(1000, 1000);
        assertEquals(16, result.change());
        assertEquals(1016, result.winner());
        assertEquals(984, result.loser());
    }

    @Test
    void favouriteGainsLessThanUnderdog() {
        int favourite = elo.change(1400, 1000);
        int underdog = elo.change(1000, 1400);
        assertTrue(favourite < 16);
        assertTrue(underdog > 16);
        assertEquals(32, favourite + underdog, 1, "changes are symmetric around K");
    }

    @Test
    void minimumChangeIsApplied() {
        assertEquals(1, elo.change(3000, 100));
        assertEquals(5, new EloCalculator(32, 0, 5).change(3000, 100));
    }

    @Test
    void ratingNeverDropsBelowFloor() {
        EloCalculator floored = new EloCalculator(32, 100, 1);
        // Evenly matched players exchange ~16 points; the loser at 105 would drop to 89 but stops at the floor.
        EloCalculator.Result result = floored.calculate(110, 105);
        assertEquals(100, result.loser());
        assertEquals(100, floored.apply(110, -50));
    }

    @Test
    void teamChangeUsesAverages() {
        int team = elo.teamChange(List.of(1100, 900), List.of(1000, 1000));
        assertEquals(elo.change(1000, 1000), team);
        assertTrue(elo.teamChange(List.of(1500, 1500), List.of(1000, 1000)) < 16);
    }
}
