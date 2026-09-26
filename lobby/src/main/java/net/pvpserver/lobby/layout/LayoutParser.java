package net.pvpserver.lobby.layout;

import net.pvpserver.lobby.layout.LobbyLayout.Border;
import net.pvpserver.lobby.layout.LobbyLayout.Egg;
import net.pvpserver.lobby.layout.LobbyLayout.Emitter;
import net.pvpserver.lobby.layout.LobbyLayout.LaunchPad;
import net.pvpserver.lobby.layout.LobbyLayout.Parkour;
import net.pvpserver.lobby.layout.LobbyLayout.Portal;
import net.pvpserver.lobby.layout.LobbyLayout.Wall;
import net.pvpserver.lobby.layout.LobbyLayout.Zone;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes layout.yml. Broken entries are skipped with a warning instead of failing the whole file, so one
 * typo never takes the lobby down.
 */
public final class LayoutParser {

    private LayoutParser() {
    }

    /**
     * @param root layout.yml root
     * @param warnings receives one line per skipped entry
     * @return layout
     */
    public static LobbyLayout read(ConfigurationSection root, List<String> warnings) {
        Point spawn = point(root, "spawn", warnings);
        if (spawn == null) {
            spawn = LobbyLayout.empty().spawn();
            warnings.add("spawn: missing, using " + spawn.format());
        }
        Border border = null;
        ConfigurationSection borderSection = root.getConfigurationSection("border");
        if (borderSection != null) {
            try {
                double[] center = LayoutNumbers.parse(borderSection.getString("center", "0 0"), 2, 2);
                double size = borderSection.getDouble("size", 0);
                if (size >= 16) {
                    border = new Border(center[0], center[1], size);
                } else {
                    warnings.add("border.size: must be at least 16");
                }
            } catch (IllegalArgumentException e) {
                warnings.add("border.center: " + e.getMessage());
            }
        }
        return new LobbyLayout(spawn, root.getInt("void-y", 0), border, points(root, "npcs", warnings),
                points(root, "holograms", warnings), portals(root, warnings), pads(root, warnings), parkour(root, warnings),
                eggs(root, warnings), zones(root, warnings), emitters(root, warnings), wall(root, warnings));
    }

    private static Point point(ConfigurationSection section, String key, List<String> warnings) {
        String text = section.getString(key);
        if (text == null) {
            return null;
        }
        try {
            return Point.parse(text);
        } catch (IllegalArgumentException e) {
            warnings.add(path(section, key) + ": " + e.getMessage());
            return null;
        }
    }

    private static BlockPos block(ConfigurationSection section, String key, List<String> warnings) {
        String text = section.getString(key);
        if (text == null) {
            warnings.add(path(section, key) + ": missing");
            return null;
        }
        try {
            return BlockPos.parse(text);
        } catch (IllegalArgumentException e) {
            warnings.add(path(section, key) + ": " + e.getMessage());
            return null;
        }
    }

    private static String path(ConfigurationSection section, String key) {
        String current = section.getCurrentPath();
        return current == null || current.isEmpty() ? key : current + "." + key;
    }

    private static Map<String, Point> points(ConfigurationSection root, String key, List<String> warnings) {
        Map<String, Point> points = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section != null) {
            for (String id : section.getKeys(false)) {
                Point point = point(section, id, warnings);
                if (point != null) {
                    points.put(id.toLowerCase(Locale.ROOT), point);
                }
            }
        }
        return points;
    }

    private static List<Portal> portals(ConfigurationSection root, List<String> warnings) {
        List<Portal> portals = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("portals");
        if (section == null) {
            return portals;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection portal = section.getConfigurationSection(id);
            if (portal == null) {
                warnings.add("portals." + id + ": expected from/to/action");
                continue;
            }
            BlockPos from = block(portal, "from", warnings);
            BlockPos to = block(portal, "to", warnings);
            String action = portal.getString("action", "");
            if (action.isBlank()) {
                warnings.add("portals." + id + ".action: missing");
                continue;
            }
            if (from != null && to != null) {
                Box box = Box.of(from, to);
                if (box.volume() > 4096) {
                    warnings.add("portals." + id + ": box is larger than 4096 blocks");
                    continue;
                }
                portals.add(new Portal(id.toLowerCase(Locale.ROOT), box, action, portal.getString("color", "#FFFFFF")));
            }
        }
        return portals;
    }

    private static List<LaunchPad> pads(ConfigurationSection root, List<String> warnings) {
        List<LaunchPad> pads = new ArrayList<>();
        List<Map<?, ?>> list = root.getMapList("launch-pads");
        for (int i = 0; i < list.size(); i++) {
            Map<?, ?> entry = list.get(i);
            try {
                BlockPos at = BlockPos.parse(String.valueOf(entry.get("at")));
                double[] velocity = LayoutNumbers.parse(String.valueOf(entry.get("velocity")), 3, 3);
                if (Math.abs(velocity[0]) > 10 || Math.abs(velocity[1]) > 10 || Math.abs(velocity[2]) > 10) {
                    throw new IllegalArgumentException("velocity components must be between -10 and 10");
                }
                pads.add(new LaunchPad(at, velocity[0], velocity[1], velocity[2]));
            } catch (IllegalArgumentException e) {
                warnings.add("launch-pads[" + i + "]: " + e.getMessage());
            }
        }
        return pads;
    }

    private static Parkour parkour(ConfigurationSection root, List<String> warnings) {
        ConfigurationSection section = root.getConfigurationSection("parkour");
        if (section == null) {
            return null;
        }
        BlockPos start = block(section, "start", warnings);
        BlockPos finish = block(section, "finish", warnings);
        List<BlockPos> checkpoints = new ArrayList<>();
        List<String> raw = section.getStringList("checkpoints");
        for (int i = 0; i < raw.size(); i++) {
            try {
                checkpoints.add(BlockPos.parse(raw.get(i)));
            } catch (IllegalArgumentException e) {
                warnings.add("parkour.checkpoints[" + i + "]: " + e.getMessage());
            }
        }
        if (start == null || finish == null) {
            warnings.add("parkour: needs a start and a finish, parkour disabled");
            return null;
        }
        int lowest = Math.min(start.y(), finish.y());
        for (BlockPos checkpoint : checkpoints) {
            lowest = Math.min(lowest, checkpoint.y());
        }
        return new Parkour(start, checkpoints, finish, section.getInt("fall-y", lowest - 4));
    }

    private static List<Egg> eggs(ConfigurationSection root, List<String> warnings) {
        List<Egg> eggs = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("eggs");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                BlockPos at = block(section, id, warnings);
                if (at != null) {
                    eggs.add(new Egg(id.toLowerCase(Locale.ROOT), at));
                }
            }
        }
        return eggs;
    }

    private static List<Zone> zones(ConfigurationSection root, List<String> warnings) {
        List<Zone> zones = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("zones");
        if (section == null) {
            return zones;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection zone = section.getConfigurationSection(id);
            if (zone == null) {
                warnings.add("zones." + id + ": expected center/radius");
                continue;
            }
            try {
                double[] center = LayoutNumbers.parse(zone.getString("center"), 2, 2);
                double radius = zone.getDouble("radius", 0);
                if (radius <= 0) {
                    throw new IllegalArgumentException("radius must be positive");
                }
                zones.add(new Zone(id.toLowerCase(Locale.ROOT), center[0], center[1], radius));
            } catch (IllegalArgumentException e) {
                warnings.add("zones." + id + ": " + e.getMessage());
            }
        }
        return zones;
    }

    private static List<Emitter> emitters(ConfigurationSection root, List<String> warnings) {
        List<Emitter> emitters = new ArrayList<>();
        List<Map<?, ?>> list = root.getMapList("emitters");
        for (int i = 0; i < list.size(); i++) {
            Map<?, ?> entry = list.get(i);
            try {
                Object type = entry.get("type");
                if (type == null) {
                    throw new IllegalArgumentException("missing type");
                }
                emitters.add(new Emitter(String.valueOf(type).toLowerCase(Locale.ROOT), Point.parse(String.valueOf(entry.get("at")))));
            } catch (IllegalArgumentException e) {
                warnings.add("emitters[" + i + "]: " + e.getMessage());
            }
        }
        return emitters;
    }

    private static Wall wall(ConfigurationSection root, List<String> warnings) {
        ConfigurationSection section = root.getConfigurationSection("leaderboard-wall");
        if (section == null) {
            return null;
        }
        Point at = point(section, "at", warnings);
        if (at == null) {
            warnings.add("leaderboard-wall.at: missing, wall disabled");
            return null;
        }
        int columns = section.getInt("columns", 4);
        if (columns < 1 || columns > 16) {
            warnings.add("leaderboard-wall.columns: must be 1-16, using 4");
            columns = 4;
        }
        return new Wall(at, columns, section.getDouble("spacing-x", 3.5), section.getDouble("spacing-y", 3.5));
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes a layout into an empty section (the whole layout.yml).
     *
     * @param layout layout
     * @param root target
     */
    public static void write(LobbyLayout layout, ConfigurationSection root) {
        root.set("spawn", layout.spawn().format());
        root.set("void-y", layout.voidY());
        if (layout.border() != null) {
            root.set("border.center", LayoutNumbers.format(layout.border().centerX()) + " " + LayoutNumbers.format(layout.border().centerZ()));
            root.set("border.size", layout.border().size());
        }
        layout.npcs().forEach((id, point) -> root.set("npcs." + id, point.format()));
        layout.holograms().forEach((id, point) -> root.set("holograms." + id, point.format()));
        for (Portal portal : layout.portals()) {
            String base = "portals." + portal.id() + ".";
            root.set(base + "from", portal.box().min().format());
            root.set(base + "to", portal.box().max().format());
            root.set(base + "action", portal.action());
            root.set(base + "color", portal.color());
        }
        List<Map<String, Object>> pads = new ArrayList<>();
        for (LaunchPad pad : layout.pads()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("at", pad.at().format());
            entry.put("velocity", LayoutNumbers.format(pad.vx()) + " " + LayoutNumbers.format(pad.vy()) + " " + LayoutNumbers.format(pad.vz()));
            pads.add(entry);
        }
        root.set("launch-pads", pads);
        if (layout.parkour() != null) {
            Parkour parkour = layout.parkour();
            root.set("parkour.start", parkour.start().format());
            root.set("parkour.checkpoints", parkour.checkpoints().stream().map(BlockPos::format).toList());
            root.set("parkour.finish", parkour.finish().format());
            root.set("parkour.fall-y", parkour.fallY());
        }
        for (Egg egg : layout.eggs()) {
            root.set("eggs." + egg.id(), egg.at().format());
        }
        for (Zone zone : layout.zones()) {
            root.set("zones." + zone.id() + ".center", LayoutNumbers.format(zone.x()) + " " + LayoutNumbers.format(zone.z()));
            root.set("zones." + zone.id() + ".radius", zone.radius());
        }
        List<Map<String, Object>> emitters = new ArrayList<>();
        for (Emitter emitter : layout.emitters()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", emitter.type());
            entry.put("at", emitter.at().format());
            emitters.add(entry);
        }
        root.set("emitters", emitters);
        if (layout.wall() != null) {
            root.set("leaderboard-wall.at", layout.wall().at().format());
            root.set("leaderboard-wall.columns", layout.wall().columns());
            root.set("leaderboard-wall.spacing-x", layout.wall().spacingX());
            root.set("leaderboard-wall.spacing-y", layout.wall().spacingY());
        }
    }
}
