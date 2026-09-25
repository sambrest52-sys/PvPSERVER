package net.pvpserver.core.storage.sql;

import net.pvpserver.core.stats.KitStats;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.StatField;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.StatsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link StatsRepository}.
 */
public final class SqlStatsRepository implements StatsRepository {

    private static final List<String> COLUMNS = List.of("uuid", "kit", "elo", "ranked_wins", "ranked_losses", "unranked_wins",
            "unranked_losses", "win_streak", "best_win_streak", "ffa_kills", "ffa_deaths", "ffa_best_streak", "ffa_elo");

    private final Database db;
    private final String table;
    private final String players;

    /**
     * @param db database
     */
    public SqlStatsRepository(Database db) {
        this.db = db;
        this.table = db.table("stats");
        this.players = db.table("players");
    }

    @Override
    public Map<String, KitStats> loadBlocking(UUID uuid) {
        return db.run(connection -> read(connection, uuid));
    }

    @Override
    public CompletableFuture<Map<String, KitStats>> load(UUID uuid) {
        return db.query(connection -> read(connection, uuid));
    }

    private Map<String, KitStats> read(Connection connection, UUID uuid) throws SQLException {
        Map<String, KitStats> map = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String kit = rs.getString("kit");
                    map.put(kit, new KitStats(kit, rs.getInt("elo"), rs.getInt("ranked_wins"), rs.getInt("ranked_losses"),
                            rs.getInt("unranked_wins"), rs.getInt("unranked_losses"), rs.getInt("win_streak"),
                            rs.getInt("best_win_streak"), rs.getInt("ffa_kills"), rs.getInt("ffa_deaths"),
                            rs.getInt("ffa_best_streak"), rs.getInt("ffa_elo")));
                }
            }
        }
        return map;
    }

    @Override
    public CompletableFuture<Void> save(UUID uuid, Collection<KitStats> stats) {
        List<KitStats> copies = stats.stream().map(KitStats::copy).toList();
        return db.query(connection -> {
            write(connection, uuid, copies);
            return null;
        });
    }

    @Override
    public void saveBlocking(UUID uuid, Collection<KitStats> stats) {
        List<KitStats> copies = stats.stream().map(KitStats::copy).toList();
        db.run(connection -> {
            write(connection, uuid, copies);
            return null;
        });
    }

    private void write(Connection connection, UUID uuid, List<KitStats> stats) throws SQLException {
        if (stats.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(db.dialect().upsert(table, COLUMNS, List.of("uuid", "kit")))) {
            for (KitStats s : stats) {
                ps.setString(1, uuid.toString());
                ps.setString(2, s.kit());
                ps.setInt(3, s.elo());
                ps.setInt(4, s.rankedWins());
                ps.setInt(5, s.rankedLosses());
                ps.setInt(6, s.unrankedWins());
                ps.setInt(7, s.unrankedLosses());
                ps.setInt(8, s.winStreak());
                ps.setInt(9, s.bestWinStreak());
                ps.setInt(10, s.ffaKills());
                ps.setInt(11, s.ffaDeaths());
                ps.setInt(12, s.ffaBestStreak());
                ps.setInt(13, s.ffaElo());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> top(String kit, StatField field, int limit) {
        return db.query(connection -> {
            String expression = field.sqlExpression();
            String sql;
            if (kit == null) {
                String aggregate = (field == StatField.ELO || field == StatField.FFA_ELO) ? "AVG" : (field.name().contains("STREAK") ? "MAX" : "SUM");
                sql = "SELECT s.uuid, p.name, " + aggregate + "(" + expression + ") AS v FROM " + table + " s JOIN " + players
                        + " p ON p.uuid = s.uuid GROUP BY s.uuid, p.name ORDER BY v DESC LIMIT ?";
            } else {
                sql = "SELECT s.uuid, p.name, (" + expression + ") AS v FROM " + table + " s JOIN " + players
                        + " p ON p.uuid = s.uuid WHERE s.kit = ? ORDER BY v DESC LIMIT ?";
            }
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                if (kit != null) {
                    ps.setString(i++, kit);
                }
                ps.setInt(i, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), (int) Math.round(rs.getDouble(3))));
                    }
                }
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> reset(UUID uuid, String kit) {
        return db.query(connection -> {
            String sql = "DELETE FROM " + table + " WHERE uuid = ?" + (kit == null ? "" : " AND kit = ?");
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                if (kit != null) {
                    ps.setString(2, kit);
                }
                ps.executeUpdate();
            }
            return null;
        });
    }
}
