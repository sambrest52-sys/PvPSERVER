package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.RelativePosition;
import net.pvpserver.core.arena.TemplateBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates every built-in arena without a server: playable spawns, contained liquids, valid block ids and states,
 * sizes that fit the instance grid, bridge goals, and enough arenas for every kit.
 */
class BuiltinArenasTest {

    private static final Map<String, GeneratedArena> ARENAS = new HashMap<>();

    @BeforeAll
    static void generateAll() {
        for (String name : BuiltinArenas.names()) {
            ARENAS.put(name, BuiltinArenas.generate(name));
        }
    }

    private static boolean passable(String block) {
        String id = id(block);
        return id.equals("air") || id.equals("snow") || id.equals("short_grass") || id.equals("fern");
    }

    private static boolean solidGround(String block) {
        String id = id(block);
        return !passable(block) && !id.equals("water") && !id.equals("lava") && !id.equals("barrier")
                && !id.contains("carpet") && !id.equals("lily_pad");
    }

    private static String id(String block) {
        int bracket = block.indexOf('[');
        String id = bracket < 0 ? block : block.substring(0, bracket);
        return id.substring(id.indexOf(':') + 1);
    }

    @Test
    void thereAreAtLeastThreeArenasForEveryArenaTagAndTwentyInTotal() {
        assertTrue(ARENAS.size() >= 20, "arena count " + ARENAS.size());
        Map<String, Integer> perTag = new HashMap<>();
        ARENAS.values().forEach(a -> a.tags().forEach(t -> perTag.merge(t, 1, Integer::sum)));
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("kits.yml")), StandardCharsets.UTF_8));
        ConfigurationSection root = kits.getConfigurationSection("kits");
        for (String kit : root.getKeys(false)) {
            int available = 0;
            List<String> blacklist = root.getStringList(kit + ".arena-blacklist");
            for (GeneratedArena arena : ARENAS.values()) {
                if (!blacklist.contains(arena.name())
                        && arena.tags().stream().anyMatch(root.getStringList(kit + ".arena-tags")::contains)) {
                    available++;
                }
            }
            assertTrue(available >= 3, kit + " has only " + available + " arenas");
            for (String name : blacklist) {
                assertTrue(ARENAS.containsKey(name), kit + " blacklists unknown arena " + name);
            }
        }
        for (String tag : List.of("standard", "sumo", "boxing", "build", "bridge", "spleef")) {
            assertTrue(perTag.getOrDefault(tag, 0) >= 3, tag + ": " + perTag.get(tag));
        }
    }

    @Test
    void generationIsDeterministic() {
        for (String name : List.of("mossy_ruins", "uhc_taiga", "bridge_ruins")) {
            TemplateBuilder a = BuiltinArenas.generate(name).blocks();
            TemplateBuilder b = BuiltinArenas.generate(name).blocks();
            assertEquals(a.palette(), b.palette(), name);
            assertTrue(java.util.Arrays.equals(a.build().blocks(), b.build().blocks()), name);
        }
    }

    @Test
    void spawnsAreSafeAndApart() {
        for (GeneratedArena arena : ARENAS.values()) {
            TemplateBuilder b = arena.blocks();
            for (RelativePosition spawn : List.of(arena.spawnA(), arena.spawnB())) {
                assertNotNull(spawn, arena.name());
                int x = (int) Math.floor(spawn.x());
                int y = (int) Math.floor(spawn.y());
                int z = (int) Math.floor(spawn.z());
                String where = arena.name() + " spawn " + x + "," + y + "," + z;
                assertTrue(b.inside(x, y, z), where + " inside template");
                assertTrue(solidGround(b.get(x, y - 1, z)), where + " stands on " + b.get(x, y - 1, z));
                assertTrue(passable(b.get(x, y, z)), where + " feet in " + b.get(x, y, z));
                assertTrue(passable(b.get(x, y + 1, z)), where + " head in " + b.get(x, y + 1, z));
                assertTrue(arena.voidY() < y - 1, where + " is above the void Y " + arena.voidY());
                assertTrue(arena.buildLimit() >= y - 1, where + " is below the build limit");
            }
            double dx = arena.spawnA().x() - arena.spawnB().x();
            double dz = arena.spawnA().z() - arena.spawnB().z();
            assertTrue(Math.sqrt(dx * dx + dz * dz) >= 4, arena.name() + " spawns too close");
            assertNotNull(arena.spectator(), arena.name() + " spectator");
        }
    }

    @Test
    void sizesFitTheInstanceGrid() {
        for (GeneratedArena arena : ARENAS.values()) {
            TemplateBuilder b = arena.blocks();
            assertTrue(b.sizeX() <= 128 && b.sizeZ() <= 128 && b.sizeY() <= 64, arena.name() + " size");
            int solid = arena.template().solidIndices().length;
            assertTrue(solid > 500 && solid < 150_000, arena.name() + " has " + solid + " blocks");
        }
    }

    @Test
    void liquidsAreContained() {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (GeneratedArena arena : ARENAS.values()) {
            TemplateBuilder b = arena.blocks();
            for (int y = 0; y < b.sizeY(); y++) {
                for (int z = 0; z < b.sizeZ(); z++) {
                    for (int x = 0; x < b.sizeX(); x++) {
                        String id = id(b.get(x, y, z));
                        if (!id.equals("water") && !id.equals("lava")) {
                            continue;
                        }
                        String where = arena.name() + " " + id + " at " + x + "," + y + "," + z;
                        assertTrue(y == 0 || !b.isAir(x, y - 1, z), where + " has nothing below");
                        for (int[] s : sides) {
                            int nx = x + s[0];
                            int nz = z + s[1];
                            assertTrue(b.inside(nx, y, nz), where + " touches the template edge");
                            assertFalse(b.isAir(nx, y, nz), where + " can flow sideways");
                        }
                    }
                }
            }
        }
    }

    @Test
    void bridgeGoalsArePitsWithAFloor() {
        for (GeneratedArena arena : ARENAS.values()) {
            if (!arena.tags().contains("bridge")) {
                assertEquals(null, arena.goalA(), arena.name());
                continue;
            }
            assertNotNull(arena.buildArea(), arena.name() + " limits building");
            for (RelativePosition goal : List.of(arena.goalA(), arena.goalB())) {
                int x = (int) Math.floor(goal.x());
                int y = (int) Math.floor(goal.y());
                int z = (int) Math.floor(goal.z());
                assertTrue(arena.blocks().isAir(x, y, z) && arena.blocks().isAir(x, y - 1, z), arena.name() + " goal is open");
                assertFalse(arena.blocks().isAir(x, y - 2, z), arena.name() + " goal has a floor");
            }
            assertNotEquals(arena.goalA().z(), arena.goalB().z());
            // Team A (spawn A) must be closer to goal A, which it defends.
            assertTrue(Math.abs(arena.spawnA().z() - arena.goalA().z()) < Math.abs(arena.spawnA().z() - arena.goalB().z()));
        }
    }

    @Test
    void blockIdsExistAndStatesAreWellFormed() {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GeneratedArena arena : ARENAS.values()) {
            for (String block : arena.blocks().palette()) {
                if (!seen.add(block)) {
                    continue;
                }
                String problem = checkState(block);
                if (problem != null) {
                    problems.add(arena.name() + ": " + block + " -> " + problem);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertEquals(null, GeneratedArenaIcons.check(ARENAS.values()), "all icons resolve");
    }

    /** Minimal block-state schema for the families the generator uses. */
    private static String checkState(String block) {
        String id = id(block);
        Material material = Material.matchMaterial(id);
        if (material == null) {
            return "unknown block id";
        }
        int bracket = block.indexOf('[');
        if (bracket < 0) {
            return null;
        }
        String props = block.substring(bracket + 1, block.length() - 1);
        Map<String, Set<String>> allowed = new HashMap<>();
        Set<String> bool = Set.of("true", "false");
        Set<String> horizontal = Set.of("north", "east", "south", "west");
        if (id.endsWith("_stairs")) {
            allowed.put("facing", horizontal);
            allowed.put("half", Set.of("top", "bottom"));
            allowed.put("shape", Set.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"));
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("_slab")) {
            allowed.put("type", Set.of("top", "bottom", "double"));
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("_log") || id.endsWith("_pillar") || id.contains("basalt") || id.equals("hay_block")
                || id.endsWith("chain") || id.endsWith("froglight")) {
            allowed.put("axis", Set.of("x", "y", "z"));
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("_leaves")) {
            allowed.put("persistent", bool);
            allowed.put("distance", Set.of("1", "2", "3", "4", "5", "6", "7"));
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("lantern") && !id.equals("sea_lantern") && !id.equals("jack_o_lantern")) {
            allowed.put("hanging", bool);
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("_fence") || id.endsWith("_pane") || id.equals("iron_bars")) {
            horizontal.forEach(d -> allowed.put(d, bool));
            allowed.put("waterlogged", bool);
        } else if (id.endsWith("_wall")) {
            horizontal.forEach(d -> allowed.put(d, Set.of("none", "low", "tall")));
            allowed.put("up", bool);
            allowed.put("waterlogged", bool);
        } else if (id.equals("snow")) {
            allowed.put("layers", Set.of("1", "2", "3", "4", "5", "6", "7", "8"));
        } else if (id.equals("tall_grass")) {
            allowed.put("half", Set.of("upper", "lower"));
        } else if (id.equals("end_rod") || id.equals("lightning_rod") || id.equals("barrel")) {
            allowed.put("facing", Set.of("north", "east", "south", "west", "up", "down"));
            allowed.put("powered", bool);
            allowed.put("open", bool);
            allowed.put("waterlogged", bool);
        } else if (id.equals("grass_block") || id.equals("podzol") || id.equals("mycelium")) {
            allowed.put("snowy", bool);
        } else if (id.endsWith("_trapdoor")) {
            allowed.put("facing", horizontal);
            allowed.put("half", Set.of("top", "bottom"));
            allowed.put("open", bool);
            allowed.put("powered", bool);
            allowed.put("waterlogged", bool);
        } else {
            return "no state schema for this block";
        }
        for (String pair : props.split(",")) {
            String[] kv = pair.split("=");
            if (kv.length != 2 || !allowed.containsKey(kv[0])) {
                return "unexpected property " + pair;
            }
            if (!allowed.get(kv[0]).contains(kv[1])) {
                return "bad value " + pair;
            }
        }
        return null;
    }

    /** Resolves arena icons; returns the first icon that does not resolve (null when all do). */
    static final class GeneratedArenaIcons {
        static String check(Iterable<GeneratedArena> arenas) {
            for (GeneratedArena arena : arenas) {
                if (Material.matchMaterial(arena.icon()) == null) {
                    return arena.icon();
                }
            }
            return null;
        }
    }
}
