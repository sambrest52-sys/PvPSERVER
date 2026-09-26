package net.pvpserver.lobby.gen;

import net.pvpserver.core.arena.TemplateBuilder;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.config.NpcDefinition;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LayoutParser;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the generated hub without a server: everything players stand on or walk through is safe, every launch
 * pad lands, every parkour jump is makeable, liquids stay put and every block state parses.
 */
class HubGeneratorTest {

    private static HubBlueprint hub;
    private static TemplateBuilder b;
    private static LobbyLayout layout;

    @BeforeAll
    static void generate() {
        hub = HubGenerator.generate(1337);
        b = hub.blocks();
        layout = hub.layout();
    }

    static boolean passable(String block) {
        String id = BlockStates.id(block);
        return id.equals("air") || id.contains("grass") && !id.equals("grass_block") || id.endsWith("_banner") || id.endsWith("pressure_plate")
                || id.equals("poppy") || id.equals("dandelion") || id.equals("azure_bluet") || id.equals("oxeye_daisy")
                || id.equals("cornflower") || id.equals("allium") || id.endsWith("tulip") || id.equals("lily_of_the_valley")
                || id.endsWith("_roots") || id.endsWith("_fungus") || id.endsWith("carpet");
    }

    private static boolean solid(int x, int y, int z) {
        return !passable(b.get(x, y, z)) && !BlockStates.id(b.get(x, y, z)).equals("water");
    }

    private static void assertStandable(String what, Point point) {
        BlockPos feet = point.block();
        assertTrue(solid(feet.x(), feet.y() - 1, feet.z()) || b.get(feet.x(), feet.y(), feet.z()).contains("pressure_plate"),
                what + " has ground below " + point + " (" + b.get(feet.x(), feet.y() - 1, feet.z()) + ")");
        assertTrue(passable(b.get(feet.x(), feet.y(), feet.z())) && passable(b.get(feet.x(), feet.y() + 1, feet.z())),
                what + " has room to stand at " + point + " (" + b.get(feet.x(), feet.y(), feet.z()) + ", "
                        + b.get(feet.x(), feet.y() + 1, feet.z()) + ")");
    }

    @Test
    void generationIsDeterministic() {
        HubBlueprint again = HubGenerator.generate(1337);
        assertEquals(b.palette(), again.blocks().palette());
        assertArrayEquals(b.build().blocks(), again.blocks().build().blocks());
        assertEquals(layout, again.layout());
        HubBlueprint other = HubGenerator.generate(42);
        assertTrue(!java.util.Arrays.equals(b.build().blocks(), other.blocks().build().blocks()), "the seed changes the details");
    }

    @Test
    void theHubIsAboutOneHundredFiftyBlocksAcross() {
        assertEquals(153, (int) Math.round(HubGenerator.BARRIER_RADIUS * 2));
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (int x = 0; x < b.sizeX(); x++) {
            if (!b.isAir(x, hub.floorY(), HubGenerator.CENTER) && Math.abs(x + 0.5 - HubGenerator.MID) < HubGenerator.BARRIER_RADIUS) {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
        }
        assertTrue(maxX - minX >= 130, "walkable width " + (maxX - minX));
    }

    @Test
    void spawnNpcsAndHologramsAreWellPlaced() {
        assertStandable("spawn", layout.spawn());
        assertEquals(180f, layout.spawn().yaw(), "spawn looks at the fountain and the NPCs");
        List<String> warnings = new ArrayList<>();
        Map<String, NpcDefinition> definitions = NpcDefinition.read(bundled("npcs.yml"), warnings);
        assertEquals(definitions.keySet(), layout.npcs().keySet(), "every bundled NPC has a place");
        layout.npcs().forEach((id, point) -> assertStandable("npc " + id, point));
        for (String id : List.of("welcome", "parkour", "rules", "links", "hall-of-fame", "ranked-portal", "unranked-portal", "ffa-portal",
                "kit-editor", "cosmetics", "party")) {
            Point point = layout.holograms().get(id);
            assertNotNull(point, "hologram " + id);
            BlockPos at = point.block();
            assertTrue(b.isAir(at.x(), at.y(), at.z()), "hologram " + id + " floats in air at " + point + ": " + b.get(at.x(), at.y(), at.z()));
        }
        // Holograms, NPCs (body + text) and the wall (panel + icon per kit, 13 boards) stay well under the entity cap.
        int entities = layout.holograms().size() + layout.npcs().size() * 2 + 13 * 2;
        assertTrue(entities <= 80, "entity budget " + entities);
    }

    @Test
    void portalsAreWalkableDoorways() {
        assertEquals(Set.of("ranked", "unranked", "ffa"), Set.copyOf(layout.portals().stream().map(LobbyLayout.Portal::id).toList()));
        for (LobbyLayout.Portal portal : layout.portals()) {
            BlockPos min = portal.box().min();
            BlockPos max = portal.box().max();
            for (int x = min.x(); x <= max.x(); x++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    assertTrue(solid(x, min.y() - 1, z), portal.id() + " portal has a floor");
                    for (int y = min.y(); y <= max.y(); y++) {
                        assertTrue(b.isAir(x, y, z), portal.id() + " portal is open at " + x + " " + y + " " + z + ": " + b.get(x, y, z));
                    }
                }
            }
            assertTrue(portal.box().volume() >= 15, portal.id() + " is at least 3x5");
        }
        assertEquals("queue-ranked", layout.portals().stream().filter(p -> p.id().equals("ranked")).findFirst().orElseThrow().action());
    }

    @Test
    void everyLaunchPadLandsOnSolidGround() {
        assertTrue(layout.pads().size() >= 7, "pads " + layout.pads().size());
        for (LobbyLayout.LaunchPad pad : layout.pads()) {
            BlockPos at = pad.at();
            assertTrue(b.get(at.x(), at.y(), at.z()).contains("pressure_plate"), "pad plate at " + at.format());
            double[][] path = LaunchMath.trajectory(at.x() + 0.5, at.y(), at.z() + 0.5, new double[]{pad.vx(), pad.vy(), pad.vz()}, 200);
            BlockPos landed = null;
            double previousY = at.y();
            for (int tick = 0; tick < path.length && landed == null; tick++) {
                double[] p = path[tick];
                int x = (int) Math.floor(p[0]);
                int y = (int) Math.floor(p[1]);
                int z = (int) Math.floor(p[2]);
                boolean descending = p[1] < previousY;
                previousY = p[1];
                if (descending && solid(x, y, z)) {
                    landed = new BlockPos(x, y, z);
                    break;
                }
                assertTrue(!solid(x, y + 1, z) && (tick < 2 || !solid(x, y, z)),
                        "pad " + at.format() + " flies into " + b.get(x, y, z) + "/" + b.get(x, y + 1, z) + " at tick " + tick);
            }
            assertNotNull(landed, "pad " + at.format() + " lands");
            assertTrue(landed.y() >= hub.floorY() - 1, "pad " + at.format() + " lands at y " + landed.y());
            assertTrue(passable(b.get(landed.x(), landed.y() + 1, landed.z())), "pad " + at.format() + " lands on walkable ground");
            double r = Math.hypot(landed.x() + 0.5 - HubGenerator.MID, landed.z() + 0.5 - HubGenerator.MID);
            assertTrue(r < HubGenerator.BARRIER_RADIUS - 2, "pad " + at.format() + " stays inside the barrier");
        }
    }

    @Test
    void theParkourIsMakeable() {
        LobbyLayout.Parkour parkour = layout.parkour();
        assertNotNull(parkour);
        assertEquals(3, parkour.checkpoints().size());
        assertTrue(b.get(parkour.start().x(), parkour.start().y(), parkour.start().z()).contains("pressure_plate"));
        assertTrue(b.get(parkour.finish().x(), parkour.finish().y(), parkour.finish().z()).contains("pressure_plate"));
        parkour.checkpoints().forEach(cp -> assertTrue(b.get(cp.x(), cp.y(), cp.z()).contains("pressure_plate"), "checkpoint " + cp.format()));
        List<BlockPos> stones = hub.parkourStones();
        assertEquals(30, stones.size());
        Set<BlockPos> checkpointStones = new HashSet<>();
        parkour.checkpoints().forEach(cp -> checkpointStones.add(cp.add(0, -1, 0)));

        // Start island edge to the first stone.
        jump("start", nearest(parkour.start().add(0, -1, 0), stones.get(0), 5), 0.5, stones.get(0), half(stones.get(0), checkpointStones));
        for (int i = 0; i + 1 < stones.size(); i++) {
            jump("stone " + i, stones.get(i), half(stones.get(i), checkpointStones), stones.get(i + 1), half(stones.get(i + 1), checkpointStones));
        }
        BlockPos last = stones.get(stones.size() - 1);
        jump("summit", last, half(last, checkpointStones), nearest(parkour.finish().add(0, -1, 0), last, 7), 0.5);
        for (BlockPos stone : stones) {
            for (int dy = 1; dy <= 3; dy++) {
                assertTrue(passable(b.get(stone.x(), stone.y() + dy, stone.z())), "headroom above " + stone.format());
            }
            assertTrue(stone.y() > parkour.fallY(), "stones are above the fall height");
        }
    }

    private static double half(BlockPos stone, Set<BlockPos> checkpoints) {
        return checkpoints.contains(stone) ? 1.5 : 0.5;
    }

    /** The solid block at {@code level}'s height closest to {@code toward}. */
    private static BlockPos nearest(BlockPos level, BlockPos toward, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = level.x() - radius; x <= level.x() + radius; x++) {
            for (int z = level.z() - radius; z <= level.z() + radius; z++) {
                if (solid(x, level.y(), z)) {
                    double d = Math.hypot(x - toward.x(), z - toward.z());
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = new BlockPos(x, level.y(), z);
                    }
                }
            }
        }
        assertNotNull(best, "ground near " + level.format());
        return best;
    }

    private static void jump(String what, BlockPos from, double fromHalf, BlockPos to, double toHalf) {
        double centre = Math.hypot(to.x() - from.x(), to.z() - from.z());
        double gap = centre - fromHalf - toHalf;
        int rise = to.y() - from.y();
        assertTrue(rise <= 1, what + ": rises " + rise);
        double limit = rise > 0 ? 2.8 : 3.3;
        assertTrue(gap <= limit, what + ": gap " + String.format("%.2f", gap) + " rising " + rise + " from " + from.format() + " to " + to.format());
    }

    @Test
    void eggsButtonsAndZones() {
        assertTrue(layout.eggs().size() >= 9, "eggs " + layout.eggs().size());
        Set<String> ids = new HashSet<>();
        for (LobbyLayout.Egg egg : layout.eggs()) {
            assertTrue(ids.add(egg.id()), "unique egg id " + egg.id());
            String block = BlockStates.id(b.get(egg.at().x(), egg.at().y(), egg.at().z()));
            assertTrue(Set.of("dragon_egg", "sniffer_egg", "turtle_egg").contains(block), egg.id() + " is an egg block: " + block);
            String above = BlockStates.id(b.get(egg.at().x(), egg.at().y() + 1, egg.at().z()));
            assertTrue(above.equals("air") || above.equals("water"), egg.id() + " can be seen and clicked from above: " + above);
        }
        assertTrue(layout.buttons().size() >= 8, "buttons " + layout.buttons().size());
        for (LobbyLayout.Button button : layout.buttons()) {
            assertTrue(!b.isAir(button.at().x(), button.at().y(), button.at().z()), "button block at " + button.at().format());
        }
        assertEquals(List.of("plaza", "ranked", "ffa", "leaderboards", "cosmetics", "info", "party", "kit-editor", "unranked", "parkour"),
                layout.zones().stream().map(LobbyLayout.Zone::id).toList());
        LobbySettings settings = LobbySettings.read(bundled("config.yml"), new ArrayList<>());
        for (LobbyLayout.Zone zone : layout.zones()) {
            assertTrue(settings.zones().names().containsKey(zone.id()), "zone " + zone.id() + " has a name in config.yml");
        }
        for (LobbyLayout.Emitter emitter : layout.emitters()) {
            assertTrue(settings.ambient().emitters().containsKey(emitter.type()), "emitter type " + emitter.type() + " exists in config.yml");
        }
        assertNotNull(layout.wall());
        assertEquals(5, layout.wall().columns());
        for (int i = 0; i < 15; i++) {
            BlockPos panel = layout.wall().panel(i).block();
            assertTrue(b.isAir(panel.x(), panel.y(), panel.z()), "wall panel " + i + " floats in front of the wall");
        }
    }

    @Test
    void theBarrierEnclosesThePlayableArea() {
        int floor = hub.floorY();
        for (int step = 0; step < 360; step += 3) {
            double rad = Math.toRadians(step);
            int x = (int) Math.floor(HubGenerator.MID + (HubGenerator.BARRIER_RADIUS - 0.5) * Math.sin(rad));
            int z = (int) Math.floor(HubGenerator.MID - (HubGenerator.BARRIER_RADIUS - 0.5) * Math.cos(rad));
            for (int y = floor - 2; y < b.sizeY(); y++) {
                assertTrue(!b.isAir(x, y, z), "barrier gap at " + x + " " + y + " " + z);
            }
        }
        assertTrue(layout.voidY() < floor - 5, "void height below the islands' tops");
    }

    @Test
    void liquidsAreContained() {
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        int water = 0;
        for (int x = 0; x < b.sizeX(); x++) {
            for (int y = 1; y < b.sizeY(); y++) {
                for (int z = 0; z < b.sizeZ(); z++) {
                    if (!BlockStates.id(b.get(x, y, z)).equals("water")) {
                        continue;
                    }
                    water++;
                    for (int[] d : around) {
                        String next = b.get(x + d[0], y + d[1], z + d[2]);
                        assertTrue(!b.isAir(x + d[0], y + d[1], z + d[2]) && !passable(next) || BlockStates.id(next).equals("water"),
                                "water at " + x + " " + y + " " + z + " can flow into " + next);
                    }
                }
            }
        }
        assertTrue(water > 50, "the fountain and pools hold water");
    }

    @Test
    void blockIdsExistAndStatesAreWellFormed() {
        List<String> problems = new ArrayList<>();
        for (String block : b.palette()) {
            String problem = BlockStates.check(block);
            if (problem != null) {
                problems.add(block + " -> " + problem);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theLayoutSurvivesLayoutYml() throws InvalidConfigurationException {
        LobbyLayout world = hub.worldLayout(64);
        assertEquals(64 + 1, (int) world.spawn().y());
        assertEquals(0.5, world.spawn().x());
        YamlConfiguration out = new YamlConfiguration();
        LayoutParser.write(world, out);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        List<String> warnings = new ArrayList<>();
        assertEquals(world, LayoutParser.read(in, warnings));
        assertEquals(List.of(), warnings);
    }

    static YamlConfiguration bundled(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(HubGeneratorTest.class.getResourceAsStream("/" + name), name), StandardCharsets.UTF_8));
    }
}
