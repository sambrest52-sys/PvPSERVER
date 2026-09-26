package net.pvpserver.core.stats;

import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.storage.repository.StatsRepository;
import net.pvpserver.core.util.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Cached leaderboards, refreshed in the background every few minutes so menus and holograms are instant.
 */
public final class LeaderboardService {

    /** Key used for leaderboards across all kits. */
    public static final String GLOBAL = "global";

    private final StatsRepository repository;
    private final Supplier<List<Kit>> kits;
    private final Consumer<Runnable> mainThread;
    private final Logger logger;
    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int size = 10;
    private volatile long lastRefresh;
    private CompletableFuture<Void> running;

    /**
     * @param repository stats storage
     * @param kits kits
     * @param logger logger
     */
    public LeaderboardService(StatsRepository repository, KitService kits, Logger logger) {
        this(repository, kits::all, Tasks::sync, logger);
    }

    /**
     * @param repository stats storage
     * @param kits supplies the current kits
     * @param mainThread runs refresh listeners on the main thread
     * @param logger logger
     */
    public LeaderboardService(StatsRepository repository, Supplier<List<Kit>> kits, Consumer<Runnable> mainThread, Logger logger) {
        this.repository = repository;
        this.kits = kits;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * @param size entries per board
     */
    public void size(int size) {
        this.size = Math.max(1, size);
    }

    /**
     * @param refreshSeconds refresh period
     * @param size entries per board
     */
    public void start(int refreshSeconds, int size) {
        size(size);
        long ticks = Math.max(30, refreshSeconds) * 20L;
        Tasks.timer(this::refresh, 100L, ticks);
    }

    /**
     * Refreshes every board sequentially off the main thread. While a refresh is running, further calls return it
     * instead of starting another (a slow database never piles up queries). Boards keep their previous entries until
     * their new ones arrive, and a failed query leaves them untouched.
     *
     * @return completion
     */
    public synchronized CompletableFuture<Void> refresh() {
        if (running != null && !running.isDone()) {
            return running;
        }
        List<Kit> kitList = kits.get();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (StatField field : StatField.values()) {
            chain = chain.thenCompose(v -> load(null, field));
            for (Kit kit : kitList) {
                boolean ffaField = field.name().startsWith("FFA");
                if ((ffaField && !kit.ffa()) || (!ffaField && !(kit.ranked() || kit.unranked()))) {
                    continue;
                }
                chain = chain.thenCompose(v -> load(kit.id(), field));
            }
        }
        running = chain.whenComplete((v, error) -> {
            if (error != null) {
                logger.warning("Leaderboard refresh failed: " + error.getMessage());
                return;
            }
            lastRefresh = System.currentTimeMillis();
            mainThread.accept(() -> listeners.forEach(Runnable::run));
        });
        return running;
    }

    private CompletableFuture<Void> load(String kit, StatField field) {
        return repository.top(kit, field, size).thenAccept(list -> cache.put(key(kit, field), list));
    }

    private static String key(String kit, StatField field) {
        return (kit == null ? GLOBAL : kit) + ":" + field.name();
    }

    /**
     * @param kit kit id or null for global
     * @param field field
     * @return cached entries (empty before the first refresh)
     */
    public List<LeaderboardEntry> top(String kit, StatField field) {
        return cache.getOrDefault(key(kit, field), List.of());
    }

    /**
     * @param listener called on the main thread after each refresh (holograms)
     */
    public void onRefresh(Runnable listener) {
        listeners.add(listener);
    }

    /** @return epoch millis of the last successful refresh */
    public long lastRefresh() {
        return lastRefresh;
    }
}
