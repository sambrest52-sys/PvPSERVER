package net.pvpserver.core.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Unit of database work executed on the storage executor with a pooled connection.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface SqlFunction<T> {

    /**
     * @param connection pooled connection; closed by the caller
     * @return result
     * @throws SQLException on database errors
     */
    T apply(Connection connection) throws SQLException;
}
