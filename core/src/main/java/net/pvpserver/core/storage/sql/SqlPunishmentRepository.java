package net.pvpserver.core.storage.sql;

import net.pvpserver.core.moderation.Punishment;
import net.pvpserver.core.moderation.PunishmentType;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.PunishmentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link PunishmentRepository}.
 */
public final class SqlPunishmentRepository implements PunishmentRepository {

    private final Database db;
    private final String table;

    /**
     * @param db database
     */
    public SqlPunishmentRepository(Database db) {
        this.db = db;
        this.table = db.table("punishments");
    }

    @Override
    public CompletableFuture<Punishment> insert(Punishment p) {
        return db.query(connection -> {
            String sql = "INSERT INTO " + table + " (uuid, name, type, reason, issuer_uuid, issuer_name, created_at, expires_at, active, removed_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, p.target().toString());
                ps.setString(2, p.targetName());
                ps.setString(3, p.type().name());
                ps.setString(4, p.reason());
                ps.setString(5, p.issuer() == null ? null : p.issuer().toString());
                ps.setString(6, p.issuerName());
                ps.setLong(7, p.createdAt());
                ps.setLong(8, p.expiresAt());
                ps.setInt(9, p.active() ? 1 : 0);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? p.withId(keys.getLong(1)) : p;
                }
            }
        });
    }

    @Override
    public Optional<Punishment> findActiveBlocking(UUID target, PunishmentType type, long now) {
        return db.run(connection -> active(connection, target, type, now));
    }

    @Override
    public CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type, long now) {
        return db.query(connection -> active(connection, target, type, now));
    }

    private Optional<Punishment> active(Connection connection, UUID target, PunishmentType type, long now) throws SQLException {
        String sql = "SELECT * FROM " + table + " WHERE uuid = ? AND type = ? AND active = 1 AND (expires_at = -1 OR expires_at > ?)"
                + " ORDER BY CASE WHEN expires_at = -1 THEN 1 ELSE 0 END DESC, expires_at DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            ps.setLong(3, now);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public CompletableFuture<List<Punishment>> history(UUID target, int limit) {
        return db.query(connection -> {
            List<Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, target.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Integer> deactivate(UUID target, PunishmentType type, String removedBy, String reason) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE " + table
                    + " SET active = 0, removed_by = ?, removed_at = ?, removed_reason = ? WHERE uuid = ? AND type = ? AND active = 1")) {
                ps.setString(1, removedBy);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, reason);
                ps.setString(4, target.toString());
                ps.setString(5, type.name());
                return ps.executeUpdate();
            }
        });
    }

    private static Punishment map(ResultSet rs) throws SQLException {
        String issuer = rs.getString("issuer_uuid");
        return new Punishment(rs.getLong("id"), UUID.fromString(rs.getString("uuid")), rs.getString("name"),
                PunishmentType.valueOf(rs.getString("type")), rs.getString("reason"), issuer == null ? null : UUID.fromString(issuer),
                rs.getString("issuer_name"), rs.getLong("created_at"), rs.getLong("expires_at"), rs.getInt("active") == 1,
                rs.getString("removed_by"), rs.getLong("removed_at"), rs.getString("removed_reason"));
    }
}
