package net.pvpserver.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Runs the storage contract against a SQLite file, the default back end.
 */
class SqliteStorageTest extends StorageContractTest {

    @Override
    protected YamlConfiguration storageConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "SQLITE");
        config.set("sqlite.file", "test.db");
        return config;
    }
}
