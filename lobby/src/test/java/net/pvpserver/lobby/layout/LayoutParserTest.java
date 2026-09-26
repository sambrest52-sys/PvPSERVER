package net.pvpserver.lobby.layout;

import net.pvpserver.lobby.layout.LobbyLayout.Egg;
import net.pvpserver.lobby.layout.LobbyLayout.Emitter;
import net.pvpserver.lobby.layout.LobbyLayout.LaunchPad;
import net.pvpserver.lobby.layout.LobbyLayout.Parkour;
import net.pvpserver.lobby.layout.LobbyLayout.Portal;
import net.pvpserver.lobby.layout.LobbyLayout.Wall;
import net.pvpserver.lobby.layout.LobbyLayout.Zone;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * layout.yml parsing: round trips, tolerance of broken entries and the geometry helpers.
 */
class LayoutParserTest {

    static LobbyLayout sample() {
        Map<String, Point> npcs = new LinkedHashMap<>();
        npcs.put("ranked", new Point(4.5, 65, -10.5, 180, 0));
        npcs.put("ffa", Point.of(-4.5, 65, -10.5));
        return new LobbyLayout(new Point(0.5, 65, 8.5, 180, 0), 30, new LobbyLayout.Border(0, 0, 220), npcs,
                Map.of("parkour", Point.of(40.5, 67.25, 3.5)),
                List.of(new Portal("ranked", Box.of(new BlockPos(10, 65, -30), new BlockPos(12, 68, -30)), "queue-ranked", "#FFAA00")),
                List.of(new LaunchPad(new BlockPos(0, 64, 20), 0, 1.2, 2.5)),
                List.of(new LobbyLayout.Button(new BlockPos(-40, 65, 2), "kit-editor")),
                new Parkour(new BlockPos(50, 65, 0), List.of(new BlockPos(52, 67, 4), new BlockPos(55, 70, 9)), new BlockPos(60, 80, 12), 58),
                List.of(new Egg("fountain", new BlockPos(1, 60, 1)), new Egg("tree", new BlockPos(-30, 72, 5))),
                List.of(new Zone("plaza", 0.5, 0.5, 18), new Zone("sky", 5, 5, 6, 80)),
                List.of(new Emitter("fountain", Point.of(0.5, 67, 0.5))),
                new Wall(new Point(-20.5, 70, -40.5, 0, 0), 4, 3.5, 3.25));
    }

    @Test
    void writeThenReadIsLossless() throws InvalidConfigurationException {
        LobbyLayout layout = sample();
        YamlConfiguration out = new YamlConfiguration();
        LayoutParser.write(layout, out);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        List<String> warnings = new ArrayList<>();
        assertEquals(layout, LayoutParser.read(in, warnings));
        assertEquals(List.of(), warnings);
    }

    @Test
    void brokenEntriesAreSkippedWithWarnings() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                spawn: "0.5 65"
                npcs: {ranked: "1 2 3 90 0", broken: "x y z"}
                portals:
                  ok: {from: "0 64 0", to: "2 67 0", action: ffa}
                  huge: {from: "0 0 0", to: "100 100 100", action: ffa}
                  no-action: {from: "0 64 0", to: "1 65 0"}
                  fractional: {from: "0.5 64 0", to: "1 65 0", action: ffa}
                launch-pads:
                  - {at: "1 64 1", velocity: "0 1 2"}
                  - {at: "1 64 1", velocity: "0 99 0"}
                buttons: [{at: "1 65 1", action: stats}, {at: "1 65 1"}]
                parkour: {start: "0 64 0"}
                zones: {plaza: {center: "0 0", radius: -1}}
                emitters: [{at: "0 64 0"}]
                leaderboard-wall: {at: "0 70 0 90", columns: 40}
                """);
        List<String> warnings = new ArrayList<>();
        LobbyLayout layout = LayoutParser.read(yaml, warnings);

        assertEquals(LobbyLayout.empty().spawn(), layout.spawn());
        assertEquals(Map.of("ranked", new Point(1, 2, 3, 90, 0)), layout.npcs());
        assertEquals(List.of("ok"), layout.portals().stream().map(Portal::id).toList());
        assertEquals(1, layout.pads().size());
        assertEquals(List.of(new LobbyLayout.Button(new BlockPos(1, 65, 1), "stats")), layout.buttons());
        assertNull(layout.parkour());
        assertTrue(layout.zones().isEmpty());
        assertTrue(layout.emitters().isEmpty());
        assertEquals(4, layout.wall().columns());
        for (String expected : List.of("spawn", "npcs.broken", "portals.huge", "portals.no-action.action", "portals.fractional.from",
                "launch-pads[1]", "buttons[1]", "parkour", "zones.plaza", "emitters[0]", "leaderboard-wall.columns")) {
            assertTrue(warnings.stream().anyMatch(w -> w.startsWith(expected)), "warning for " + expected + " in " + warnings);
        }
    }

    @Test
    void parkourFallHeightDefaultsBelowTheLowestPlate() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                spawn: "0 65 0"
                parkour: {start: "0 70 0", checkpoints: ["3 66 0", "bad"], finish: "6 90 0"}
                """);
        List<String> warnings = new ArrayList<>();
        Parkour parkour = LayoutParser.read(yaml, warnings).parkour();
        assertEquals(62, parkour.fallY());
        assertEquals(1, parkour.checkpoints().size());
        assertEquals(1, warnings.size());
    }

    @Test
    void translateMovesEverything() {
        LobbyLayout moved = sample().translate(100, -10, 5);
        assertEquals(new Point(100.5, 55, 13.5, 180, 0), moved.spawn());
        assertEquals(20, moved.voidY());
        assertEquals(new BlockPos(110, 55, -25), moved.portals().get(0).box().min());
        assertEquals(new BlockPos(150, 55, 5), moved.parkour().start());
        assertEquals(new BlockPos(60, 55, 7), moved.buttons().get(0).at());
        assertEquals(48, moved.parkour().fallY());
        assertEquals(100.5, moved.zones().get(0).x());
        assertEquals(70, moved.zones().get(1).minY());
        assertEquals(Integer.MIN_VALUE, moved.zones().get(0).minY());
        assertEquals(100, moved.border().centerX());
        assertEquals(-35.5, moved.wall().at().z());
        assertEquals(sample(), moved.translate(-100, 10, -5));
    }

    @Test
    void pointsParseAndFormat() {
        assertEquals(new Point(1.5, 64, -3.25, 90, -10), Point.parse("1.5 64 -3.25 90 -10"));
        assertEquals(new Point(1, 2, 3, 45, 0), Point.parse("1,2,3,45"));
        assertEquals("1.5 64 -3.25 90 -10", new Point(1.5, 64, -3.25, 90, -10).format());
        assertEquals("0 65 0", Point.of(0, 65, 0).format());
        assertThrows(IllegalArgumentException.class, () -> Point.parse("1 2"));
        assertThrows(IllegalArgumentException.class, () -> Point.parse("1 2 NaN"));
        assertThrows(IllegalArgumentException.class, () -> BlockPos.parse("1 2 3.5"));
        assertEquals(new BlockPos(-1, 64, 2), Point.of(-0.5, 64.9, 2.1).block());
    }

    @Test
    void blockKeysAreUniqueForNearbyAndNegativeBlocks() {
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -64; y <= 320; y += 64) {
                for (int z = -3; z <= 3; z++) {
                    keys.add(BlockPos.key(x, y, z));
                }
            }
        }
        assertEquals(7 * 7 * 7, keys.size());
        assertNotEquals(BlockPos.key(0, 64, 1), BlockPos.key(1, 64, 0));
    }

    @Test
    void wallPanelsRunToTheViewersRight() {
        // Wall facing south (+z): viewers stand south looking north, their right is +x (east).
        Wall south = new Wall(new Point(0, 70, 0, 0, 0), 3, 4, 3);
        assertEquals(0, south.panel(0).x(), 1e-9);
        assertEquals(4, south.panel(1).x(), 1e-9);
        assertEquals(8, south.panel(2).x(), 1e-9);
        assertEquals(0, south.panel(3).x(), 1e-9);
        assertEquals(67, south.panel(3).y(), 1e-9);
        // Wall facing east (+x): viewers look west, their right is north (-z).
        Wall east = new Wall(new Point(0, 70, 0, -90, 0), 3, 4, 3);
        assertEquals(-4, east.panel(1).z(), 1e-9);
        assertEquals(0, east.panel(1).x(), 1e-9);
    }
}
