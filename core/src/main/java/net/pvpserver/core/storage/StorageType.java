package net.pvpserver.core.storage;

/**
 * Supported storage back ends.
 */
public enum StorageType {
    /** Embedded single-file database, the default. */
    SQLITE,
    /** MySQL 8+ via the MySQL Connector/J bundled with Paper. */
    MYSQL,
    /** MariaDB; uses the MariaDB driver when present, otherwise the MySQL driver (wire compatible). */
    MARIADB
}
