package net.pvpserver.core.storage;

import net.pvpserver.core.moderation.Punishment;
import net.pvpserver.core.moderation.PunishmentType;
import net.pvpserver.core.moderation.Report;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.profile.TimeOfDay;
import net.pvpserver.core.stats.KitStats;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.MatchRecord;
import net.pvpserver.core.stats.StatField;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the SQL repositories against a real SQLite database (schema, upserts, queries).
 */
class SqliteStorageTest {

    @TempDir
    Path folder;
    private Database database;
    private Storage storage;

    @BeforeEach
    void open() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "SQLITE");
        config.set("sqlite.file", "test.db");
        database = new Database(config, folder.toFile(), Logger.getLogger("test"));
        storage = new Storage(database, "default", 1000);
        // Schema creation must be idempotent across restarts.
        Schema.create(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void profileIsCreatedThenLoadedWithSettings() {
        UUID id = UUID.randomUUID();
        PlayerProfile created = storage.profiles().loadOrCreateBlocking(id, "Steve");
        assertEquals("default", created.rankId());
        created.settings().set(Setting.SCOREBOARD, false);
        created.settings().timeOfDay(TimeOfDay.NIGHT);
        created.cosmetics().killEffect("lightning");
        UUID ignored = UUID.randomUUID();
        created.ignored().add(ignored);
        created.rankId("vip");
        storage.profiles().save(created).join();

        PlayerProfile loaded = storage.profiles().loadOrCreateBlocking(id, "Steve");
        assertFalse(loaded.settings().is(Setting.SCOREBOARD));
        assertTrue(loaded.settings().is(Setting.DUEL_REQUESTS));
        assertEquals(TimeOfDay.NIGHT, loaded.settings().timeOfDay());
        assertEquals("lightning", loaded.cosmetics().killEffect());
        assertTrue(loaded.ignored().contains(ignored));
        assertEquals("vip", loaded.rankId());
        assertEquals(Optional.of(id), storage.profiles().findUuid("steve").join());
        assertEquals(Optional.of("Steve"), storage.profiles().findName(id).join());
    }

    @Test
    void statsUpsertAndLeaderboards() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storage.profiles().loadOrCreateBlocking(a, "Alpha");
        storage.profiles().loadOrCreateBlocking(b, "Bravo");

        KitStats alpha = new KitStats("nodebuff", 1000);
        alpha.elo(1200);
        alpha.recordDuel(true, true);
        alpha.recordDuel(true, true);
        KitStats bravo = new KitStats("nodebuff", 1000);
        bravo.elo(1100);
        bravo.recordDuel(true, false);
        bravo.recordFfaKill(3);
        storage.stats().save(a, List.of(alpha)).join();
        storage.stats().save(b, List.of(bravo)).join();
        // Second save updates instead of inserting a duplicate.
        alpha.elo(1250);
        storage.stats().save(a, List.of(alpha)).join();

        Map<String, KitStats> loaded = storage.stats().load(a).join();
        assertEquals(1250, loaded.get("nodebuff").elo());
        assertEquals(2, loaded.get("nodebuff").rankedWins());
        assertEquals(2, loaded.get("nodebuff").bestWinStreak());

        List<LeaderboardEntry> top = storage.stats().top("nodebuff", StatField.ELO, 10).join();
        assertEquals(2, top.size());
        assertEquals("Alpha", top.get(0).name());
        assertEquals(1250, top.get(0).value());
        List<LeaderboardEntry> kills = storage.stats().top(null, StatField.FFA_KILLS, 10).join();
        assertEquals("Bravo", kills.get(0).name());

        storage.stats().reset(a, "nodebuff").join();
        assertTrue(storage.stats().load(a).join().isEmpty());
    }

    @Test
    void punishmentsLifecycle() {
        UUID target = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Punishment temp = new Punishment(0, target, "Cheater", PunishmentType.BAN, "Reach", null, "CONSOLE", now,
                now + 60_000, true, null, 0, null);
        Punishment saved = storage.punishments().insert(temp).join();
        assertTrue(saved.id() > 0);
        assertTrue(storage.punishments().findActive(target, PunishmentType.BAN, now).join().isPresent());
        assertFalse(storage.punishments().findActive(target, PunishmentType.BAN, now + 120_000).join().isPresent(), "expired");
        assertFalse(storage.punishments().findActive(target, PunishmentType.MUTE, now).join().isPresent());

        Punishment permanent = new Punishment(0, target, "Cheater", PunishmentType.BAN, "Again", null, "CONSOLE", now + 1,
                Punishment.PERMANENT, true, null, 0, null);
        storage.punishments().insert(permanent).join();
        assertEquals(Punishment.PERMANENT,
                storage.punishments().findActiveBlocking(target, PunishmentType.BAN, now).orElseThrow().expiresAt(),
                "permanent bans take precedence");

        assertEquals(2, storage.punishments().deactivate(target, PunishmentType.BAN, "Admin", "appeal").join());
        assertFalse(storage.punishments().findActive(target, PunishmentType.BAN, now).join().isPresent());
        List<Punishment> history = storage.punishments().history(target, 10).join();
        assertEquals(2, history.size());
        assertEquals("Admin", history.get(0).removedBy());
    }

    @Test
    void layoutsReportsAndHistory() {
        UUID id = UUID.randomUUID();
        storage.layouts().save(id, "nodebuff", "0:1,1:0").join();
        storage.layouts().save(id, "nodebuff", "0:2").join();
        assertEquals(Map.of("nodebuff", "0:2"), storage.layouts().loadBlocking(id));
        storage.layouts().save(id, "nodebuff", null).join();
        assertTrue(storage.layouts().loadBlocking(id).isEmpty());

        Report report = storage.reports().insert(new Report(0, id, "Alice", UUID.randomUUID(), "Bob", "kill aura", 5)).join();
        assertTrue(report.id() > 0);
        assertEquals("kill aura", storage.reports().recent(5).join().get(0).reason());

        storage.matches().insert(new MatchRecord("sumo", "QUEUE", true, "Alice", "Bob", 16, 30_000, 10)).join();
        assertEquals(1, storage.matches().recent("Bob", 5).join().size());
        assertEquals(0, storage.matches().recent("Carol", 5).join().size());
    }
}
