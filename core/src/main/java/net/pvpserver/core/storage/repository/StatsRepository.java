package net.pvpserver.core.storage.repository;

import net.pvpserver.core.stats.KitStats;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.StatField;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Per-kit statistics persistence and leaderboard queries.
 */
public interface StatsRepository {

    /**
     * Blocking load for pre-login.
     *
     * @param uuid player id
     * @return stats keyed by kit id
     */
    Map<String, KitStats> loadBlocking(UUID uuid);

    /**
     * @param uuid player id
     * @return stats keyed by kit id
     */
    CompletableFuture<Map<String, KitStats>> load(UUID uuid);

    /**
     * @param uuid player id
     * @param stats snapshots to upsert
     * @return completion
     */
    CompletableFuture<Void> save(UUID uuid, Collection<KitStats> stats);

    /**
     * Blocking save for shutdown.
     *
     * @param uuid player id
     * @param stats snapshots
     */
    void saveBlocking(UUID uuid, Collection<KitStats> stats);

    /**
     * @param kit kit id, or {@code null} for all kits summed (ELO is averaged)
     * @param field field to order by
     * @param limit max rows
     * @return ordered entries
     */
    CompletableFuture<List<LeaderboardEntry>> top(String kit, StatField field, int limit);

    /**
     * Resets statistics for a kit (or all kits when {@code kit} is null).
     *
     * @param uuid player id
     * @param kit kit id or null
     * @return completion
     */
    CompletableFuture<Void> reset(UUID uuid, String kit);
}
