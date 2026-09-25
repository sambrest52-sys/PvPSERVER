package net.pvpserver.core.storage.sql;

import net.pvpserver.core.stats.MatchRecord;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.MatchHistoryRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link MatchHistoryRepository}.
 */
public final class SqlMatchHistoryRepository implements MatchHistoryRepository {

    private final Database db;
    private final String table;

    /**
     * @param db database
     */
    public SqlMatchHistoryRepository(Database db) {
        this.db = db;
        this.table = db.table("matches");
    }

    @Override
    public CompletableFuture<Void> insert(MatchRecord r) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + table
                    + " (kit, type, ranked, winners, losers, elo_change, duration, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, r.kit());
                ps.setString(2, r.type());
                ps.setInt(3, r.ranked() ? 1 : 0);
                ps.setString(4, r.winners());
                ps.setString(5, r.losers());
                ps.setInt(6, r.eloChange());
                ps.setLong(7, r.durationMillis());
                ps.setLong(8, r.endedAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<MatchRecord>> recent(String name, int limit) {
        return db.query(connection -> {
            List<MatchRecord> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table
                    + " WHERE winners LIKE ? OR losers LIKE ? ORDER BY ended_at DESC LIMIT ?")) {
                // "_" acts as a single-char wildcard here; harmless over-matching for a history view.
                String like = "%" + name.replace("%", "") + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new MatchRecord(rs.getString("kit"), rs.getString("type"), rs.getInt("ranked") == 1,
                                rs.getString("winners"), rs.getString("losers"), rs.getInt("elo_change"),
                                rs.getLong("duration"), rs.getLong("ended_at")));
                    }
                }
            }
            return list;
        });
    }
}
