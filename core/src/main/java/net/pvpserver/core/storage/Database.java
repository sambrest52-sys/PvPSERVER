package net.pvpserver.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pooled JDBC access. All work runs on a dedicated executor and is exposed as {@link CompletableFuture}s so the main
 * thread never blocks on IO.
 */
public final class Database implements AutoCloseable {

    private final Logger logger;
    private final StorageType type;
    private final Dialect dialect;
    private final String tablePrefix;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    /**
     * Opens the pool described by the {@code storage} section of config.yml.
     *
     * @param section storage configuration
     * @param dataFolder plugin folder (for SQLite)
     * @param logger plugin logger
     */
    public Database(ConfigurationSection section, File dataFolder, Logger logger) {
        this.logger = logger;
        this.type = parseType(section.getString("type", "SQLITE"));
        this.dialect = type == StorageType.SQLITE ? Dialect.SQLITE : Dialect.MYSQL;
        this.tablePrefix = section.getString("table-prefix", "pvp_");

        HikariConfig config = new HikariConfig();
        config.setPoolName("PvPCore-Pool");
        switch (type) {
            case SQLITE -> {
                File file = new File(dataFolder, section.getString("sqlite.file", "practice.db"));
                file.getParentFile().mkdirs();
                config.setDriverClassName("org.sqlite.JDBC");
                config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
                // SQLite allows one writer; a single connection avoids SQLITE_BUSY and keeps ordering deterministic.
                config.setMaximumPoolSize(1);
                config.setMinimumIdle(1);
                // sqlite-jdbc reads pragmas from the driver properties Hikari passes through.
                config.addDataSourceProperty("journal_mode", "WAL");
                config.addDataSourceProperty("synchronous", "NORMAL");
                config.addDataSourceProperty("busy_timeout", "5000");
            }
            case MYSQL, MARIADB -> {
                ConfigurationSection sql = section.getConfigurationSection("mysql");
                String host = sql == null ? "localhost" : sql.getString("host", "localhost");
                int port = sql == null ? 3306 : sql.getInt("port", 3306);
                String db = sql == null ? "practice" : sql.getString("database", "practice");
                String params = sql == null ? "" : sql.getString("parameters", "?useSSL=false&characterEncoding=utf8");
                boolean mariaDriver = type == StorageType.MARIADB && classExists("org.mariadb.jdbc.Driver");
                if (mariaDriver) {
                    config.setDriverClassName("org.mariadb.jdbc.Driver");
                    config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db + params);
                } else {
                    config.setDriverClassName(classExists("com.mysql.cj.jdbc.Driver") ? "com.mysql.cj.jdbc.Driver" : "com.mysql.jdbc.Driver");
                    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + params);
                }
                config.setUsername(sql == null ? "root" : sql.getString("username", "root"));
                config.setPassword(sql == null ? "" : sql.getString("password", ""));
                ConfigurationSection pool = section.getConfigurationSection("pool");
                config.setMaximumPoolSize(pool == null ? 10 : pool.getInt("maximum-pool-size", 10));
                config.setMinimumIdle(pool == null ? 2 : pool.getInt("minimum-idle", 2));
                config.setMaxLifetime(pool == null ? 1_800_000 : pool.getLong("max-lifetime-ms", 1_800_000));
                config.setConnectionTimeout(pool == null ? 10_000 : pool.getLong("connection-timeout-ms", 10_000));
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
            }
        }
        this.dataSource = new HikariDataSource(config);

        int threads = type == StorageType.SQLITE ? 1 : Math.max(2, section.getInt("threads", 4));
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "PvPCore-DB-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
    }

    private static StorageType parseType(String raw) {
        try {
            return StorageType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return StorageType.SQLITE;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Runs work asynchronously with a pooled connection.
     *
     * @param function work
     * @param <T> result type
     * @return future of the result; SQL errors complete it exceptionally and are logged
     */
    public <T> CompletableFuture<T> query(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> run(function), executor);
    }

    /**
     * Runs work synchronously on the calling thread. Only for callers that are already off the main thread
     * (e.g. {@code AsyncPlayerPreLoginEvent}) or during startup/shutdown.
     *
     * @param function work
     * @param <T> result type
     * @return result
     */
    public <T> T run(SqlFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return function.apply(connection);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error", e);
            throw new CompletionException(e);
        }
    }

    /**
     * Executes DDL, ignoring "already exists" style errors (MySQL has no CREATE INDEX IF NOT EXISTS).
     *
     * @param connection connection
     * @param sql statement
     * @param ignoreErrors whether failures are tolerated
     * @throws SQLException when not ignored
     */
    public static void ddl(Connection connection, String sql, boolean ignoreErrors) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (!ignoreErrors) {
                throw e;
            }
        }
    }

    /** @return configured back end */
    public StorageType type() {
        return type;
    }

    /** @return SQL dialect */
    public Dialect dialect() {
        return dialect;
    }

    /**
     * @param name logical table name
     * @return prefixed table name
     */
    public String table(String name) {
        return tablePrefix + name;
    }

    /** @return executor used for storage work (callers may chain on it) */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Drains queued work (up to 10 seconds) and closes the pool. Called on disable after final saves were queued.
     */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database executor did not finish within 10s; forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }
}
