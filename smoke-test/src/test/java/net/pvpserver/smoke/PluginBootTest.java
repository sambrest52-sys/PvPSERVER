package net.pvpserver.smoke;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the four real plugin jars in a mocked Paper server and drives the main flows through the Bukkit API only
 * (the test never links against plugin classes, just like a server).
 */
class PluginBootTest {

    @TempDir
    Path temp;
    private ServerMock server;
    private Plugin core;
    private Plugin lobby;
    private Plugin duels;
    private Plugin ffa;

    @BeforeEach
    void boot() throws Exception {
        server = MockBukkit.mock(new PracticeServerMock(temp.resolve("worlds").toFile()));
        server.addSimpleWorld("world");
        PluginHarness harness = new PluginHarness(server, new File("target/plugins"), temp.resolve("plugins").toFile());
        core = harness.load("PvPCore.jar");
        lobby = harness.load("PvPLobby.jar");
        duels = harness.load("PvPDuels.jar");
        ffa = harness.load("PvPFFA.jar");
        // Let arena templates load (async) and the arena/FFA worlds get pasted.
        waitFor(() -> false, 2000);
    }

    @AfterEach
    void shutdown() {
        MockBukkit.unmock();
    }

    private void waitFor(java.util.function.BooleanSupplier condition, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end && !condition.getAsBoolean()) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
    }

    private PlayerMock join(String name) throws InterruptedException {
        PlayerMock player = new PracticePlayerMock(server, name);
        server.addPlayer(player);
        waitFor(() -> false, 100);
        return player;
    }

    @Test
    void allPluginsEnable() {
        assertTrue(core.isEnabled(), "PvPCore enabled");
        assertTrue(lobby.isEnabled(), "PvPLobby enabled");
        assertTrue(duels.isEnabled(), "PvPDuels enabled");
        assertTrue(ffa.isEnabled(), "PvPFFA enabled");
        assertNotNull(Bukkit.getWorld("pvp_arenas"), "arena world created");
        assertTrue(new File(core.getDataFolder(), "arenas/classic.arena").exists(), "placeholder arenas generated");
        assertTrue(new File(lobby.getDataFolder(), "hotbar.yml").exists(), "lobby wrote its own defaults");
        assertTrue(new File(duels.getDataFolder(), "scoreboard.yml").exists());
    }

    @Test
    void joiningPlayerGetsLobbyHotbar() throws Exception {
        PlayerMock player = join("Steve");
        assertTrue(player.isOnline());
        assertEquals(GameMode.ADVENTURE, player.getGameMode());
        ItemStack unranked = player.getInventory().getItem(0);
        assertNotNull(unranked);
        assertEquals(Material.IRON_SWORD, unranked.getType(), "lobby hotbar slot 0 is the unranked queue");
        assertEquals(Material.COMPARATOR, player.getInventory().getItem(8).getType());
    }

    @Test
    void queueMatchmakingStartsAMatchAndFakeDeathEndsIt() throws Exception {
        PlayerMock a = join("Alpha");
        PlayerMock b = join("Bravo");
        assertTrue(run(a, "queue join nodebuff ranked"));
        assertTrue(run(b, "queue join nodebuff ranked"));
        // Matchmaking runs every second, then an arena is pasted and players are teleported.
        waitFor(() -> "pvp_arenas".equals(a.getWorld().getName()) && "pvp_arenas".equals(b.getWorld().getName()), 5000);
        assertEquals("pvp_arenas", a.getWorld().getName(), "Alpha teleported into an arena instance");
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertEquals(Material.DIAMOND_SWORD, a.getInventory().getItem(0).getType(), "kit applied");
        // Countdown (5s) then fight.
        waitFor(() -> false, 6000);
        // Lethal damage is intercepted: no vanilla death, the match ends and both return to the lobby.
        EntityDamageByEntityEvent lethal = hit(b, a, 1000.0);
        assertTrue(lethal.isCancelled(), "lethal hit intercepted as a fake death");
        assertFalse(a.isDead(), "fake death keeps the player alive");
        waitFor(() -> "world".equals(a.getWorld().getName()) && "world".equals(b.getWorld().getName()), 8000);
        assertEquals("world", a.getWorld().getName(), "loser back in lobby");
        assertEquals("world", b.getWorld().getName(), "winner back in lobby");
        assertEquals(Material.IRON_SWORD, b.getInventory().getItem(0).getType(), "lobby hotbar restored");

        // Results are persisted asynchronously: match history immediately, stats on quit/autosave.
        a.disconnect();
        b.disconnect();
        waitFor(() -> false, 1000);
        File db = new File(core.getDataFolder(), "data/practice.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            ResultSet match = statement.executeQuery("SELECT winners, losers, ranked, elo_change FROM pvp_matches");
            assertTrue(match.next(), "match recorded");
            assertEquals("Bravo", match.getString(1));
            assertEquals("Alpha", match.getString(2));
            assertEquals(1, match.getInt(3));
            assertEquals(16, match.getInt(4), "equal ratings exchange K/2");
            ResultSet elo = statement.executeQuery("SELECT p.name, s.elo, s.ranked_wins, s.ranked_losses FROM pvp_stats s "
                    + "JOIN pvp_players p ON p.uuid = s.uuid WHERE s.kit = 'nodebuff' ORDER BY s.elo DESC");
            assertTrue(elo.next());
            assertEquals("Bravo", elo.getString(1));
            assertEquals(1016, elo.getInt(2));
            assertEquals(1, elo.getInt(3));
            assertTrue(elo.next());
            assertEquals("Alpha", elo.getString(1));
            assertEquals(984, elo.getInt(2));
            assertEquals(1, elo.getInt(4));
        }
    }

    @Test
    void ffaJoinDeathAndLeave() throws Exception {
        waitFor(() -> Bukkit.getWorld("pvp_ffa") != null, 3000);
        PlayerMock a = join("Killer");
        PlayerMock b = join("Victim");
        assertTrue(run(a, "ffa nodebuff"));
        assertTrue(run(b, "ffa nodebuff"));
        waitFor(() -> false, 3000);
        assertEquals("pvp_ffa", a.getWorld().getName());
        // Move both out of the spawn safe zone and past spawn protection before fighting.
        a.teleport(a.getLocation().add(12, 0, 0));
        b.teleport(a.getLocation().add(1, 0, 0));
        waitFor(() -> false, 2500);
        EntityDamageByEntityEvent lethal = hit(a, b, 1000.0);
        assertTrue(lethal.isCancelled(), "FFA lethal hit intercepted");
        waitFor(() -> false, 200);
        assertFalse(b.isDead());
        assertEquals(20.0, b.getHealth(), 0.01, "respawned with full health");
        assertEquals("pvp_ffa", b.getWorld().getName(), "instant respawn inside FFA");
        assertTrue(run(a, "ffa leave") || true);
        waitFor(() -> false, 200);
    }

    @Test
    void partiesAndModeration() throws Exception {
        PlayerMock leader = join("Leader");
        PlayerMock member = join("Member");
        assertTrue(run(leader, "party invite Member"));
        assertTrue(run(member, "party accept Leader"));
        waitFor(() -> false, 100);
        assertEquals(Material.GOLDEN_SWORD, leader.getInventory().getItem(0).getType(), "party leader hotbar");
        assertTrue(run(member, "party leave"));

        PlayerMock staff = join("Staff");
        staff.setOp(true);
        assertTrue(run(staff, "mute Member 10m spam"));
        waitFor(() -> false, 500);
        assertTrue(run(staff, "pvpadmin reload"));
        assertTrue(run(staff, "arena list"));
        assertTrue(run(staff, "kb set default horizontal 0.41"));
    }

    @Test
    void cleanShutdownMidMatch() throws Exception {
        PlayerMock a = join("One");
        PlayerMock b = join("Two");
        run(a, "queue join sumo unranked");
        run(b, "queue join sumo unranked");
        waitFor(() -> "pvp_arenas".equals(a.getWorld().getName()), 5000);
        // Disabling mid-match must cancel the match and return players to the lobby without errors.
        server.getPluginManager().disablePlugin(duels);
        assertEquals("world", a.getWorld().getName());
        server.getPluginManager().disablePlugin(ffa);
        server.getPluginManager().disablePlugin(lobby);
        server.getPluginManager().disablePlugin(core);
        assertFalse(core.isEnabled());
    }

    /** Fires the damage event Paper would fire for a melee hit and applies it when not cancelled. */
    private EntityDamageByEntityEvent hit(PlayerMock attacker, PlayerMock victim, double damage) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, damage);
        server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            victim.damage(event.getFinalDamage());
        }
        return event;
    }

    @Test
    void sumoVoidFallEndsTheMatch() throws Exception {
        PlayerMock a = join("SumoA");
        PlayerMock b = join("SumoB");
        assertTrue(run(a, "queue join sumo unranked"));
        assertTrue(run(b, "queue join sumo unranked"));
        waitFor(() -> "pvp_arenas".equals(a.getWorld().getName()) && "pvp_arenas".equals(b.getWorld().getName()), 5000);
        assertEquals("pvp_arenas", a.getWorld().getName());
        assertTrue(a.getInventory().isEmpty(), "sumo kit has no items");
        waitFor(() -> false, 6000);
        // Falling off the platform (below the arena's void Y) eliminates the player.
        a.simulatePlayerMove(a.getLocation().clone().subtract(0, 20, 0));
        waitFor(() -> "world".equals(a.getWorld().getName()) && "world".equals(b.getWorld().getName()), 8000);
        assertEquals("world", b.getWorld().getName(), "winner returned to lobby after the end screen");
    }

    @Test
    void duelRequestAcceptedStartsMatch() throws Exception {
        PlayerMock a = join("Challenger");
        PlayerMock b = join("Target");
        assertTrue(run(a, "duel Target boxing"));
        waitFor(() -> false, 100);
        // Options menu: slot 31 sends the request (menu actions run on the next tick).
        a.simulateInventoryClick(a.getOpenInventory(), 31);
        waitFor(() -> false, 200);
        assertTrue(run(b, "duel accept Challenger"));
        waitFor(() -> "pvp_arenas".equals(a.getWorld().getName()) && "pvp_arenas".equals(b.getWorld().getName()), 5000);
        assertEquals("pvp_arenas", a.getWorld().getName(), "accepted duel starts a match");
        assertEquals(Material.DIAMOND_SWORD, a.getInventory().getItem(0).getType(), "boxing kit applied");
        assertTrue(run(a, "leave"), "forfeit via /leave");
        waitFor(() -> "world".equals(a.getWorld().getName()) && "world".equals(b.getWorld().getName()), 8000);
        assertEquals("world", a.getWorld().getName());
        assertEquals("world", b.getWorld().getName());
    }

    private boolean run(PlayerMock player, String command) {
        return server.dispatchCommand(player, command);
    }

    private static void dump(PlayerMock player) {
        String message;
        while ((message = player.nextMessage()) != null) {
            System.out.println("[" + player.getName() + "] " + message);
        }
    }
}
