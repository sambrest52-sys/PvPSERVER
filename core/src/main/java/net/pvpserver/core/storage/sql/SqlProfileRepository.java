package net.pvpserver.core.storage.sql;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pvpserver.core.profile.CosmeticSelection;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.PlayerSettings;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.profile.TimeOfDay;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.repository.ProfileRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL implementation of {@link ProfileRepository}. Settings, cosmetics and ignore lists are stored as JSON.
 */
public final class SqlProfileRepository implements ProfileRepository {

    private static final Gson GSON = new Gson();
    private static final List<String> COLUMNS = List.of("uuid", "name", "name_lower", "rank_id", "first_join", "last_join",
            "playtime", "settings", "cosmetics", "ignored");

    private final Database db;
    private final String table;
    private final String defaultRank;
    private final int startingElo;

    /**
     * @param db database
     * @param defaultRank rank for new players
     * @param startingElo initial ELO
     */
    public SqlProfileRepository(Database db, String defaultRank, int startingElo) {
        this.db = db;
        this.table = db.table("players");
        this.defaultRank = defaultRank;
        this.startingElo = startingElo;
    }

    @Override
    public PlayerProfile loadOrCreateBlocking(UUID uuid, String name) {
        return db.run(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerSettings settings = parseSettings(rs.getString("settings"));
                        CosmeticSelection cosmetics = parseCosmetics(rs.getString("cosmetics"));
                        PlayerProfile profile = new PlayerProfile(uuid, name, rs.getString("rank_id"), rs.getLong("first_join"),
                                System.currentTimeMillis(), rs.getLong("playtime"), settings, cosmetics, startingElo);
                        parseIgnored(rs.getString("ignored"), profile);
                        return profile;
                    }
                }
            }
            long now = System.currentTimeMillis();
            PlayerProfile profile = new PlayerProfile(uuid, name, defaultRank, now, now, 0L, new PlayerSettings(),
                    new CosmeticSelection(), startingElo);
            write(connection, profile);
            return profile;
        });
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        Object[] snapshot = snapshot(profile);
        return db.query(connection -> {
            write(connection, snapshot);
            return null;
        });
    }

    @Override
    public void saveBlocking(PlayerProfile profile) {
        Object[] snapshot = snapshot(profile);
        db.run(connection -> {
            write(connection, snapshot);
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuid(String name) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM " + table
                    + " WHERE name_lower = ? ORDER BY last_join DESC LIMIT 1")) {
                ps.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<String>> findName(UUID uuid) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT name FROM " + table + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> setRank(UUID uuid, String rankId) {
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE " + table + " SET rank_id = ? WHERE uuid = ?")) {
                ps.setString(1, rankId);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Object[] snapshot(PlayerProfile profile) {
        profile.flushPlaytime();
        return new Object[]{profile.uuid().toString(), profile.name(), profile.name().toLowerCase(Locale.ROOT),
                profile.rankId(), profile.firstJoin(), profile.lastJoin(), profile.playtimeMillis(),
                settingsJson(profile.settings()), cosmeticsJson(profile.cosmetics()), ignoredJson(profile)};
    }

    private void write(Connection connection, PlayerProfile profile) throws SQLException {
        write(connection, snapshot(profile));
    }

    private void write(Connection connection, Object[] values) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(db.dialect().upsert(table, COLUMNS, List.of("uuid")))) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        }
    }

    private static String settingsJson(PlayerSettings settings) {
        JsonObject root = new JsonObject();
        JsonObject toggles = new JsonObject();
        for (Map.Entry<Setting, Boolean> entry : settings.toggles().entrySet()) {
            toggles.addProperty(entry.getKey().key(), entry.getValue());
        }
        root.add("toggles", toggles);
        root.addProperty("time", settings.timeOfDay().name());
        return GSON.toJson(root);
    }

    private static PlayerSettings parseSettings(String json) {
        PlayerSettings settings = new PlayerSettings();
        if (json == null || json.isBlank()) {
            return settings;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("toggles")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("toggles").entrySet()) {
                    Setting setting = Setting.byKey(entry.getKey());
                    if (setting != null) {
                        settings.set(setting, entry.getValue().getAsBoolean());
                    }
                }
            }
            if (root.has("time")) {
                settings.timeOfDay(TimeOfDay.valueOf(root.get("time").getAsString()));
            }
        } catch (RuntimeException ignored) {
            // corrupted JSON falls back to defaults
        }
        return settings;
    }

    private static String cosmeticsJson(CosmeticSelection cosmetics) {
        JsonObject root = new JsonObject();
        root.addProperty("kill-effect", cosmetics.killEffect());
        root.addProperty("death-animation", cosmetics.deathAnimation());
        root.addProperty("join-message", cosmetics.joinMessage());
        root.addProperty("trail", cosmetics.trail());
        root.addProperty("join-effect", cosmetics.joinEffect());
        return GSON.toJson(root);
    }

    private static CosmeticSelection parseCosmetics(String json) {
        CosmeticSelection selection = new CosmeticSelection();
        if (json == null || json.isBlank()) {
            return selection;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("kill-effect")) selection.killEffect(root.get("kill-effect").getAsString());
            if (root.has("death-animation")) selection.deathAnimation(root.get("death-animation").getAsString());
            if (root.has("join-message")) selection.joinMessage(root.get("join-message").getAsString());
            if (root.has("trail")) selection.trail(root.get("trail").getAsString());
            if (root.has("join-effect")) selection.joinEffect(root.get("join-effect").getAsString());
        } catch (RuntimeException ignored) {
            // defaults
        }
        return selection;
    }

    private static String ignoredJson(PlayerProfile profile) {
        JsonArray array = new JsonArray();
        profile.ignored().forEach(id -> array.add(id.toString()));
        return GSON.toJson(array);
    }

    private static void parseIgnored(String json, PlayerProfile profile) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                profile.ignored().add(UUID.fromString(element.getAsString()));
            }
        } catch (RuntimeException ignored) {
            // ignore malformed
        }
    }
}
