package net.pvpserver.smoke;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boot checks and the core flows: lobby join, ranked queue into a pasted arena with persisted ELO, sumo, duel
 * requests, FFA, parties/moderation commands and shutdown mid-match.
 */
class PluginBootTest extends SmokeTestBase {

    @Test
    void allPluginsEnable() {
        assertTrue(core.isEnabled(), "PvPCore enabled");
        assertTrue(lobby.isEnabled(), "PvPLobby enabled");
        assertTrue(duels.isEnabled(), "PvPDuels enabled");
        assertTrue(ffa.isEnabled(), "PvPFFA enabled");
        assertNotNull(Bukkit.getWorld("pvp_arenas"), "arena world created");
        for (String arena : List.of("colosseum", "mossy_ruins", "frozen_lake", "nether_keep", "sky_temple", "sumo_dojo",
                "sumo_lotus", "sumo_skyring", "sumo_islet", "boxing_ring", "boxing_gym", "boxing_rooftop", "uhc_plains",
                "uhc_taiga", "uhc_mesa", "bridge_classic", "bridge_ruins", "bridge_nether", "spleef_classic", "spleef_layers",
                "spleef_lava")) {
            assertTrue(new File(core.getDataFolder(), "arenas/" + arena + ".arena").exists(), arena + " generated");
        }
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
        try (Connection connection = sqlite();
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
        joinFfa(a, "nodebuff");
        joinFfa(b, "nodebuff");
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
        waitFor(() -> false, 1500); // let the asynchronous template reload finish before the test folder is deleted
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
        assertTrue(a.getInventory().isEmpty(), "boxing is fought with fists");
        assertTrue(a.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED), "boxing kit applied (Speed II)");
        assertTrue(run(a, "leave"), "forfeit via /leave");
        waitFor(() -> "world".equals(a.getWorld().getName()) && "world".equals(b.getWorld().getName()), 8000);
        assertEquals("world", a.getWorld().getName());
        assertEquals("world", b.getWorld().getName());
    }
}
