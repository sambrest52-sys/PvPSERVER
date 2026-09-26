package net.pvpserver.smoke;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Match flows beyond the basic 1v1: kit win conditions (boxing, bridge), build journaling, party fights, the 2v2
 * queue, spectators, the kit editor, rematches, the post-match inventory viewer and disconnect/reconnect handling.
 */
class MatchScenariosTest extends SmokeTestBase {

    @Test
    void boxingIsWonByTheHundredthHit() throws Exception {
        PlayerMock a = join("BoxerA");
        PlayerMock b = join("BoxerB");
        assertTrue(run(a, "queue join boxing unranked"));
        assertTrue(run(b, "queue join boxing unranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        closeIn(a, b);
        for (int i = 1; i < 100; i++) {
            assertFalse(hit(a, b, 6.0).isCancelled(), "boxing hit " + i + " registers");
        }
        assertEquals(20.0, b.getHealth(), 0.01, "boxing hits deal no damage");
        ticks(5);
        assertTrue(in("pvp_arenas", a, b), "99 hits do not end the match");
        hit(a, b, 6.0);
        awaitLobby(a, b);
        assertEquals(List.of("boxing|BoxerA|BoxerB"), matchRows());
    }

    @Test
    void bridgeGoalsScoreRoundsAndPlacedBlocksRollBack() throws Exception {
        PlayerMock a = join("BridgeA");
        PlayerMock b = join("BridgeB");
        assertTrue(run(a, "queue join bridge unranked"));
        assertTrue(run(b, "queue join bridge unranked"));
        awaitArena(a, b);
        // Team A spawns on the low-z base; goal B is the pit 7 blocks behind team B's spawn, one block down.
        PlayerMock scorer = a.getLocation().getZ() < b.getLocation().getZ() ? a : b;
        PlayerMock defender = scorer == a ? b : a;
        Location spawn = scorer.getLocation().clone();
        Location enemyGoal = defender.getLocation().clone().add(0, -1, 7);
        ticks(COUNTDOWN_TICKS);

        // Build on the bridge; the block must be journaled and removed when the arena returns to the pool.
        Location placeAt = spawn.clone().add(0, 0, 20);
        Block block = placeAt.getBlock();
        assertTrue(block.getType().isAir(), "above the bridge is air");
        BlockPlaceEvent place = scorer.simulateBlockPlace(Material.WHITE_WOOL, placeAt);
        assertFalse(place.isCancelled(), "bridge allows building");
        if (block.getType().isAir()) {
            block.setType(Material.WHITE_WOOL);
        }
        BlockPlaceEvent tooHigh = scorer.simulateBlockPlace(Material.WHITE_WOOL, spawn.clone().add(0, 30, 20));
        assertTrue(tooHigh.isCancelled(), "building above the arena's build limit is blocked");

        // Bridge kit: first to 5 goals, and the white terracotta becomes the team colour.
        assertEquals(Material.RED_TERRACOTTA, scorer.getInventory().getItem(3).getType(), "team A builds with red blocks");
        assertEquals(Material.BLUE_TERRACOTTA, defender.getInventory().getItem(3).getType(), "team B builds with blue blocks");
        for (int goal = 1; goal <= 5; goal++) {
            scorer.simulatePlayerMove(enemyGoal);
            if (goal < 5) {
                ticks(3);
                assertEquals(spawn.getBlockZ(), scorer.getLocation().getBlockZ(), "goal " + goal + " sends players back to spawn");
                assertTrue(in("pvp_arenas", a, b), "match continues after goal " + goal);
                ticks(70);
            }
        }
        awaitLobby(a, b);
        assertEquals(List.of("bridge|" + scorer.getName() + "|" + (scorer == a ? b : a).getName()), matchRows());
        await("placed block rolled back", () -> block.getType().isAir(), 3000);
    }

    @Test
    void partySplitAndPartyFfaFights() throws Exception {
        PlayerMock leader = join("PLeader");
        List<PlayerMock> members = new ArrayList<>();
        for (String name : List.of("PMemberA", "PMemberB", "PMemberC")) {
            PlayerMock member = join(name);
            assertTrue(run(leader, "party invite " + name));
            assertTrue(run(member, "party accept PLeader"));
            members.add(member);
        }
        List<PlayerMock> everyone = new ArrayList<>(members);
        everyone.add(0, leader);

        // Split: two teams of two, friendly fire off.
        assertTrue(run(leader, "party fight"));
        ticks(2);
        clickSlot(leader, 11);
        clickItem(leader, Material.ENCHANTED_GOLDEN_APPLE);
        awaitArena(everyone.toArray(PlayerMock[]::new));
        ticks(COUNTDOWN_TICKS);
        List<PlayerMock> enemies = new ArrayList<>();
        int teammates = 0;
        for (PlayerMock member : members) {
            closeIn(leader, member);
            if (hit(leader, member, 1.0).isCancelled()) {
                teammates++;
            } else {
                enemies.add(member);
            }
        }
        assertEquals(1, teammates, "exactly one party member is on the leader's team");
        for (PlayerMock enemy : enemies) {
            closeIn(leader, enemy);
            assertTrue(hit(leader, enemy, 1000).isCancelled(), "fake death");
        }
        awaitLobby(everyone.toArray(PlayerMock[]::new));
        assertEquals(Material.GOLDEN_SWORD, leader.getInventory().getItem(0).getType(), "party survives the fight");

        // Party FFA: everyone for themselves.
        assertTrue(run(leader, "party fight"));
        ticks(2);
        clickSlot(leader, 13);
        clickItem(leader, Material.ENCHANTED_GOLDEN_APPLE);
        awaitArena(everyone.toArray(PlayerMock[]::new));
        ticks(COUNTDOWN_TICKS);
        for (PlayerMock member : members) {
            closeIn(leader, member);
            assertTrue(hit(leader, member, 1000).isCancelled());
            if (member != members.get(members.size() - 1)) {
                ticks(2);
                assertTrue(in("pvp_arenas", leader), "FFA continues while two players are alive");
            }
        }
        awaitLobby(everyone.toArray(PlayerMock[]::new));
        assertTrue(matchRows().isEmpty(), "party fights do not record stats");
    }

    @Test
    void partyVersusPartyDuel() throws Exception {
        PlayerMock red = join("RedLead");
        PlayerMock redMate = join("RedMate");
        PlayerMock blue = join("BlueLead");
        PlayerMock blueMate = join("BlueMate");
        assertTrue(run(red, "party invite RedMate"));
        assertTrue(run(redMate, "party accept RedLead"));
        assertTrue(run(blue, "party invite BlueMate"));
        assertTrue(run(blueMate, "party accept BlueLead"));

        assertTrue(run(red, "duel BlueLead nodebuff"));
        ticks(2);
        clickSlot(red, 31);
        assertTrue(run(blue, "duel accept RedLead"));
        awaitArena(red, redMate, blue, blueMate);
        ticks(COUNTDOWN_TICKS);
        closeIn(red, redMate);
        assertTrue(hit(red, redMate, 1.0).isCancelled(), "no friendly fire inside a party team");
        // The whole red party forfeits: blue wins and everyone returns.
        assertTrue(run(red, "leave"));
        assertTrue(run(redMate, "leave"));
        awaitLobby(red, redMate, blue, blueMate);
    }

    @Test
    void rankedTwoVersusTwoQueueFormsTeamsAndRecordsTeamElo() throws Exception {
        List<PlayerMock> players = new ArrayList<>();
        for (String name : List.of("Duo1", "Duo2", "Duo3", "Duo4")) {
            PlayerMock player = join(name);
            assertTrue(run(player, "queue join nodebuff ranked 2v2"));
            players.add(player);
        }
        PlayerMock[] all = players.toArray(PlayerMock[]::new);
        awaitArena(all);
        ticks(COUNTDOWN_TICKS);
        PlayerMock me = players.get(0);
        PlayerMock mate = null;
        List<PlayerMock> enemies = new ArrayList<>();
        for (PlayerMock other : players.subList(1, 4)) {
            closeIn(me, other);
            if (hit(me, other, 1.0).isCancelled()) {
                mate = other;
            } else {
                enemies.add(other);
            }
        }
        assertNotNull(mate, "one queued solo was teamed with Duo1");
        assertEquals(2, enemies.size());
        for (PlayerMock enemy : enemies) {
            closeIn(me, enemy);
            hit(me, enemy, 1000);
        }
        awaitLobby(all);
        for (PlayerMock player : players) {
            player.disconnect();
        }
        waitFor(() -> false, 800);
        try (Connection connection = sqlite(); Statement statement = connection.createStatement()) {
            ResultSet match = statement.executeQuery("SELECT winners, losers, ranked FROM pvp_matches");
            assertTrue(match.next());
            assertTrue(match.getString(1).contains("Duo1") && match.getString(1).contains(mate.getName()), match.getString(1));
            assertTrue(match.getString(2).contains(enemies.get(0).getName()) && match.getString(2).contains(enemies.get(1).getName()));
            assertEquals(1, match.getInt(3));
            ResultSet elo = statement.executeQuery("SELECT p.name, s.elo FROM pvp_stats s JOIN pvp_players p ON p.uuid = s.uuid "
                    + "WHERE s.kit = 'nodebuff' ORDER BY p.name");
            int above = 0;
            int below = 0;
            while (elo.next()) {
                boolean winner = elo.getString(1).equals("Duo1") || elo.getString(1).equals(mate.getName());
                assertEquals(winner, elo.getInt(2) > 1000, elo.getString(1) + " " + elo.getInt(2));
                if (winner) {
                    above++;
                } else {
                    below++;
                }
            }
            assertEquals(2, above);
            assertEquals(2, below);
        }
    }

    @Test
    void spectatorsAreHiddenInvulnerableAndReturnWithTheMatch() throws Exception {
        PlayerMock a = join("FighterA");
        PlayerMock b = join("FighterB");
        PlayerMock watcher = join("Watcher");
        assertTrue(run(a, "queue join sumo unranked"));
        assertTrue(run(b, "queue join sumo unranked"));
        awaitArena(a, b);
        assertTrue(run(watcher, "spectate FighterA"));
        ticks(2);
        assertEquals("pvp_arenas", watcher.getWorld().getName());
        assertTrue(watcher.getAllowFlight(), "spectators fly");
        assertFalse(a.canSee(watcher), "fighters cannot see spectators");
        assertTrue(hit(a, watcher, 5).isCancelled(), "spectators cannot be hit");
        assertTrue(run(watcher, "spectate leave"));
        ticks(2);
        assertEquals("world", watcher.getWorld().getName());
        assertEquals(Material.IRON_SWORD, watcher.getInventory().getItem(0).getType(), "lobby hotbar after spectating");
        assertTrue(a.canSee(watcher));

        assertTrue(run(watcher, "spectate FighterB"));
        ticks(2);
        assertEquals("pvp_arenas", watcher.getWorld().getName());
        assertTrue(run(a, "leave"));
        awaitLobby(a, b, watcher);
    }

    @Test
    void kitEditorLayoutIsSavedAndUsedInMatches() throws Exception {
        PlayerMock a = join("Editor");
        PlayerMock b = join("Opponent");
        assertTrue(run(a, "kiteditor"));
        ticks(2);
        clickItem(a, Material.SPLASH_POTION); // NoDebuff is the first editable kit
        ItemStack first = a.getInventory().getItem(0);
        ItemStack last = a.getInventory().getItem(8);
        assertNotNull(first, "kit given for editing");
        assertNotNull(last);
        Material firstType = first.getType();
        Material lastType = last.getType();
        assertFalse(firstType == lastType, "slots 0 and 8 differ");
        a.getInventory().setItem(0, last);
        a.getInventory().setItem(8, first);
        clickSlot(a, 3); // save
        assertEquals(Material.IRON_SWORD, a.getInventory().getItem(0).getType(), "back to the lobby after saving");

        assertTrue(run(a, "queue join nodebuff unranked"));
        assertTrue(run(b, "queue join nodebuff unranked"));
        awaitArena(a, b);
        assertEquals(lastType, a.getInventory().getItem(0).getType(), "custom layout applied");
        assertEquals(firstType, a.getInventory().getItem(8).getType(), "custom layout applied");
        assertEquals(firstType, b.getInventory().getItem(0).getType(), "other players keep the default layout");
        assertTrue(run(a, "leave"));
        awaitLobby(a, b);

        a.disconnect();
        waitFor(() -> false, 500);
        try (Connection connection = sqlite(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT kit FROM pvp_kit_layouts");
            assertTrue(rs.next(), "layout persisted");
            assertEquals("nodebuff", rs.getString(1));
        }
    }

    @Test
    void inventoryViewerAndRematch() throws Exception {
        PlayerMock a = join("RematchA");
        PlayerMock b = join("RematchB");
        assertTrue(run(a, "queue join nodebuff unranked"));
        assertTrue(run(b, "queue join nodebuff unranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        drain(a);
        closeIn(b, a);
        hit(b, a, 1000);
        ticks(3);
        String command = drainClickCommand(a, "/inventory ");
        assertNotNull(command, "result message links the post-match inventories");
        assertTrue(run(a, command.substring(1)));
        ticks(2);
        assertTrue(a.getOpenInventory().getTopInventory().contains(Material.DIAMOND_SWORD), "snapshot shows the kit");
        awaitLobby(a, b);

        assertTrue(run(a, "rematch"), "rematch request sent");
        assertTrue(run(b, "rematch"), "the other player's /rematch accepts it");
        awaitArena(a, b);
        assertTrue(run(b, "leave"));
        awaitLobby(a, b);
    }

    @Test
    void disconnectForfeitsAndReconnectingPlayerStartsClean() throws Exception {
        PlayerMock a = join("Leaver");
        PlayerMock b = join("Stayer");
        assertTrue(run(a, "queue join sumo ranked"));
        assertTrue(run(b, "queue join sumo ranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        a.disconnect();
        awaitLobby(b);
        waitFor(() -> false, 500);
        try (Connection connection = sqlite(); Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT s.elo FROM pvp_stats s JOIN pvp_players p ON p.uuid = s.uuid "
                    + "WHERE p.name = 'Leaver' AND s.kit = 'sumo'");
            assertTrue(rs.next(), "the quitter's loss was saved with their profile");
            assertEquals(984, rs.getInt(1));
        }

        PlayerMock back = join("Leaver");
        assertEquals("world", back.getWorld().getName(), "reconnects land in the lobby");
        assertEquals(Material.IRON_SWORD, back.getInventory().getItem(0).getType());
        drain(back);
        assertTrue(run(back, "leave"));
        assertTrue(drain(back).stream().anyMatch(m -> m.contains("not in a match")), "no stale match or queue");
        assertTrue(run(back, "queue join sumo unranked"));
        drain(back);
        assertTrue(run(back, "queue join sumo unranked"));
        assertTrue(drain(back).stream().anyMatch(m -> m.contains("already in a queue")), "duplicate queueing rejected");
        assertTrue(run(b, "queue join sumo unranked"));
        awaitArena(back, b);
        assertTrue(run(back, "leave"));
        awaitLobby(back, b);
    }

    @Test
    void soupHealsInstantlyAndSpleefOnlyBreaksSnow() throws Exception {
        PlayerMock a = join("SoupA");
        PlayerMock b = join("SoupB");
        assertTrue(run(a, "queue join soup unranked"));
        assertTrue(run(b, "queue join soup unranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        closeIn(b, a);
        hit(b, a, 10.0);
        assertTrue(a.getHealth() < 20.0, "hit landed");
        double before = a.getHealth();
        a.getInventory().setHeldItemSlot(5);
        assertEquals(Material.MUSHROOM_STEW, a.getInventory().getItemInMainHand().getType());
        org.bukkit.event.player.PlayerInteractEvent soup = new org.bukkit.event.player.PlayerInteractEvent(a,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, a.getInventory().getItemInMainHand(), null,
                org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(soup);
        assertEquals(Math.min(20.0, before + 7.0), a.getHealth(), 0.01, "soup heals 3.5 hearts at once");
        assertTrue(a.getInventory().getItemInMainHand().getType().isAir(), "the stew is used up and leaves no bowl");
        assertTrue(run(a, "leave"));
        awaitLobby(a, b);

        assertTrue(run(a, "queue join spleef unranked"));
        assertTrue(run(b, "queue join spleef unranked"));
        awaitArena(a, b);
        ticks(COUNTDOWN_TICKS);
        Block snow = b.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        assertEquals(Material.SNOW_BLOCK, snow.getType(), "spleef floor is snow");
        assertFalse(a.simulateBlockBreak(snow).isCancelled(), "snow can be dug");
        Block wall = null;
        for (int dx = 1; dx < 20 && wall == null; dx++) {
            Block candidate = snow.getRelative(dx, 0, 0);
            if (!candidate.getType().isAir() && candidate.getType() != Material.SNOW_BLOCK) {
                wall = candidate;
            }
        }
        assertNotNull(wall, "found the arena wall");
        assertTrue(a.simulateBlockBreak(wall).isCancelled(), "anything but snow is protected (" + wall.getType() + ")");
        assertTrue(run(a, "leave"));
        awaitLobby(a, b);
        await("dug snow restored", () -> snow.getType() == Material.SNOW_BLOCK, 3000);
    }

    /** @return {@code kit|winners|losers} for every recorded match, oldest first */
    private List<String> matchRows() throws SQLException, InterruptedException {
        waitFor(() -> false, 300);
        List<String> rows = new ArrayList<>();
        try (Connection connection = sqlite(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT kit, winners, losers FROM pvp_matches ORDER BY id")) {
            while (rs.next()) {
                rows.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
            }
        }
        return rows;
    }
}
