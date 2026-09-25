package net.pvpserver.duels.queue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueMatcherTest {

    private static final long NOW = 1_000_000L;
    private static final EloRange RANGE = new EloRange(50, 25, 5, 500);

    private static QueueEntry solo(int elo, long waitedMillis) {
        return new QueueEntry(UUID.randomUUID(), List.of(UUID.randomUUID()), elo, NOW - waitedMillis);
    }

    private static QueueEntry party(int elo, long waitedMillis) {
        UUID leader = UUID.randomUUID();
        return new QueueEntry(leader, List.of(leader, UUID.randomUUID()), elo, NOW - waitedMillis);
    }

    @Test
    void unrankedPairsInJoinOrder() {
        QueueEntry a = solo(1000, 30_000);
        QueueEntry b = solo(2000, 20_000);
        QueueEntry c = solo(500, 10_000);
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(List.of(c, b, a), 1, false, RANGE, NOW);
        assertEquals(1, pairings.size());
        assertEquals(a, pairings.get(0).teamA().get(0));
        assertEquals(b, pairings.get(0).teamB().get(0));
    }

    @Test
    void rankedRequiresRatingsInsideRange() {
        QueueEntry a = solo(1000, 0);
        QueueEntry b = solo(1100, 0);
        assertTrue(QueueMatcher.match(List.of(a, b), 1, true, RANGE, NOW).isEmpty(), "100 apart > 50 range");
        assertEquals(1, QueueMatcher.match(List.of(solo(1000, 0), solo(1040, 0)), 1, true, RANGE, NOW).size());
    }

    @Test
    void rangeWidensWithWaitingTimeForBothSides() {
        QueueEntry veteran = solo(1000, 60_000);
        QueueEntry newcomer = solo(1100, 0);
        assertTrue(QueueMatcher.match(List.of(veteran, newcomer), 1, true, RANGE, NOW).isEmpty(),
                "the newcomer's own window is still 50");
        QueueEntry patient = solo(1100, 20_000);
        assertEquals(1, QueueMatcher.match(List.of(veteran, patient), 1, true, RANGE, NOW).size(), "both windows reach 100+");
    }

    @Test
    void rankedPrefersClosestRating() {
        QueueEntry oldest = solo(1000, 60_000);
        QueueEntry far = solo(1300, 59_000);
        QueueEntry close = solo(1010, 58_000);
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(List.of(oldest, far, close), 1, true, RANGE, NOW);
        assertEquals(close, pairings.get(0).teamB().get(0));
    }

    @Test
    void noPlayerIsMatchedTwice() {
        List<QueueEntry> entries = List.of(solo(1000, 5), solo(1000, 4), solo(1000, 3), solo(1000, 2), solo(1000, 1));
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(entries, 1, false, RANGE, NOW);
        assertEquals(2, pairings.size());
        long distinct = pairings.stream().flatMap(p -> p.entries().stream()).distinct().count();
        assertEquals(4, distinct);
    }

    @Test
    void twoPartiesFormA2v2() {
        QueueEntry first = party(1000, 10_000);
        QueueEntry second = party(1020, 5_000);
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(List.of(first, second), 2, true, RANGE, NOW);
        assertEquals(1, pairings.size());
        assertEquals(List.of(first), pairings.get(0).teamA());
        assertEquals(List.of(second), pairings.get(0).teamB());
    }

    @Test
    void solosAreCombinedIntoTeams() {
        QueueEntry partyEntry = party(1000, 30_000);
        QueueEntry soloA = solo(1000, 20_000);
        QueueEntry soloB = solo(1010, 10_000);
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(List.of(partyEntry, soloA, soloB), 2, false, RANGE, NOW);
        assertEquals(1, pairings.size());
        QueueMatcher.Pairing pairing = pairings.get(0);
        int players = pairing.entries().stream().mapToInt(QueueEntry::size).sum();
        assertEquals(4, players);
        assertTrue(pairing.teamA().equals(List.of(partyEntry)) || pairing.teamB().equals(List.of(partyEntry)));
    }

    @Test
    void fourSolosMakeOne2v2AndOddOneWaits() {
        List<QueueEntry> solos = List.of(solo(1000, 50), solo(1000, 40), solo(1000, 30), solo(1000, 20), solo(1000, 10));
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(solos, 2, false, RANGE, NOW);
        assertEquals(1, pairings.size());
        assertEquals(4, pairings.get(0).entries().size());
    }

    @Test
    void oversizedEntriesAreIgnored() {
        QueueEntry trio = new QueueEntry(UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), 1000, NOW);
        assertTrue(QueueMatcher.match(List.of(trio, party(1000, 0)), 2, false, RANGE, NOW).isEmpty());
    }

    @Test
    void rankedTeamFormationRespectsRange() {
        QueueEntry low = solo(800, 0);
        QueueEntry high = solo(1500, 0);
        QueueEntry lowMate = solo(820, 0);
        QueueEntry highMate = solo(1510, 0);
        List<QueueMatcher.Pairing> pairings = QueueMatcher.match(List.of(low, high, lowMate, highMate), 2, true, RANGE, NOW);
        assertTrue(pairings.isEmpty(), "teams form (800+820, 1500+1510) but are too far apart to fight");
    }
}
