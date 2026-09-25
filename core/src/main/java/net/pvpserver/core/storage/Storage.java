package net.pvpserver.core.storage;

import net.pvpserver.core.storage.repository.KitLayoutRepository;
import net.pvpserver.core.storage.repository.MatchHistoryRepository;
import net.pvpserver.core.storage.repository.ProfileRepository;
import net.pvpserver.core.storage.repository.PunishmentRepository;
import net.pvpserver.core.storage.repository.ReportRepository;
import net.pvpserver.core.storage.repository.StatsRepository;
import net.pvpserver.core.storage.sql.SqlKitLayoutRepository;
import net.pvpserver.core.storage.sql.SqlMatchHistoryRepository;
import net.pvpserver.core.storage.sql.SqlProfileRepository;
import net.pvpserver.core.storage.sql.SqlPunishmentRepository;
import net.pvpserver.core.storage.sql.SqlReportRepository;
import net.pvpserver.core.storage.sql.SqlStatsRepository;

/**
 * Holder for every repository over one {@link Database}.
 */
public final class Storage {

    private final Database database;
    private final ProfileRepository profiles;
    private final StatsRepository stats;
    private final KitLayoutRepository layouts;
    private final PunishmentRepository punishments;
    private final ReportRepository reports;
    private final MatchHistoryRepository matches;

    /**
     * Creates the schema and repositories.
     *
     * @param database database
     * @param defaultRank default rank id for new profiles
     * @param startingElo starting ELO
     */
    public Storage(Database database, String defaultRank, int startingElo) {
        this.database = database;
        Schema.create(database);
        this.profiles = new SqlProfileRepository(database, defaultRank, startingElo);
        this.stats = new SqlStatsRepository(database);
        this.layouts = new SqlKitLayoutRepository(database);
        this.punishments = new SqlPunishmentRepository(database);
        this.reports = new SqlReportRepository(database);
        this.matches = new SqlMatchHistoryRepository(database);
    }

    /** @return database */
    public Database database() {
        return database;
    }

    /** @return profile repository */
    public ProfileRepository profiles() {
        return profiles;
    }

    /** @return stats repository */
    public StatsRepository stats() {
        return stats;
    }

    /** @return kit layout repository */
    public KitLayoutRepository layouts() {
        return layouts;
    }

    /** @return punishment repository */
    public PunishmentRepository punishments() {
        return punishments;
    }

    /** @return report repository */
    public ReportRepository reports() {
        return reports;
    }

    /** @return match history repository */
    public MatchHistoryRepository matches() {
        return matches;
    }
}
