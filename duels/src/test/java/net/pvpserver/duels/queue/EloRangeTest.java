package net.pvpserver.duels.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EloRangeTest {

    @Test
    void widensStepwiseAndCaps() {
        EloRange range = new EloRange(50, 25, 5, 200);
        assertEquals(50, range.range(0));
        assertEquals(50, range.range(4_999));
        assertEquals(75, range.range(5_000));
        assertEquals(100, range.range(10_000));
        assertEquals(200, range.range(600_000));
    }

    @Test
    void invalidValuesAreClamped() {
        EloRange range = new EloRange(100, 10, 0, 50);
        assertEquals(100, range.max());
        assertEquals(1, range.intervalSeconds());
    }
}
