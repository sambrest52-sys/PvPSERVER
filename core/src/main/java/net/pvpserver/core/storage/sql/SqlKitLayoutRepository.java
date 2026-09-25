package net.pvpserver.core.storage.sql;

import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.KitLayoutRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link KitLayoutRepository}.
 */
public final class SqlKitLayoutRepository implements KitLayoutRepository {

    private final Database db;
    private final String table;

    /**
     * @param db database
     */
    public SqlKitLayoutRepository(Database db) {
        this.db = db;
        this.table = db.table("kit_layouts");
    }

    @Override
    public Map<String, String> loadBlocking(UUID uuid) {
        return db.run(connection -> {
            Map<String, String> map = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT kit, layout FROM " + table + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString(1), rs.getString(2));
                    }
                }
            }
            return map;
        });
    }

    @Override
    public CompletableFuture<Void> save(UUID uuid, String kit, String layout) {
        return db.query(connection -> {
            if (layout == null) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ? AND kit = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, kit);
                    ps.executeUpdate();
                }
                return null;
            }
            try (PreparedStatement ps = connection.prepareStatement(db.dialect().upsert(table, List.of("uuid", "kit", "layout"), List.of("uuid", "kit")))) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                ps.setString(3, layout);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
