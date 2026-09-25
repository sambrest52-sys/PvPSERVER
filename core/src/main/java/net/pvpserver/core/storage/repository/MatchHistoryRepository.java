package net.pvpserver.core.storage.repository;

import net.pvpserver.core.stats.MatchRecord;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Append-only match history.
 */
public interface MatchHistoryRepository {

    /**
     * @param record finished match
     * @return completion
     */
    CompletableFuture<Void> insert(MatchRecord record);

    /**
     * @param name player name to search in winners/losers
     * @param limit max rows
     * @return newest first
     */
    CompletableFuture<List<MatchRecord>> recent(String name, int limit);
}
