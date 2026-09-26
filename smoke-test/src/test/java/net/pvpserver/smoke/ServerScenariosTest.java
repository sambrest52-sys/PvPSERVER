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
        joinFfa(a, "nodebuff");
        joinFfa(b, "nodebuff");
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
        // Paste the lotus sumo map into the editor world to have something to capture.
        assertTrue(run(admin, "arena edit sumo_lotus"));
        await("editor paste", () -> "pvp_editor".equals(admin.getWorld().getName()), 5000);
        // The editor teleports to spawn A, which sits at (9.5, 6, 12.5) inside the 25x10x25 lotus template.
        Location origin = admin.getLocation().clone().subtract(9.5, 6, 12.5);
        assertTrue(run(admin, "arena cancel"));

        assertTrue(run(admin, "arena create ring"));
        admin.teleport(origin);
        assertTrue(run(admin, "arena pos1"));
        admin.teleport(origin.clone().add(24, 9, 24));
        assertTrue(run(admin, "arena pos2"));
        admin.teleport(at(origin, 9.5, 6, 12.5, -90));
        assertTrue(run(admin, "arena setspawn a"));
        admin.teleport(at(origin, 15.5, 6, 12.5, 90));
        assertTrue(run(admin, "arena setspawn b"));
        assertTrue(run(admin, "arena tag remove standard"));
        assertTrue(run(admin, "arena tag add sumo"));
        assertTrue(run(admin, "arena voidy " + (origin.getBlockY() + 4)));
        assertTrue(run(admin, "arena save"));
        File template = new File(core.getDataFolder(), "arenas/ring.arena");
        await("template written", template::exists, 5000);
        waitFor(() -> false, 300);
        YamlConfiguration arenas = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "arenas.yml"));
        assertEquals(List.of("sumo"), arenas.getStringList("arenas.ring.tags"));
        assertNotNull(arenas.getString("arenas.ring.spawn-a"));

        // With the built-in sumo arenas disabled, sumo matches must use the new arena.
        for (String builtin : List.of("sumo_dojo", "sumo_lotus", "sumo_skyring", "sumo_islet")) {
            assertTrue(run(admin, "arena disable " + builtin));
        }
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

    // ------------------------------------------------------------------ importing arenas

    /** A 15x4x15 quartz sumo disc (radius 6) with [A] and [B] signs, as a Sponge v2 schematic. */
    private static byte[] sumoSchematic() throws java.io.IOException {
        int w = 15;
        int h = 4;
        int l = 15;
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    double d = Math.hypot(x - 7, z - 7);
                    boolean floor = y == 1 && d <= 6;
                    boolean sign = y == 2 && z == 7 && (x == 4 || x == 10);
                    data.write(sign ? 2 : floor ? 1 : 0);
                }
            }
        }
        java.util.Map<String, Object> palette = new java.util.LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:quartz_block", 1);
        palette.put("minecraft:oak_sign[rotation=4,waterlogged=false]", 2);
        java.util.List<Object> signs = new java.util.ArrayList<>();
        for (String[] s : new String[][]{{"4", "[A]"}, {"10", "[B]"}}) {
            java.util.Map<String, Object> text = new java.util.LinkedHashMap<>();
            text.put("messages", List.of("\"" + s[1] + "\"", "\"\"", "\"\"", "\"\""));
            java.util.Map<String, Object> entity = new java.util.LinkedHashMap<>();
            entity.put("Pos", new int[]{Integer.parseInt(s[0]), 2, 7});
            entity.put("Id", "minecraft:sign");
            entity.put("front_text", text);
            signs.add(entity);
        }
        java.util.Map<String, Object> schematic = new java.util.LinkedHashMap<>();
        schematic.put("Version", 2);
        schematic.put("DataVersion", 4556);
        schematic.put("Width", (short) w);
        schematic.put("Height", (short) h);
        schematic.put("Length", (short) l);
        schematic.put("PaletteMax", 3);
        schematic.put("Palette", palette);
        schematic.put("BlockData", data.toByteArray());
        schematic.put("BlockEntities", signs);
        return nbt("Schematic", schematic);
    }

    @Test
    void importedSchematicBecomesAPlayableArena() throws Exception {
        PlayerMock admin = join("Importer");
        admin.setOp(true);
        File imports = new File(core.getDataFolder(), "imports");
        assertTrue(new File(imports, "README.txt").exists(), "import folder explains itself");
        java.nio.file.Files.write(new File(imports, "quartz_ring.schem").toPath(), sumoSchematic());

        drain(admin);
        assertTrue(run(admin, "arena import"));
        assertTrue(drain(admin).stream().anyMatch(m -> m.contains("quartz_ring.schem")), "import lists the file");
        assertTrue(run(admin, "arena import quartz_ring"));
        await("schematic pasted into the editor", () -> "pvp_editor".equals(admin.getWorld().getName()), 5000);
        assertTrue(drain(admin).stream().anyMatch(m -> m.contains("Imported quartz_ring") && m.contains("from [A]/[B] signs")),
                "spawns taken from the marker signs");
        assertEquals(Material.QUARTZ_BLOCK, admin.getLocation().getBlock().getRelative(BlockFace.DOWN).getType(),
                "teleported onto spawn A, which stands on the imported floor");
        assertTrue(admin.getLocation().getBlock().getType().isAir(), "the marker sign was removed");
        assertTrue(run(admin, "arena tag remove standard"));
        assertTrue(run(admin, "arena tag add sumo"));
        assertTrue(run(admin, "arena save"));
        await("imported template saved", () -> new File(core.getDataFolder(), "arenas/quartz_ring.arena").exists(), 5000);
        waitFor(() -> false, 300);

        for (String builtin : List.of("sumo_dojo", "sumo_lotus", "sumo_skyring", "sumo_islet")) {
            assertTrue(run(admin, "arena disable " + builtin));
        }
        PlayerMock a = join("QuartzA");
        PlayerMock b = join("QuartzB");
        drain(a);
        assertTrue(run(a, "queue join sumo unranked"));
        assertTrue(run(b, "queue join sumo unranked"));
        awaitArena(a, b);
        assertEquals(Material.QUARTZ_BLOCK, a.getLocation().getBlock().getRelative(BlockFace.DOWN).getType());
        assertTrue(Math.abs(a.getLocation().getX() - b.getLocation().getX()) > 5, "players start on the two marked spots");
        assertTrue(drain(a).stream().anyMatch(m -> m.contains("Arena: quartz_ring")), "sumo is played on the imported arena");
        assertTrue(run(a, "leave"));
        awaitLobby(a, b);

        // The one-step form saves straight into the pool.
        assertTrue(run(admin, "arena import quartz_ring.schem quick_ring save"));
        await("one-step import saved", () -> new File(core.getDataFolder(), "arenas/quick_ring.arena").exists(), 5000);
    }

    @Test
    void worldFolderImportLoadsTheWorld() throws Exception {
        PlayerMock admin = join("WorldImporter");
        admin.setOp(true);
        File worldFolder = new File(core.getDataFolder(), "imports/old_map");
        new File(worldFolder, "region").mkdirs();
        java.nio.file.Files.write(new File(worldFolder, "level.dat").toPath(), new byte[]{0});
        java.nio.file.Files.write(new File(worldFolder, "uid.dat").toPath(), new byte[]{1, 2, 3});
        assertTrue(run(admin, "arena importworld old_map"));
        await("imported world loaded", () -> Bukkit.getWorld("import_old_map") != null && "import_old_map".equals(admin.getWorld().getName()), 5000);
        File copy = new File(server.getWorldContainer(), "import_old_map");
        assertTrue(new File(copy, "level.dat").exists(), "world copied into the server");
        assertFalse(new File(copy, "uid.dat").exists(), "the copy gets its own world UUID");
        assertTrue(run(admin, "arena create captured"), "the normal editor flow continues from here");
    }

    @Test
    void leaderboardHologramIsSpawnedAndRemoved() throws Exception {
        PlayerMock admin = join("HoloAdmin");
        admin.setOp(true);
        World world = admin.getWorld();
        // On Paper the chunk a player stands in is always loaded; MockBukkit needs it loaded explicitly.
        admin.getLocation().getChunk().load();
        // The lobby has its own displays (NPC name plates, the wall...); count only what /lbholo adds.
        long before = world.getEntitiesByClass(TextDisplay.class).stream().filter(TextDisplay::isValid).count();
        assertTrue(run(admin, "lbholo create topelo global ELO"));
        ticks(5);
        assertEquals(before + 1, world.getEntitiesByClass(TextDisplay.class).stream().filter(TextDisplay::isValid).count(), "hologram spawned");
        assertTrue(new File(lobby.getDataFolder(), "holograms.yml").exists());
        assertTrue(YamlConfiguration.loadConfiguration(new File(lobby.getDataFolder(), "holograms.yml")).contains("holograms.topelo"),
                "hologram persisted");
        assertTrue(run(admin, "lbholo delete topelo"));
        ticks(2);
        assertEquals(before, world.getEntitiesByClass(TextDisplay.class).stream().filter(TextDisplay::isValid).count(), "hologram removed");
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
