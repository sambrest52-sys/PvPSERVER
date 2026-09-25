package net.pvpserver.smoke;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the plugins against a MySQL/MariaDB server and plays a ranked match end to end. Skipped unless a server is
 * supplied, e.g. {@code mvn -Psmoke verify -Dpvp.test.mysql.host=127.0.0.1 -Dpvp.test.mysql.database=practice_test
 * -Dpvp.test.mysql.username=pvp -Dpvp.test.mysql.password=pvp [-Dpvp.test.mysql.type=MARIADB]}.
 */
@EnabledIfSystemProperty(named = "pvp.test.mysql.host", matches = ".+")
class MysqlBootTest extends SmokeTestBase {

    private final String prefix = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";
    private final String host = System.getProperty("pvp.test.mysql.host");
    private final int port = Integer.getInteger("pvp.test.mysql.port", 3306);
    private final String database = System.getProperty("pvp.test.mysql.database", "practice_test");
    private final String username = System.getProperty("pvp.test.mysql.username", "root");
    private final String password = System.getProperty("pvp.test.mysql.password", "");

    @Override
    protected void beforeLoad(File pluginsFolder) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", System.getProperty("pvp.test.mysql.type", "MYSQL"));
        config.set("storage.table-prefix", prefix);
        config.set("storage.mysql.host", host);
        config.set("storage.mysql.port", port);
        config.set("storage.mysql.database", database);
        config.set("storage.mysql.username", username);
        config.set("storage.mysql.password", password);
        File file = new File(pluginsFolder, "PvPCore/config.yml");
        file.getParentFile().mkdirs();
        config.save(file);
    }

    private Connection mysql() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true",
                username, password);
    }

    @Override
    protected void afterShutdown() throws SQLException {
        try (Connection connection = mysql(); Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SHOW TABLES LIKE '" + prefix + "%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                statement.execute("DROP TABLE " + table);
            }
        }
    }

    @Test
    void rankedMatchIsPersistedToMysql() throws Exception {
        assertTrue(core.isEnabled(), "core enabled with MySQL storage");
        assertTrue(!new File(core.getDataFolder(), "data/practice.db").exists(), "no SQLite file created");
        PlayerMock a = join("SqlAlpha");
        PlayerMock b = join("SqlBravo");
        assertTrue(run(a, "queue join gapple ranked"));
        assertTrue(run(b, "queue join gapple ranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        closeIn(a, b);
        hit(a, b, 1000);
        awaitLobby(a, b);
        a.disconnect();
        b.disconnect();
        waitFor(() -> false, 1000);
        try (Connection connection = mysql(); Statement statement = connection.createStatement()) {
            ResultSet match = statement.executeQuery("SELECT winners, losers, ranked FROM " + prefix + "matches");
            assertTrue(match.next(), "match history written to MySQL");
            assertEquals("SqlAlpha", match.getString(1));
            assertEquals("SqlBravo", match.getString(2));
            ResultSet elo = statement.executeQuery("SELECT p.name, s.elo FROM " + prefix + "stats s JOIN " + prefix
                    + "players p ON p.uuid = s.uuid WHERE s.kit = 'gapple' ORDER BY s.elo DESC");
            assertTrue(elo.next());
            assertEquals("SqlAlpha", elo.getString(1));
            assertEquals(1016, elo.getInt(2));
            assertTrue(elo.next());
            assertEquals(984, elo.getInt(2));
        }

        // A returning player's profile and rating come back from MySQL.
        PlayerMock back = join("SqlBravo");
        assertTrue(back.isOnline());
        assertTrue(run(back, "queue join gapple ranked"));
        assertTrue(drain(back).stream().anyMatch(m -> m.contains("984")), "rating loaded from MySQL is shown when queueing");
    }
}
