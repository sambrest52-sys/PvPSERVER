package net.pvpserver.core.stats;

import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.KitRules;
import net.pvpserver.core.storage.repository.StatsRepository;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaderboards are served from a cache that refreshes in the background; reads never hit storage.
 */
class LeaderboardServiceTest {

    /** Counts queries and answers with one entry whose value is the query number. */
    static final class FakeStats implements StatsRepository {
        final Map<String, AtomicInteger> queries = new ConcurrentHashMap<>();
        final AtomicInteger total = new AtomicInteger();
        volatile boolean fail;
        volatile CompletableFuture<Void> gate = CompletableFuture.completedFuture(null);

        @Override
        public CompletableFuture<List<LeaderboardEntry>> top(String kit, StatField field, int limit) {
            queries.computeIfAbsent((kit == null ? "global" : kit) + ":" + field, k -> new AtomicInteger()).incrementAndGet();
            int n = total.incrementAndGet();
            if (fail) {
                return CompletableFuture.failedFuture(new IllegalStateException("database down"));
            }
            return gate.thenApply(v -> List.of(new LeaderboardEntry(UUID.randomUUID(), "p" + n, n)));
        }

        @Override
        public Map<String, KitStats> loadBlocking(UUID uuid) {
            return Map.of();
        }

        @Override
        public CompletableFuture<Map<String, KitStats>> load(UUID uuid) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<Void> save(UUID uuid, Collection<KitStats> stats) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void saveBlocking(UUID uuid, Collection<KitStats> stats) {
        }

        @Override
        public CompletableFuture<Void> reset(UUID uuid, String kit) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static Kit kit(String id, boolean ranked, boolean ffa) {
        return new Kit(id, id, null, true, ranked, ranked, ffa, true, 1, "", Set.of(), Set.of(), Set.of(), KitRules.DEFAULT,
                new ItemStack[36], new ItemStack[4], null, List.of(), List.of(), "x");
    }

    private final FakeStats stats = new FakeStats();
    private final List<Runnable> mainThread = new ArrayList<>();
    private final LeaderboardService service = new LeaderboardService(stats,
            () -> List.of(kit("nodebuff", true, false), kit("sumo", true, true), kit("ffa-only", false, true)),
            mainThread::add, Logger.getLogger("test"));

    private void runMainThread() {
        List<Runnable> tasks = new ArrayList<>(mainThread);
        mainThread.clear();
        tasks.forEach(Runnable::run);
    }

    @Test
    void readsBeforeTheFirstRefreshAreEmptyAndFree() {
        assertTrue(service.top("nodebuff", StatField.ELO).isEmpty());
        assertTrue(service.top(null, StatField.WINS).isEmpty());
        assertEquals(0, stats.total.get());
        assertEquals(0, service.lastRefresh());
    }

    @Test
    void refreshLoadsEachBoardOnceAndReadsComeFromTheCache() {
        AtomicInteger notified = new AtomicInteger();
        service.onRefresh(notified::incrementAndGet);
        service.refresh().join();
        runMainThread();

        assertEquals(1, notified.get(), "listeners run once, on the main thread");
        assertTrue(service.lastRefresh() > 0);
        // Duel stats for ranked/unranked kits, FFA stats for FFA kits, and every stat globally.
        assertEquals(1, stats.queries.get("nodebuff:ELO").get());
        assertEquals(1, stats.queries.get("sumo:FFA_KILLS").get());
        assertEquals(1, stats.queries.get("ffa-only:FFA_ELO").get());
        assertEquals(null, stats.queries.get("nodebuff:FFA_KILLS"), "no FFA boards for duel-only kits");
        assertEquals(null, stats.queries.get("ffa-only:ELO"), "no duel boards for FFA-only kits");
        for (StatField field : StatField.values()) {
            assertEquals(1, stats.queries.get("global:" + field).get(), field.name());
        }
        int queries = stats.total.get();

        for (int i = 0; i < 100; i++) {
            assertEquals(1, service.top("nodebuff", StatField.ELO).size());
            assertEquals(1, service.top(null, StatField.WINS).size());
        }
        assertEquals(queries, stats.total.get(), "reads never query storage");
    }

    @Test
    void failedRefreshKeepsThePreviousBoards() {
        service.refresh().join();
        runMainThread();
        long firstRefresh = service.lastRefresh();
        LeaderboardEntry before = service.top("nodebuff", StatField.ELO).get(0);
        AtomicInteger notified = new AtomicInteger();
        service.onRefresh(notified::incrementAndGet);

        stats.fail = true;
        service.refresh().exceptionally(error -> null).join();
        runMainThread();

        assertEquals(before, service.top("nodebuff", StatField.ELO).get(0));
        assertEquals(firstRefresh, service.lastRefresh());
        assertEquals(0, notified.get(), "no refresh event after a failure");
    }

    @Test
    void overlappingRefreshesShareOneRun() {
        stats.gate = new CompletableFuture<>();
        CompletableFuture<Void> first = service.refresh();
        CompletableFuture<Void> second = service.refresh();
        assertSame(first, second, "a running refresh is reused");
        stats.gate.complete(null);
        first.join();
        int queries = stats.total.get();
        service.refresh().join();
        assertEquals(queries * 2, stats.total.get(), "a finished refresh does not block the next one");
    }

    @Test
    void newEntriesReplaceOldOnesAfterTheNextRefresh() {
        service.refresh().join();
        int firstValue = service.top("sumo", StatField.WINS).get(0).value();
        service.refresh().join();
        assertTrue(service.top("sumo", StatField.WINS).get(0).value() > firstValue);
    }
}
