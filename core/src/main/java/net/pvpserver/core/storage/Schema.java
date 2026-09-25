package net.pvpserver.core.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates all tables and indexes. Idempotent; runs on every start.
 */
public final class Schema {

    private Schema() {
    }

    /**
     * @param db database
     */
    public static void create(Database db) {
        Dialect d = db.dialect();
        db.run(connection -> {
            String players = db.table("players");
            ddl(connection, "CREATE TABLE IF NOT EXISTS " + players + " ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "name VARCHAR(16) NOT NULL,"
                    + "name_lower VARCHAR(16) NOT NULL,"
                    + "rank_id VARCHAR(32) NOT NULL,"
                    + "first_join BIGINT NOT NULL,"
                    + "last_join BIGINT NOT NULL,"
                    + "playtime BIGINT NOT NULL DEFAULT 0,"
                    + "settings TEXT,"
                    + "cosmetics TEXT,"
                    + "ignored TEXT)");
            index(d, connection, db.table("idx_players_name"), players, "name_lower");

            String stats = db.table("stats");
            ddl(connection, "CREATE TABLE IF NOT EXISTS " + stats + " ("
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "kit VARCHAR(64) NOT NULL,"
                    + "elo INT NOT NULL,"
                    + "ranked_wins INT NOT NULL DEFAULT 0,"
                    + "ranked_losses INT NOT NULL DEFAULT 0,"
                    + "unranked_wins INT NOT NULL DEFAULT 0,"
                    + "unranked_losses INT NOT NULL DEFAULT 0,"
                    + "win_streak INT NOT NULL DEFAULT 0,"
                    + "best_win_streak INT NOT NULL DEFAULT 0,"
                    + "ffa_kills INT NOT NULL DEFAULT 0,"
                    + "ffa_deaths INT NOT NULL DEFAULT 0,"
                    + "ffa_best_streak INT NOT NULL DEFAULT 0,"
                    + "ffa_elo INT NOT NULL,"
                    + "PRIMARY KEY (uuid, kit))");
            index(d, connection, db.table("idx_stats_elo"), stats, "kit, elo");
            index(d, connection, db.table("idx_stats_ffa"), stats, "kit, ffa_kills");

            ddl(connection, "CREATE TABLE IF NOT EXISTS " + db.table("kit_layouts") + " ("
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "kit VARCHAR(64) NOT NULL,"
                    + "layout TEXT NOT NULL,"
                    + "PRIMARY KEY (uuid, kit))");

            String punishments = db.table("punishments");
            ddl(connection, "CREATE TABLE IF NOT EXISTS " + punishments + " ("
                    + "id " + d.autoIncrementPrimaryKey() + ","
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "name VARCHAR(16) NOT NULL,"
                    + "type VARCHAR(16) NOT NULL,"
                    + "reason TEXT NOT NULL,"
                    + "issuer_uuid VARCHAR(36),"
                    + "issuer_name VARCHAR(32) NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "expires_at BIGINT NOT NULL,"
                    + "active INT NOT NULL,"
                    + "removed_by VARCHAR(32),"
                    + "removed_at BIGINT NOT NULL DEFAULT 0,"
                    + "removed_reason TEXT)");
            index(d, connection, db.table("idx_punish_uuid"), punishments, "uuid, type, active");

            ddl(connection, "CREATE TABLE IF NOT EXISTS " + db.table("reports") + " ("
                    + "id " + d.autoIncrementPrimaryKey() + ","
                    + "reporter_uuid VARCHAR(36) NOT NULL,"
                    + "reporter_name VARCHAR(16) NOT NULL,"
                    + "target_uuid VARCHAR(36) NOT NULL,"
                    + "target_name VARCHAR(16) NOT NULL,"
                    + "reason TEXT NOT NULL,"
                    + "created_at BIGINT NOT NULL)");

            ddl(connection, "CREATE TABLE IF NOT EXISTS " + db.table("matches") + " ("
                    + "id " + d.autoIncrementPrimaryKey() + ","
                    + "kit VARCHAR(64) NOT NULL,"
                    + "type VARCHAR(32) NOT NULL,"
                    + "ranked INT NOT NULL,"
                    + "winners TEXT NOT NULL,"
                    + "losers TEXT NOT NULL,"
                    + "elo_change INT NOT NULL,"
                    + "duration BIGINT NOT NULL,"
                    + "ended_at BIGINT NOT NULL)");
            return null;
        });
    }

    private static void ddl(Connection connection, String sql) throws SQLException {
        Database.ddl(connection, sql, false);
    }

    private static void index(Dialect dialect, Connection connection, String name, String table, String columns) throws SQLException {
        // MySQL lacks CREATE INDEX IF NOT EXISTS: ignore "duplicate key name" on restarts.
        Database.ddl(connection, dialect.createIndex(name, table, columns), dialect != Dialect.SQLITE);
    }
}
