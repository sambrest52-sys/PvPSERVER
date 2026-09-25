package net.pvpserver.smoke;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerMoveEvent;
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
 * Server features outside matches: FFA combat logging, in-game arena authoring, leaderboard holograms, moderation
 * and private messaging.
 */
class ServerScenariosTest extends SmokeTestBase {

    @Test
    void ffaCombatLogCountsAsADeathForTheLastAttacker() throws Exception {
        await("FFA world", () -> Bukkit.getWorld("pvp_ffa") != null, 3000);
        PlayerMock a = join("Hunter");
        PlayerMock b = join("Logger");
        assertTrue(run(a, "ffa nodebuff"));
        assertTrue(run(b, "ffa nodebuff"));
        await("both in FFA", () -> in("pvp_ffa", a, b), 3000);
        Location spawn = a.getLocation().clone();
        a.teleport(spawn.clone().add(12, 0, 0));
        b.teleport(spawn.clone().add(13, 0, 0));
        waitFor(() -> false, 2500); // spawn protection
        assertFalse(hit(a, b, 2.0).isCancelled(), "hit outside the safe zone lands");

        // Tagged players cannot run back into the safe zone.
        assertTrue(heldBack(b.simulatePlayerMove(spawn.clone().add(1, 0, 0))), "tagged player kept out of the safe zone");

        b.disconnect();
        ticks(2);
        a.disconnect();
        waitFor(() -> false, 800);
        try (Connection connection = sqlite(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT p.name, s.ffa_kills, s.ffa_deaths FROM pvp_stats s "
                     + "JOIN pvp_players p ON p.uuid = s.uuid WHERE s.kit = 'nodebuff' ORDER BY p.name")) {
            assertTrue(rs.next());
            assertEquals("Hunter", rs.getString(1));
            assertEquals(1, rs.getInt(2), "combat log credited to the attacker");
            assertTrue(rs.next());
            assertEquals("Logger", rs.getString(1));
            assertEquals(1, rs.getInt(3), "combat log counts as a death");
        }
    }

    @Test
    void arenaCreatedInGameIsSavedAndPlayable() throws Exception {
        PlayerMock admin = join("Builder");
        admin.setOp(true);
        // Paste the sumo template into the editor world to have something to capture.
        assertTrue(run(admin, "arena edit sumo"));
        await("editor paste", () -> "pvp_editor".equals(admin.getWorld().getName()), 5000);
        // The editor teleports to spawn A, which sits at (7.5, 5, 10.5) inside the sumo template.
        Location origin = admin.getLocation().clone().subtract(7.5, 5, 10.5);
        assertTrue(run(admin, "arena cancel"));

        assertTrue(run(admin, "arena create ring"));
        admin.teleport(origin);
        assertTrue(run(admin, "arena pos1"));
        admin.teleport(origin.clone().add(20, 9, 20));
        assertTrue(run(admin, "arena pos2"));
        admin.teleport(at(origin, 7.5, 5, 10.5, -90));
        assertTrue(run(admin, "arena setspawn a"));
        admin.teleport(at(origin, 13.5, 5, 10.5, 90));
        assertTrue(run(admin, "arena setspawn b"));
        assertTrue(run(admin, "arena tag remove standard"));
        assertTrue(run(admin, "arena tag add sumo"));
        assertTrue(run(admin, "arena voidy " + (origin.getBlockY() + 1)));
        assertTrue(run(admin, "arena save"));
        File template = new File(core.getDataFolder(), "arenas/ring.arena");
        await("template written", template::exists, 5000);
        waitFor(() -> false, 300);
        YamlConfiguration arenas = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "arenas.yml"));
        assertEquals(List.of("sumo"), arenas.getStringList("arenas.ring.tags"));
        assertNotNull(arenas.getString("arenas.ring.spawn-a"));

        // With the built-in sumo arena disabled, sumo matches must use the new arena.
        assertTrue(run(admin, "arena disable sumo"));
        PlayerMock a = join("RingA");
        PlayerMock b = join("RingB");
        assertTrue(run(a, "queue join sumo unranked"));
        assertTrue(run(b, "queue join sumo unranked"));
        awaitArena(a, b);
        assertEquals(Material.SMOOTH_STONE, a.getLocation().getBlock().getRelative(BlockFace.DOWN).getType(),
                "players stand on the captured blocks");
    }

    /**
     * Paper moves the player to {@link PlayerMoveEvent#getTo()}, which plugins rewrite to push players back.
     * MockBukkit's simulation only honours cancellation, so the outcome is read from the event.
     */
    private static boolean heldBack(PlayerMoveEvent event) {
        return event.isCancelled() || event.getTo().getBlockX() == event.getFrom().getBlockX();
    }

    private static Location at(Location origin, double x, double y, double z, float yaw) {
        Location location = origin.clone().add(x, y, z);
        location.setYaw(yaw);
        location.setPitch(0);
        return location;
    }

    @Test
    void leaderboardHologramIsSpawnedAndRemoved() throws Exception {
        PlayerMock admin = join("HoloAdmin");
        admin.setOp(true);
        World world = admin.getWorld();
        // On Paper the chunk a player stands in is always loaded; MockBukkit needs it loaded explicitly.
        admin.getLocation().getChunk().load();
        assertTrue(run(admin, "lbholo create topelo global ELO"));
        ticks(5);
        List<TextDisplay> displays = world.getEntitiesByClass(TextDisplay.class).stream().toList();
        assertFalse(displays.isEmpty(), "hologram spawned");
        assertTrue(new File(lobby.getDataFolder(), "holograms.yml").exists());
        assertTrue(YamlConfiguration.loadConfiguration(new File(lobby.getDataFolder(), "holograms.yml")).contains("holograms.topelo"),
                "hologram persisted");
        assertTrue(run(admin, "lbholo delete topelo"));
        ticks(2);
        assertTrue(world.getEntitiesByClass(TextDisplay.class).stream().noneMatch(TextDisplay::isValid), "hologram removed");
    }

    @Test
    void moderationAndPrivateMessages() throws Exception {
        PlayerMock staff = join("Moderator");
        staff.setOp(true);
        PlayerMock target = join("Suspect");
        PlayerMock player = join("Witness");

        // Private messages, replies and ignoring.
        assertTrue(run(player, "msg Suspect hello there"));
        assertTrue(drain(target).stream().anyMatch(m -> m.contains("hello there")), "private message delivered");
        assertTrue(run(target, "r hi back"));
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("hi back")), "reply delivered");
        assertTrue(run(target, "ignore Witness"));
        drain(target);
        assertTrue(run(player, "msg Suspect are you there"));
        assertTrue(drain(target).stream().noneMatch(m -> m.contains("are you there")), "ignored player's message blocked");

        // Freeze stops movement until toggled off.
        assertTrue(run(staff, "freeze Suspect"));
        Location before = target.getLocation().clone();
        assertTrue(heldBack(target.simulatePlayerMove(before.clone().add(3, 0, 0))), "frozen player cannot move");
        target.teleport(before);
        assertTrue(run(staff, "freeze Suspect"));
        assertFalse(heldBack(target.simulatePlayerMove(before.clone().add(3, 0, 0))), "unfrozen player moves");

        // Vanish hides staff from players.
        assertTrue(run(staff, "vanish"));
        assertFalse(player.canSee(staff), "vanished staff hidden");
        assertTrue(run(staff, "vanish"));
        assertTrue(player.canSee(staff));

        // Reports reach online staff.
        drain(staff);
        assertTrue(run(player, "report Suspect using reach"));
        waitFor(() -> false, 300);
        assertTrue(drain(staff).stream().anyMatch(m -> m.contains("Suspect") && m.contains("using reach")), "staff alerted about the report");

        // Warnings are shown to the player; bans kick and block rejoining until pardoned.
        assertTrue(run(staff, "warn Suspect spam"));
        waitFor(() -> false, 300);
        assertTrue(drain(target).stream().anyMatch(m -> m.contains("spam")), "warning shown");
        assertTrue(run(staff, "tempban Suspect 1h hacking"));
        await("banned player kicked", () -> !target.isOnline(), 3000);
        PlayerMock rejoin = join("Suspect");
        assertFalse(rejoin.isOnline(), "banned player cannot join");
        drain(staff);
        assertTrue(run(staff, "history Suspect"));
        waitFor(() -> false, 300);
        List<String> history = drain(staff);
        assertTrue(history.stream().anyMatch(m -> m.contains("hacking")), "history lists the ban: " + history);
        assertTrue(run(staff, "unban Suspect"));
        waitFor(() -> false, 300);
        assertTrue(join("Suspect").isOnline(), "pardoned player can join again");
    }
}
