package net.pvpserver.core.storage.repository;

import net.pvpserver.core.moderation.Report;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Report persistence.
 */
public interface ReportRepository {

    /**
     * @param report report without id
     * @return report with id
     */
    CompletableFuture<Report> insert(Report report);

    /**
     * @param limit max rows
     * @return newest first
     */
    CompletableFuture<List<Report>> recent(int limit);
}
