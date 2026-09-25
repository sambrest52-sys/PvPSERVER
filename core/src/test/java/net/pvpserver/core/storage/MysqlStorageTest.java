package net.pvpserver.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the storage contract against a MySQL or MariaDB server. Skipped unless a server is supplied, e.g.
 * {@code mvn test -pl core -Dpvp.test.mysql.host=127.0.0.1 -Dpvp.test.mysql.database=practice_test
 * -Dpvp.test.mysql.username=pvp -Dpvp.test.mysql.password=pvp [-Dpvp.test.mysql.type=MARIADB]}.
 * Each test uses its own random table prefix and drops its tables afterwards.
 */
@EnabledIfSystemProperty(named = "pvp.test.mysql.host", matches = ".+")
class MysqlStorageTest extends StorageContractTest {

    private final String prefix = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";

    @Override
    protected YamlConfiguration storageConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", System.getProperty("pvp.test.mysql.type", "MYSQL"));
        config.set("table-prefix", prefix);
        config.set("mysql.host", System.getProperty("pvp.test.mysql.host"));
        config.set("mysql.port", Integer.getInteger("pvp.test.mysql.port", 3306));
        config.set("mysql.database", System.getProperty("pvp.test.mysql.database", "practice_test"));
        config.set("mysql.username", System.getProperty("pvp.test.mysql.username", "root"));
        config.set("mysql.password", System.getProperty("pvp.test.mysql.password", ""));
        config.set("pool.maximum-pool-size", 4);
        return config;
    }

    @Override
    protected void cleanUp() {
        database.run(connection -> {
            List<String> tables = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SHOW TABLES LIKE '" + prefix + "%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            try (Statement statement = connection.createStatement()) {
                for (String table : tables) {
                    statement.execute("DROP TABLE " + table);
                }
            }
            return null;
        });
    }
}
