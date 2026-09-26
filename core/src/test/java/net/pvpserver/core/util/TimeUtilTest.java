package net.pvpserver.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {

    @Test
    void parsesCompoundDurations() {
        assertEquals(90_000, TimeUtil.parseDuration("1m30s"));
        assertEquals(86_400_000L + 2 * 3_600_000L, TimeUtil.parseDuration("1d2h"));
        assertEquals(2 * 604_800_000L, TimeUtil.parseDuration("2w"));
        assertEquals(2_592_000_000L, TimeUtil.parseDuration("1mo"));
    }

    @Test
    void permanentAndInvalidInputs() {
        assertEquals(Long.MAX_VALUE, TimeUtil.parseDuration("perm"));
        assertEquals(-1, TimeUtil.parseDuration("abc"));
        assertEquals(-1, TimeUtil.parseDuration("10x"));
        assertEquals(-1, TimeUtil.parseDuration(""));
        assertEquals(-1, TimeUtil.parseDuration("5m junk"));
    }

    @Test
    void formatsDurationsAndClocks() {
        assertEquals("1d 2h 3m 4s", TimeUtil.formatDuration(93_784_000L));
        assertEquals("0s", TimeUtil.formatDuration(0));
        assertEquals("permanent", TimeUtil.formatDuration(Long.MAX_VALUE));
        assertEquals("01:05", TimeUtil.formatClock(65_000));
        assertEquals("1:01:05", TimeUtil.formatClock(3_665_000));
    }

    @org.junit.jupiter.api.Test
    void formatsPreciseTimes() {
        org.junit.jupiter.api.Assertions.assertEquals("0:07.250", TimeUtil.formatMillis(7250));
        org.junit.jupiter.api.Assertions.assertEquals("1:23.456", TimeUtil.formatMillis(83_456));
        org.junit.jupiter.api.Assertions.assertEquals("1:02:03.004", TimeUtil.formatMillis(3_723_004));
        org.junit.jupiter.api.Assertions.assertEquals("0:00.000", TimeUtil.formatMillis(-5));
    }
}
