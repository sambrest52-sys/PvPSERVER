package net.pvpserver.lobby.gen;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Minimal block-state schema for every block family the hub generator uses, so a typo in a property name or value
 * fails a unit test instead of a paste on a live server.
 */
final class BlockStates {

    private static final Set<String> BOOL = Set.of("true", "false");
    private static final Set<String> HORIZONTAL = Set.of("north", "east", "south", "west");
    private static final Set<String> ALL_FACES = Set.of("north", "east", "south", "west", "up", "down");

    private BlockStates() {
    }

    static String id(String block) {
        int bracket = block.indexOf('[');
        String id = bracket < 0 ? block : block.substring(0, bracket);
        return id.substring(id.indexOf(':') + 1);
    }

    /**
     * @param block block data string
     * @return null when valid, else the problem
     */
    static String check(String block) {
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
        if (id.endsWith("_stairs")) {
            allowed.put("facing", HORIZONTAL);
            allowed.put("half", Set.of("top", "bottom"));
            allowed.put("shape", Set.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"));
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_slab")) {
            allowed.put("type", Set.of("top", "bottom", "double"));
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_pillar") || id.equals("hay_block") || id.endsWith("chain")) {
            allowed.put("axis", Set.of("x", "y", "z"));
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_leaves")) {
            allowed.put("persistent", BOOL);
            allowed.put("distance", Set.of("1", "2", "3", "4", "5", "6", "7"));
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("lantern") && !id.equals("sea_lantern")) {
            allowed.put("hanging", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_fence") || id.endsWith("_pane") || id.equals("iron_bars")) {
            HORIZONTAL.forEach(d -> allowed.put(d, BOOL));
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_wall") && !id.endsWith("_wall_banner")) {
            HORIZONTAL.forEach(d -> allowed.put(d, Set.of("none", "low", "tall")));
            allowed.put("up", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("_wall_banner")) {
            allowed.put("facing", HORIZONTAL);
        } else if (id.equals("end_rod") || id.equals("lightning_rod") || id.equals("barrel") || id.equals("amethyst_cluster")) {
            allowed.put("facing", ALL_FACES);
            allowed.put("powered", BOOL);
            allowed.put("open", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.equals("grass_block") || id.equals("podzol") || id.equals("mycelium")) {
            allowed.put("snowy", BOOL);
        } else if (id.endsWith("_trapdoor")) {
            allowed.put("facing", HORIZONTAL);
            allowed.put("half", Set.of("top", "bottom"));
            allowed.put("open", BOOL);
            allowed.put("powered", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("campfire")) {
            allowed.put("facing", HORIZONTAL);
            allowed.put("lit", BOOL);
            allowed.put("signal_fire", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.equals("water_cauldron")) {
            allowed.put("level", Set.of("1", "2", "3"));
        } else if (id.endsWith("weighted_pressure_plate")) {
            allowed.put("power", Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"));
        } else if (id.endsWith("_pressure_plate")) {
            allowed.put("powered", BOOL);
        } else if (id.equals("lectern")) {
            allowed.put("facing", HORIZONTAL);
            allowed.put("has_book", BOOL);
            allowed.put("powered", BOOL);
        } else if (id.equals("bell")) {
            allowed.put("attachment", Set.of("floor", "ceiling", "single_wall", "double_wall"));
            allowed.put("facing", HORIZONTAL);
            allowed.put("powered", BOOL);
        } else if (id.equals("anvil") || id.equals("chipped_anvil") || id.equals("damaged_anvil")) {
            allowed.put("facing", HORIZONTAL);
        } else if (id.equals("grindstone")) {
            allowed.put("face", Set.of("floor", "wall", "ceiling"));
            allowed.put("facing", HORIZONTAL);
        } else if (id.equals("blast_furnace") || id.equals("furnace") || id.equals("smoker")) {
            allowed.put("facing", HORIZONTAL);
            allowed.put("lit", BOOL);
        } else if (id.equals("decorated_pot")) {
            allowed.put("cracked", BOOL);
            allowed.put("facing", HORIZONTAL);
            allowed.put("waterlogged", BOOL);
        } else if (id.endsWith("candle")) {
            allowed.put("candles", Set.of("1", "2", "3", "4"));
            allowed.put("lit", BOOL);
            allowed.put("waterlogged", BOOL);
        } else if (id.equals("sniffer_egg")) {
            allowed.put("hatch", Set.of("0", "1", "2"));
        } else if (id.equals("turtle_egg")) {
            allowed.put("eggs", Set.of("1", "2", "3", "4"));
            allowed.put("hatch", Set.of("0", "1", "2"));
        } else if (id.equals("cake")) {
            allowed.put("bites", Set.of("0", "1", "2", "3", "4", "5", "6"));
        } else if (id.equals("jukebox")) {
            allowed.put("has_record", BOOL);
        } else if (id.equals("note_block")) {
            allowed.put("instrument", Set.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone"));
            allowed.put("note", Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24"));
            allowed.put("powered", BOOL);
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
}
