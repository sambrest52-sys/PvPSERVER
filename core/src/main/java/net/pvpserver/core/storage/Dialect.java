package net.pvpserver.core.storage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL differences between SQLite and MySQL/MariaDB.
 */
public enum Dialect {
    SQLITE {
        @Override
        public String autoIncrementPrimaryKey() {
            return "INTEGER PRIMARY KEY AUTOINCREMENT";
        }

        @Override
        public String upsert(String table, List<String> columns, List<String> keys) {
            String updates = columns.stream().filter(c -> !keys.contains(c))
                    .map(c -> c + "=excluded." + c).collect(Collectors.joining(", "));
            return insertPrefix(table, columns) + " ON CONFLICT(" + String.join(", ", keys) + ") DO UPDATE SET " + updates;
        }
    },
    MYSQL {
        @Override
        public String autoIncrementPrimaryKey() {
            return "BIGINT AUTO_INCREMENT PRIMARY KEY";
        }

        @Override
        public String upsert(String table, List<String> columns, List<String> keys) {
            String updates = columns.stream().filter(c -> !keys.contains(c))
                    .map(c -> c + "=VALUES(" + c + ")").collect(Collectors.joining(", "));
            return insertPrefix(table, columns) + " ON DUPLICATE KEY UPDATE " + updates;
        }
    };

    /** @return column definition for an auto-increment primary key */
    public abstract String autoIncrementPrimaryKey();

    /**
     * Builds an insert-or-update statement.
     *
     * @param table table name
     * @param columns all columns in bind order
     * @param keys conflict key columns
     * @return SQL with one {@code ?} per column
     */
    public abstract String upsert(String table, List<String> columns, List<String> keys);

    /**
     * @param index index name
     * @param table table
     * @param columns indexed columns
     * @return statement creating the index; MySQL has no IF NOT EXISTS so failures are ignored by the caller
     */
    public String createIndex(String index, String table, String columns) {
        return this == SQLITE
                ? "CREATE INDEX IF NOT EXISTS " + index + " ON " + table + " (" + columns + ")"
                : "CREATE INDEX " + index + " ON " + table + " (" + columns + ")";
    }

    private static String insertPrefix(String table, List<String> columns) {
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
    }
}
