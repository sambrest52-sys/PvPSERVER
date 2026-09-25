package net.pvpserver.core.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialectTest {

    @Test
    void sqliteUpsertUsesOnConflict() {
        assertEquals("INSERT INTO t (a, b, c) VALUES (?, ?, ?) ON CONFLICT(a) DO UPDATE SET b=excluded.b, c=excluded.c",
                Dialect.SQLITE.upsert("t", List.of("a", "b", "c"), List.of("a")));
    }

    @Test
    void mysqlUpsertUsesOnDuplicateKey() {
        assertEquals("INSERT INTO t (a, b, c) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE c=VALUES(c)",
                Dialect.MYSQL.upsert("t", List.of("a", "b", "c"), List.of("a", "b")));
    }
}
