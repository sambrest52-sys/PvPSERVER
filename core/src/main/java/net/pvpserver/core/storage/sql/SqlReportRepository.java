package net.pvpserver.core.storage.sql;

import net.pvpserver.core.moderation.Report;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.ReportRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link ReportRepository}.
 */
public final class SqlReportRepository implements ReportRepository {

    private final Database db;
    private final String table;

    /**
     * @param db database
     */
    public SqlReportRepository(Database db) {
        this.db = db;
        this.table = db.table("reports");
    }

    @Override
    public CompletableFuture<Report> insert(Report r) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + table
                    + " (reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.reporter().toString());
                ps.setString(2, r.reporterName());
                ps.setString(3, r.target().toString());
                ps.setString(4, r.targetName());
                ps.setString(5, r.reason());
                ps.setLong(6, r.createdAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : 0;
                    return new Report(id, r.reporter(), r.reporterName(), r.target(), r.targetName(), r.reason(), r.createdAt());
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Report>> recent(int limit) {
        return db.query(connection -> {
            List<Report> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY created_at DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Report(rs.getLong("id"), UUID.fromString(rs.getString("reporter_uuid")), rs.getString("reporter_name"),
                                UUID.fromString(rs.getString("target_uuid")), rs.getString("target_name"), rs.getString("reason"),
                                rs.getLong("created_at")));
                    }
                }
            }
            return list;
        });
    }
}
