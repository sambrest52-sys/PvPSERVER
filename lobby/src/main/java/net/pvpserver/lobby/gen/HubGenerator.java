package net.pvpserver.lobby.gen;

import net.pvpserver.core.arena.gen.Canvas;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.Box;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.LobbyLayout.Button;
import net.pvpserver.lobby.layout.LobbyLayout.Egg;
import net.pvpserver.lobby.layout.LobbyLayout.Emitter;
import net.pvpserver.lobby.layout.LobbyLayout.LaunchPad;
import net.pvpserver.lobby.layout.LobbyLayout.Parkour;
import net.pvpserver.lobby.layout.LobbyLayout.Portal;
import net.pvpserver.lobby.layout.LobbyLayout.Wall;
import net.pvpserver.lobby.layout.LobbyLayout.Zone;
import net.pvpserver.lobby.layout.Point;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the default hub: a floating plaza island with a fountain and an emblem floor, eight themed zone islands
 * joined to it by bridges (Ranked Hall, Unranked Hall, FFA Gate, Hall of Fame, Cosmetics Shop, Info Pavilion, Party
 * Lounge, Kit Workshop), a spiral sky parkour, launch pads, hidden eggs, and a ring of decorative floating islands
 * and crystal spires as a skyline. The playable area is about 150 blocks across and walled in by an invisible barrier
 * cylinder. Generation is deterministic for a seed and needs no server, so it is unit tested and previewed offline.
 */
public final class HubGenerator {

    /** Template width and depth. */
    public static final int SIZE = 200;
    /** Template height. */
    public static final int HEIGHT = 100;
    /** Template y of the plaza floor block. */
    public static final int FLOOR = 36;
    /** Template x/z of the hub centre block. */
    public static final int CENTER = 100;
    /** Barrier cylinder radius. */
    public static final double BARRIER_RADIUS = 76.5;

    static final double MID = CENTER + 0.5;
    static final double PLAZA_RADIUS = 32;
    static final double ZONE_DISTANCE = 56;
    static final double ZONE_RADIUS = 14;
    static final double PARKOUR_ANGLE = 157.5;
    static final double PARKOUR_AXIS = 58;
    static final double PARKOUR_RADIUS = 9;

    /**
     * A zone island.
     *
     * @param id zone id
     * @param angle direction from the plaza, degrees clockwise from north
     * @param front direction its buildings face
     * @param color dye colour of its accents
     * @param rim wall or fence that edges the island
     */
    record ZoneSpec(String id, double angle, String front, String color, String rim) {
    }

    static final List<ZoneSpec> ZONES = List.of(
            new ZoneSpec("ranked", 0, "south", "yellow", "diorite_wall"),
            new ZoneSpec("ffa", 45, "south", "red", "polished_blackstone_brick_wall"),
            new ZoneSpec("leaderboards", 90, "west", "orange", "deepslate_brick_wall"),
            new ZoneSpec("cosmetics", 135, "north", "magenta", "end_stone_brick_wall"),
            new ZoneSpec("info", 180, "north", "lime", "mossy_stone_brick_wall"),
            new ZoneSpec("party", 225, "north", "pink", "spruce_fence"),
            new ZoneSpec("kit-editor", 270, "east", "brown", "cobblestone_wall"),
            new ZoneSpec("unranked", 315, "south", "cyan", "prismarine_wall"));

    private final Canvas c;
    private final int f = FLOOR;

    private final Map<String, Point> npcs = new LinkedHashMap<>();
    private final Map<String, Point> holograms = new LinkedHashMap<>();
    private final List<Portal> portals = new ArrayList<>();
    private final List<LaunchPad> pads = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<Egg> eggs = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final List<double[]> corridors = new ArrayList<>();
    private final List<BlockPos> stones = new ArrayList<>();
    private Wall wall;
    private Parkour parkour;
    private Point spawn;

    private HubGenerator(long seed) {
        this.c = new Canvas(SIZE, HEIGHT, SIZE, seed);
    }

    /**
     * @param seed seed
     * @return the hub
     */
    public static HubBlueprint generate(long seed) {
        return new HubGenerator(seed).build();
    }

    private HubBlueprint build() {
        mainIsland();
        for (ZoneSpec zone : ZONES) {
            zoneIsland(zone);
            bridge(zone);
        }
        plaza();
        for (ZoneSpec zone : ZONES) {
            Frame frame = frame(zone);
            switch (zone.id()) {
                case "ranked" -> rankedHall(frame);
                case "unranked" -> unrankedHall(frame);
                case "ffa" -> ffaGate(frame);
                case "leaderboards" -> hallOfFame(frame);
                case "cosmetics" -> cosmeticsShop(frame);
                case "info" -> infoPavilion(frame);
                case "party" -> partyLounge(frame);
                case "kit-editor" -> kitWorkshop(frame);
                default -> throw new IllegalStateException(zone.id());
            }
            zones.add(new Zone(zone.id(), frame.px(0, 0) + 0.5, frame.pz(0, 0) + 0.5, ZONE_RADIUS + 1));
        }
        zones.add(0, new Zone("plaza", MID, MID, PLAZA_RADIUS));
        rims();
        parkour();
        expressPads();
        skyline();
        barrier();
        c.b.connectShapes();

        LobbyLayout layout = new LobbyLayout(spawn, f - 10, new LobbyLayout.Border(MID, MID, SIZE), npcs, holograms, portals, pads,
                buttons, parkour, eggs, zones, emitters, wall).rounded();
        return new HubBlueprint(c.b, layout, f, CENTER, CENTER, List.copyOf(stones));
    }

    // ------------------------------------------------------------------ geometry helpers

    /** Point at a compass angle (degrees clockwise from north) and distance from the hub centre. */
    static double[] polar(double angle, double radius) {
        double rad = Math.toRadians(angle);
        return new double[]{MID + radius * Math.sin(rad), MID - radius * Math.cos(rad)};
    }

    private static Frame frame(ZoneSpec zone) {
        double[] p = polar(zone.angle(), ZONE_DISTANCE);
        return new Frame((int) Math.floor(p[0]), (int) Math.floor(p[1]), zone.front());
    }

    /** Yaw of someone at (x, z) looking at (tx, tz). */
    static float yawTowards(double x, double z, double tx, double tz) {
        return (float) Math.round(Math.toDegrees(Math.atan2(-(tx - x), tz - z)));
    }

    private static double segmentDistance(double px, double pz, double[] segment) {
        double ax = segment[0];
        double az = segment[1];
        double bx = segment[2];
        double bz = segment[3];
        double dx = bx - ax;
        double dz = bz - az;
        double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz);
        t = Math.max(0, Math.min(1, t));
        double qx = ax + t * dx - px;
        double qz = az + t * dz - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    private boolean inCorridor(int x, int z, double width) {
        for (double[] corridor : corridors) {
            if (segmentDistance(x + 0.5, z + 0.5, corridor) <= width) {
                return true;
            }
        }
        return false;
    }

    private void add(Frame fr, double a, double b, int y, String block) {
        c.set(fr.x(a, b), y, fr.z(a, b), block);
    }

    private Point at(Frame fr, double a, double b, double y) {
        return Point.of(fr.px(a, b) + 0.5, y, fr.pz(a, b) + 0.5);
    }

    private BlockPos block(Frame fr, double a, double b, int y) {
        return new BlockPos(fr.x(a, b), y, fr.z(a, b));
    }

    private String axisA(Frame fr) {
        return fr.aAlongX() ? "x" : "z";
    }

    private String axisB(Frame fr) {
        return fr.aAlongX() ? "z" : "x";
    }

    private void emit(String type, double x, double y, double z) {
        emitters.add(new Emitter(type, Point.of(x, y, z)));
    }

    private void egg(String id, int x, int y, int z, String block) {
        c.set(x, y, z, block);
        eggs.add(new Egg(id, new BlockPos(x, y, z)));
    }

    private void wallBanner(int x, int y, int z, String facing, String color) {
        c.setIfAir(x, y, z, color + "_wall_banner[facing=" + facing + "]");
    }

    private static int[] offset(String direction) {
        return switch (direction) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "east" -> new int[]{1, 0};
            default -> new int[]{-1, 0};
        };
    }

    // ------------------------------------------------------------------ islands

    /** Grassy floating island with a rocky, ore-speckled underside. */
    private void island(double cx, double cz, double radius, int depth, String top, String under) {
        int r = (int) Math.ceil(radius + 2);
        for (int x = (int) cx - r; x <= (int) cx + r; x++) {
            for (int z = (int) cz - r; z <= (int) cz + r; z++) {
                double edge = radius + (c.noise.fractal(x, z, 9, 2) - 0.5) * 3;
                if (Canvas.dist(x, z, cx, cz) <= edge) {
                    c.set(x, f, z, top);
                    c.set(x, f - 1, z, under);
                    c.set(x, f - 2, z, under);
                }
            }
        }
        c.underside(cx, f - 3, cz, radius + 0.5, depth, "stone", "stone", "andesite", "cobblestone", "tuff", "dirt");
        for (int x = (int) cx - r; x <= (int) cx + r; x++) {
            for (int z = (int) cz - r; z <= (int) cz + r; z++) {
                for (int y = f - depth - 3; y < f - 3; y++) {
                    if (c.get(x, y, z).equals("minecraft:stone") && c.rnd.nextDouble() < 0.035) {
                        c.set(x, y, z, c.pick("coal_ore", "iron_ore", "copper_ore", "gold_ore", "lapis_ore", "emerald_ore"));
                    }
                }
            }
        }
    }

    private void mainIsland() {
        island(MID, MID, PLAZA_RADIUS, 28, "grass_block", "dirt");
    }

    private void zoneIsland(ZoneSpec zone) {
        Frame fr = frame(zone);
        island(fr.px(0, 0) + 0.5, fr.pz(0, 0) + 0.5, ZONE_RADIUS, 18, "grass_block", "dirt");
    }

    /** Bridge from the plaza island to a zone island: planked deck, railings and lantern posts. */
    private void bridge(ZoneSpec zone) {
        double[] from = polar(zone.angle(), PLAZA_RADIUS - 4);
        double[] to = polar(zone.angle(), ZONE_DISTANCE - ZONE_RADIUS + 3);
        double[] segment = {from[0], from[1], to[0], to[1]};
        corridors.add(segment);
        int minX = (int) Math.floor(Math.min(from[0], to[0]) - 4);
        int maxX = (int) Math.ceil(Math.max(from[0], to[0]) + 4);
        int minZ = (int) Math.floor(Math.min(from[1], to[1]) - 4);
        int maxZ = (int) Math.ceil(Math.max(from[1], to[1]) + 4);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double d = segmentDistance(x + 0.5, z + 0.5, segment);
                boolean overVoid = c.isAir(x, f, z) || c.get(x, f, z).contains("_wall") || c.get(x, f, z).contains("fence");
                if (d <= 2.4) {
                    String deck = d <= 0.8 ? "stone_bricks" : c.mix("spruce_planks", "stripped_spruce_wood[axis=y]", 0.1);
                    if (overVoid || !c.get(x, f, z).contains("stone_bricks")) {
                        c.set(x, f, z, deck);
                    }
                    if (overVoid && d <= 1.6) {
                        c.set(x, f - 1, z, "stone_brick_slab[type=top]");
                    }
                } else if (d <= 3.4 && overVoid) {
                    c.set(x, f, z, "spruce_planks");
                    c.set(x, f + 1, z, "spruce_fence");
                }
            }
        }
        // Lantern posts along both railings.
        double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
        double ux = (to[0] - from[0]) / length;
        double uz = (to[1] - from[1]) / length;
        for (double t = 4; t < length - 2; t += 5) {
            for (int side = -1; side <= 1; side += 2) {
                int x = (int) Math.floor(from[0] + ux * t - uz * side * 2.9);
                int z = (int) Math.floor(from[1] + uz * t + ux * side * 2.9);
                if (c.get(x, f + 1, z).contains("fence")) {
                    c.set(x, f + 2, z, "spruce_fence");
                    c.set(x, f + 3, z, "lantern[hanging=false]");
                }
            }
        }
    }

    /** Edges every island with its zone's wall or fence (bridges keep their openings) and dots lanterns on it. */
    private void rims() {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int count = 0;
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                if (c.isAir(x, f, z) || !(c.isAir(x, f + 1, z) || plant(c.get(x, f + 1, z))) || inCorridor(x, z, 3.6)) {
                    continue;
                }
                boolean edge = false;
                for (int[] d : around) {
                    if (c.isAir(x + d[0], f, z + d[1])) {
                        edge = true;
                        break;
                    }
                }
                if (!edge) {
                    continue;
                }
                String rim = "stone_brick_wall";
                double best = Canvas.dist(x, z, MID, MID) - PLAZA_RADIUS;
                for (ZoneSpec zone : ZONES) {
                    Frame fr = frame(zone);
                    double d = Canvas.dist(x, z, fr.px(0, 0) + 0.5, fr.pz(0, 0) + 0.5) - ZONE_RADIUS;
                    if (d < best) {
                        best = d;
                        rim = zone.rim();
                    }
                }
                c.set(x, f + 1, z, rim);
                if (++count % 9 == 0) {
                    c.set(x, f + 2, z, "lantern[hanging=false]");
                }
            }
        }
    }

    private static boolean plant(String block) {
        return block.endsWith("short_grass") || block.endsWith("poppy") || block.endsWith("dandelion") || block.endsWith("bluet")
                || block.endsWith("daisy") || block.endsWith("cornflower") || block.endsWith("allium") || block.endsWith("tulip")
                || block.endsWith("valley");
    }

    // ------------------------------------------------------------------ plaza

    private static String zoneColor(double angle) {
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        return ZONES.get(index).color();
    }

    private void plaza() {
        int r = 25;
        for (int x = CENTER - r; x <= CENTER + r; x++) {
            for (int z = CENTER - r; z <= CENTER + r; z++) {
                double d = Canvas.dist(x, z, MID, MID);
                if (d > 24.5) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(x + 0.5 - MID, -(z + 0.5 - MID)));
                double spoke = Math.abs(((angle % 45) + 45) % 45);
                double fromSpoke = Math.min(spoke, 45 - spoke);
                double starRadius = 12 + 10 * (1 - fromSpoke / 22.5);
                String block;
                if (d <= 7.5) {
                    block = "polished_blackstone_bricks";
                } else if (d > 22.5) {
                    block = ((int) Math.floor(angle + 360) % 15 < 2) ? "chiseled_stone_bricks" : "polished_blackstone_bricks";
                } else if (d <= starRadius - 1) {
                    double lateral = d * Math.sin(Math.toRadians(fromSpoke));
                    block = lateral <= 0.9 ? zoneColor(angle) + "_concrete" : "smooth_quartz";
                } else if (d <= starRadius) {
                    block = "gold_block";
                } else {
                    double n = c.noise.fractal(x, z, 5, 2);
                    block = n < 0.38 ? "andesite" : n > 0.66 ? "stone_bricks" : "polished_andesite";
                }
                c.set(x, f, z, block);
            }
        }
        // Garden ring: flowers and grass, with paved paths continuing each spoke to its bridge.
        for (int x = CENTER - 34; x <= CENTER + 34; x++) {
            for (int z = CENTER - 34; z <= CENTER + 34; z++) {
                double d = Canvas.dist(x, z, MID, MID);
                if (d <= 24.5 || !c.get(x, f, z).equals("minecraft:grass_block")) {
                    continue;
                }
                if (inCorridor(x, z, 2.4)) {
                    c.set(x, f, z, inCorridor(x, z, 0.8) ? "stone_bricks" : "polished_andesite");
                    continue;
                }
                double n = c.noise.fractal(x * 1.7, z * 1.7, 4, 2);
                if (n > 0.62) {
                    c.plant(x, f + 1, z, c.pick("poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium"), "grass_block");
                } else if (n < 0.3 && c.rnd.nextDouble() < 0.5) {
                    c.plant(x, f + 1, z, "short_grass", "grass_block");
                }
            }
        }
        fountain();
        spawnMedallion();
        for (int i = 0; i < 8; i++) {
            double between = 22.5 + 45 * i;
            boolean south = between == 157.5 || between == 202.5;
            if (!south) {
                double[] p = polar(between, 14.5);
                planter((int) Math.floor(p[0]), (int) Math.floor(p[1]));
            }
            double[] bench = polar(between, 18);
            bench((int) Math.floor(bench[0]), (int) Math.floor(bench[1]), between);
            double[] pillar = polar(between, 21.5);
            pillar((int) Math.floor(pillar[0]), (int) Math.floor(pillar[1]), zoneColor(between - 22.5), zoneColor(between + 22.5));
            for (int side = -1; side <= 1; side += 2) {
                double[] lamp = polar(45 * i + side * 11.25, 23.5);
                c.lanternPost((int) Math.floor(lamp[0]), f + 1, (int) Math.floor(lamp[1]), "dark_oak_fence", 3, "lantern");
            }
        }
        // Cherry trees and azaleas in the garden ring, clear of the paths.
        for (int i = 0; i < 16; i++) {
            double angle = 11.25 + 22.5 * i + (c.rnd.nextDouble() - 0.5) * 6;
            double[] p = polar(angle, 28 + c.rnd.nextDouble() * 1.5);
            int x = (int) Math.floor(p[0]);
            int z = (int) Math.floor(p[1]);
            if (inCorridor(x, z, 4.5) || !c.get(x, f, z).contains("grass")) {
                continue;
            }
            if (i % 2 == 0) {
                c.broadTree(x, f + 1, z, 5 + c.rnd.nextInt(2), "cherry_log", "cherry_leaves");
                emit("leaves", x + 0.5, f + 5, z + 0.5);
            } else {
                c.set(x, f + 1, z, c.pick("flowering_azalea", "azalea"));
            }
        }
        // NPC arc on the south side of the fountain, facing the spawn.
        String[] arc = {"kit-editor", "unranked", "ranked", "ffa", "stats"};
        double[] angles = {236, 208, 180, 152, 124};
        for (int i = 0; i < arc.length; i++) {
            double[] p = polar(angles[i], 11);
            int x = (int) Math.floor(p[0]);
            int z = (int) Math.floor(p[1]);
            c.set(x, f + 1, z, "chiseled_quartz_block");
            c.set(x, f, z, zoneColor(i == 4 ? 90 : new double[]{270, 315, 0, 45}[i]) + "_concrete");
            npcs.put(arc[i], Point.of(x + 0.5, f + 2, z + 0.5).facing(yawTowards(x + 0.5, z + 0.5, spawn.x(), spawn.z()), 0));
        }
        holograms.put("welcome", Point.of(MID, f + 11.5, MID));
    }

    private void fountain() {
        for (int x = CENTER - 6; x <= CENTER + 6; x++) {
            for (int z = CENTER - 6; z <= CENTER + 6; z++) {
                double d = Canvas.dist(x, z, MID, MID);
                if (d <= 4.5) {
                    c.set(x, f - 1, z, (x + z) % 3 == 0 ? "dark_prismarine" : "prismarine_bricks");
                    c.set(x, f, z, "water");
                } else if (d <= 5.5) {
                    c.set(x, f, z, "smooth_quartz");
                    c.set(x, f + 1, z, "smooth_quartz_slab[type=bottom]");
                }
            }
        }
        c.set(CENTER + 3, f - 1, CENTER + 3, "sea_lantern");
        c.set(CENTER - 3, f - 1, CENTER - 3, "sea_lantern");
        c.set(CENTER + 3, f - 1, CENTER - 3, "sea_lantern");
        c.set(CENTER - 3, f - 1, CENTER + 3, "sea_lantern");
        // Centre column with an upper bowl and four water basins.
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            for (int z = CENTER - 1; z <= CENTER + 1; z++) {
                c.set(x, f, z, "chiseled_quartz_block");
                c.set(x, f + 1, z, "quartz_block");
                c.set(x, f + 3, z, "smooth_quartz");
            }
        }
        c.column(CENTER, CENTER, f + 2, f + 7, "quartz_pillar[axis=y]");
        c.set(CENTER - 1, f + 2, CENTER - 1, "quartz_pillar[axis=y]");
        c.set(CENTER + 1, f + 2, CENTER + 1, "quartz_pillar[axis=y]");
        c.set(CENTER - 1, f + 2, CENTER + 1, "quartz_pillar[axis=y]");
        c.set(CENTER + 1, f + 2, CENTER - 1, "quartz_pillar[axis=y]");
        c.set(CENTER + 2, f + 3, CENTER, "water_cauldron[level=3]");
        c.set(CENTER - 2, f + 3, CENTER, "water_cauldron[level=3]");
        c.set(CENTER, f + 3, CENTER + 2, "water_cauldron[level=3]");
        c.set(CENTER, f + 3, CENTER - 2, "water_cauldron[level=3]");
        c.set(CENTER, f + 8, CENTER, "sea_lantern");
        c.set(CENTER, f + 9, CENTER, "end_rod[facing=up]");
        c.set(CENTER + 1, f + 8, CENTER, "end_rod[facing=east]");
        c.set(CENTER - 1, f + 8, CENTER, "end_rod[facing=west]");
        c.set(CENTER, f + 8, CENTER + 1, "end_rod[facing=south]");
        c.set(CENTER, f + 8, CENTER - 1, "end_rod[facing=north]");
        c.set(CENTER + 2, f + 1, CENTER + 3, "lily_pad");
        c.set(CENTER - 3, f + 1, CENTER + 1, "lily_pad");
        c.set(CENTER - 1, f + 1, CENTER - 3, "lily_pad");
        emit("fountain", MID, f + 8.6, MID);
        emit("mist", MID + 3, f + 1.1, MID);
        emit("mist", MID - 3, f + 1.1, MID);
        emit("drip", MID + 2, f + 3.2, MID);
        emit("drip", MID - 2, f + 3.2, MID);
        egg("fountain", CENTER + 2, f - 1, CENTER - 3, "dragon_egg");
    }

    /** Crossed swords inlaid in the floor where players spawn. */
    private void spawnMedallion() {
        double[] p = polar(180, 19);
        int cx = (int) Math.floor(p[0]);
        int cz = (int) Math.floor(p[1]);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.9) {
                    continue;
                }
                String block;
                if (d > 3.1) {
                    block = "gold_block";
                } else if (Math.abs(dx) == Math.abs(dz) && d >= 1) {
                    block = Math.abs(dx) == 2 ? "gold_block" : "iron_block";
                } else if (dx == 0 && dz == 0) {
                    block = "iron_block";
                } else {
                    block = "polished_blackstone";
                }
                c.set(cx + dx, f, cz + dz, block);
            }
        }
        spawn = new Point(cx + 0.5, f + 1, cz + 0.5, 180, 0);
    }

    private void planter(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, f + 1, z + dz, dx == 0 && dz == 0 ? "rooted_dirt" : "polished_andesite");
            }
        }
        c.set(x, f + 2, z, c.pick("flowering_azalea", "azalea"));
        c.set(x + 1, f + 2, z, c.pick("potted_red_tulip", "potted_blue_orchid", "potted_allium"));
        c.set(x - 1, f + 2, z, c.pick("potted_fern", "potted_azure_bluet", "potted_oxeye_daisy"));
    }

    private void bench(int x, int z, double angle) {
        String facing = Canvas.facingAway(x, z, MID, MID);
        boolean alongX = facing.equals("north") || facing.equals("south");
        for (int i = -1; i <= 1; i++) {
            int bx = alongX ? x + i : x;
            int bz = alongX ? z : z + i;
            c.set(bx, f + 1, bz, "spruce_stairs[facing=" + facing + "]");
        }
        int[] side = alongX ? new int[]{1, 0} : new int[]{0, 1};
        c.set(x + side[0] * 2, f + 1, z + side[1] * 2, "spruce_trapdoor[facing=" + (alongX ? "east" : "south") + ",half=bottom,open=true]");
        c.set(x - side[0] * 2, f + 1, z - side[1] * 2, "spruce_trapdoor[facing=" + (alongX ? "west" : "north") + ",half=bottom,open=true]");
    }

    private void pillar(int x, int z, String colorA, String colorB) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, f + 1, z + dz, "polished_andesite_slab[type=bottom]");
            }
        }
        c.set(x, f + 1, z, "chiseled_stone_bricks");
        c.column(x, z, f + 2, f + 8, "quartz_pillar[axis=y]");
        c.set(x, f + 9, z, "chiseled_quartz_block");
        c.set(x, f + 10, z, "lantern[hanging=false]");
        String[] faces = {"north", "east", "south", "west"};
        for (int i = 0; i < faces.length; i++) {
            int[] o = offset(faces[i]);
            wallBanner(x + o[0], f + 7, z + o[1], faces[i], i % 2 == 0 ? colorA : colorB);
        }
    }

    // ------------------------------------------------------------------ zone builders

    /** Paints a zone floor disc; {@code painter} picks the block from local (a, b) and distance. */
    private void floor(Frame fr, double radius, FloorPainter painter) {
        int r = (int) Math.ceil(radius);
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d <= radius && !c.isAir(fr.x(a, b), f, fr.z(a, b))) {
                    add(fr, a, b, f, painter.block(a, b, d));
                }
            }
        }
    }

    @FunctionalInterface
    private interface FloorPainter {
        String block(int a, int b, double d);
    }

    /** Portal frame at depth {@code b}: two posts, a lintel, a solid backing wall and the trigger box. */
    private void portal(Frame fr, String id, int b, String post, String lintel, String corner, String backing, String action,
                        String color) {
        for (int y = f + 1; y <= f + 6; y++) {
            add(fr, -2, b, y, post);
            add(fr, 2, b, y, post);
            for (int a = -2; a <= 2; a++) {
                add(fr, a, b + 1, y, backing);
            }
        }
        for (int a = -2; a <= 2; a++) {
            add(fr, a, b, f + 6, lintel);
        }
        add(fr, -2, b, f + 6, corner);
        add(fr, 2, b, f + 6, corner);
        add(fr, 0, b, f + 7, corner);
        for (int a = -1; a <= 1; a++) {
            add(fr, a, b, f, color + "_concrete");
        }
        portals.add(new Portal(id, Box.of(block(fr, -1, b, f + 1), block(fr, 1, b, f + 5)), action, hex(color)));
        holograms.put(id + "-portal", at(fr, 0, b - 0.6, f + 7.6));
        emit("sparkle", fr.px(0, b) + 0.5, f + 3, fr.pz(0, b) + 0.5);
    }

    private static String hex(String color) {
        return switch (color) {
            case "yellow" -> "#FFC83D";
            case "cyan" -> "#3DD6FF";
            case "red" -> "#FF4040";
            default -> "#FFFFFF";
        };
    }

    /** 3x3 base with a beacon and tinted glass: a coloured beam that marks the zone from anywhere in the lobby. */
    private void beacon(int x, int y, int z, String base, String glass) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, y, z + dz, base);
            }
        }
        c.set(x, y + 1, z, "beacon");
        c.set(x, y + 2, z, glass);
    }

    private void rankedHall(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (d > 8.6 && d <= 9.5) {
                return "gold_block";
            }
            return Math.abs(a) <= 1 && b < 8 ? "yellow_concrete" : c.mix("smooth_quartz", "quartz_bricks", 0.15);
        });
        // Rotunda: nine pillars leaving the front open, an entablature ring and a stepped dome.
        for (int k = 0; k < 12; k++) {
            int angle = k * 30;
            if (angle >= 150 && angle <= 210) {
                continue;
            }
            double a = 11 * Math.sin(Math.toRadians(angle));
            double b = 11 * Math.cos(Math.toRadians(angle));
            int x = fr.x(a, b);
            int z = fr.z(a, b);
            c.set(x, f + 1, z, "chiseled_quartz_block");
            c.column(x, z, f + 2, f + 8, "quartz_pillar[axis=y]");
            c.set(x, f + 9, z, "chiseled_quartz_block");
            String inward = Canvas.facingToward(x, z, fr.px(0, 0) + 0.5, fr.pz(0, 0) + 0.5);
            int[] o = offset(inward);
            wallBanner(x + o[0], f + 7, z + o[1], inward, "yellow");
        }
        for (int a = -13; a <= 13; a++) {
            for (int b = -13; b <= 13; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > 9.5 && d <= 12.5) {
                    add(fr, a, b, f + 10, "quartz_bricks");
                }
                for (int layer = 0; layer <= 6; layer++) {
                    double outer = 12.5 * Math.sqrt(Math.max(0, 1 - Math.pow(layer / 6.6, 2)));
                    double inner = layer == 6 ? -1 : outer - 1.4;
                    if (d <= outer && d > inner) {
                        add(fr, a, b, f + 11 + layer, layer == 0 && d > outer - 0.8 ? "gold_block" : "smooth_quartz");
                    }
                }
            }
        }
        beacon(fr.x(0, 0), f + 18, fr.z(0, 0), "gold_block", "yellow_stained_glass");
        c.column(fr.x(0, 0), fr.z(0, 0), f + 13, f + 16, "iron_chain[axis=y]");
        add(fr, 0, 0, f + 12, "lantern[hanging=true]");
        portal(fr, "ranked", 8, "quartz_pillar[axis=y]", "chiseled_quartz_block", "gold_block", "smooth_quartz", "queue-ranked", "yellow");
        for (int side = -1; side <= 1; side += 2) {
            add(fr, side * 4, 6, f + 1, "gold_block");
            add(fr, side * 4, 6, f + 2, "lantern[hanging=false]");
        }
        BlockPos nook = block(fr, 3, 10, f + 1);
        egg("ranked", nook.x(), nook.y(), nook.z(), "turtle_egg[eggs=1,hatch=0]");
    }

    private void unrankedHall(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> ((a + b) & 1) == 0 ? "prismarine_bricks" : "dark_prismarine");
        // Reflecting pools either side of the aisle.
        for (int side = -1; side <= 1; side += 2) {
            for (int a = 5; a <= 9; a++) {
                for (int b = -3; b <= 5; b++) {
                    int la = side * a;
                    boolean edge = a == 5 || a == 9 || b == -3 || b == 5;
                    add(fr, la, b, f - 1, "prismarine");
                    add(fr, la, b, f, edge ? "prismarine_bricks" : "water");
                    if (edge) {
                        add(fr, la, b, f + 1, "prismarine_brick_slab[type=bottom]");
                    }
                }
            }
            add(fr, side * 7, 1, f - 1, "sea_lantern");
            add(fr, side * 6, -1, f + 1, "lily_pad");
            add(fr, side * 8, 3, f + 1, "lily_pad");
            emit("drip", fr.px(side * 7, 1) + 0.5, f + 1.2, fr.pz(side * 7, 1) + 0.5);
        }
        // Temple roof on pillars with a glass skylight.
        int[][] posts = {{-10, -6}, {-4, -6}, {4, -6}, {10, -6}, {-10, 2}, {10, 2}, {-9, 9}, {9, 9}};
        for (int[] post : posts) {
            for (int y = f + 1; y <= f + 8; y++) {
                add(fr, post[0], post[1], y, y == f + 8 ? "sea_lantern" : "prismarine_bricks");
            }
        }
        for (int a = -11; a <= 11; a++) {
            for (int b = -7; b <= 10; b++) {
                if (Math.sqrt(a * a + b * b) > 14.2) {
                    continue;
                }
                boolean skylight = Math.abs(a) <= 3 && b >= -2 && b <= 5;
                add(fr, a, b, f + 9, skylight ? "cyan_stained_glass" : "dark_prismarine");
                if (a == -11 || a == 11 || b == -7 || b == 10 || Math.sqrt(a * a + b * b) > 13.2) {
                    add(fr, a, b, f + 10, "prismarine_brick_slab[type=bottom]");
                }
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int y = f + 7; y <= f + 8; y++) {
                add(fr, a, 9, y, "prismarine_bricks");
            }
        }
        beacon(fr.x(0, 8), f + 10, fr.z(0, 8), "diamond_block", "cyan_stained_glass");
        portal(fr, "unranked", 8, "dark_prismarine", "prismarine_bricks", "sea_lantern", "prismarine_bricks", "queue-unranked", "cyan");
        for (int side = -1; side <= 1; side += 2) {
            add(fr, side * 3, -6, f + 8, "iron_chain[axis=y]");
            add(fr, side * 3, -6, f + 7, "sea_lantern");
        }
        BlockPos hidden = block(fr, -10, 8, f + 1);
        egg("unranked", hidden.x(), hidden.y(), hidden.z(), "turtle_egg[eggs=1,hatch=0]");
    }

    private void ffaGate(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (d > 6 && d <= 7) {
                return "red_nether_bricks";
            }
            double n = c.noise.fractal(fr.x(a, b), fr.z(a, b), 3, 2);
            return n > 0.66 ? "cracked_polished_blackstone_bricks" : n < 0.3 ? "blackstone" : "polished_blackstone_bricks";
        });
        // Colosseum wall around the back half with arched openings and crenellations.
        for (int a = -13; a <= 13; a++) {
            for (int b = -13; b <= 13; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d < 10 || d > 12 || b < -2) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(a, b));
                boolean gate = Math.abs(a) <= 3 && b > 0;
                boolean arch = !gate && Math.abs(((angle % 30) + 30) % 30 - 15) < 4 && d < 11;
                for (int y = f + 1; y <= f + 8; y++) {
                    if (gate && y <= f + 7) {
                        continue;
                    }
                    if (arch && y <= f + 4) {
                        continue;
                    }
                    add(fr, a, b, y, y == f + 5 && arch ? "red_nether_bricks" : c.mix("polished_blackstone_bricks", "cracked_polished_blackstone_bricks", 0.12));
                }
                if (((a + b) & 1) == 0) {
                    add(fr, a, b, f + 9, "polished_blackstone_brick_wall");
                }
            }
        }
        // The gate: blackstone and gold frame around a red curtain.
        for (int a = -3; a <= 3; a++) {
            add(fr, a, 10, f + 8, "red_nether_bricks");
            add(fr, a, 10, f + 7, Math.abs(a) == 3 ? "gilded_blackstone" : "chiseled_polished_blackstone");
            for (int y = f + 1; y <= f + 7; y++) {
                add(fr, a, 12, y, "crying_obsidian");
                add(fr, a, 11, y, "obsidian");
            }
        }
        portal(fr, "ffa", 10, "gilded_blackstone", "chiseled_polished_blackstone", "red_nether_bricks", "obsidian", "ffa:nodebuff", "red");
        beacon(fr.x(0, 11), f + 9, fr.z(0, 11), "netherite_block", "red_stained_glass");
        for (int side = -1; side <= 1; side += 2) {
            add(fr, side * 5, 6, f + 1, "polished_blackstone_wall");
            add(fr, side * 5, 6, f + 2, "polished_blackstone_wall");
            add(fr, side * 5, 6, f + 3, "soul_campfire[facing=" + fr.dir("front") + ",lit=true,signal_fire=false,waterlogged=false]");
            emit("soul", fr.px(side * 5, 6) + 0.5, f + 3.8, fr.pz(side * 5, 6) + 0.5);
            add(fr, side * 2, 9, f + 6, "iron_chain[axis=y]");
            add(fr, side * 2, 9, f + 5, "soul_lantern[hanging=true]");
        }
        // Crimson growth creeping over the arena floor.
        for (int i = 0; i < 26; i++) {
            double a = (c.rnd.nextDouble() - 0.5) * 20;
            double b = -9 + c.rnd.nextDouble() * 16;
            if (Math.abs(a) < 3 || Math.sqrt(a * a + b * b) > 11.5) {
                continue;
            }
            int x = fr.x(a, b);
            int z = fr.z(a, b);
            if (c.isAir(x, f + 1, z)) {
                c.set(x, f, z, "crimson_nylium");
                c.plant(x, f + 1, z, c.pick("crimson_roots", "crimson_fungus", "crimson_roots"), "crimson_nylium");
            }
        }
        emit("flame", fr.px(0, 9) + 0.5, f + 7.2, fr.pz(0, 9) + 0.5);
        BlockPos nook = block(fr, -10, 3, f + 1);
        add(fr, -10, 3, f, "polished_blackstone_bricks");
        egg("ffa", nook.x(), nook.y(), nook.z(), "dragon_egg");
    }

    private void hallOfFame(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (Math.abs(a) <= 1 && b < 7) {
                return "deepslate_tiles";
            }
            return d > 11 ? "deepslate_bricks" : c.mix("polished_deepslate", "deepslate_tiles", 0.2);
        });
        // The wall of fame: panels in front of polished blackstone slabs, gold trim and lanterns on top.
        int face = 7;
        for (int a = -10; a <= 10; a++) {
            for (int y = f - 2; y <= f + 15; y++) {
                add(fr, a, face + 1, y, y <= f ? "deepslate_tiles" : "deepslate_bricks");
                add(fr, a, face, y, y <= f ? "deepslate_tiles" : "polished_deepslate");
            }
            add(fr, a, face, f + 16, "gold_block");
            add(fr, a, face + 1, f + 16, "deepslate_brick_slab[type=bottom]");
        }
        int[] columns = {-8, -4, 0, 4, 8};
        double[] rows = {f + 10.6, f + 6.6, f + 2.6};
        for (double row : rows) {
            for (int column : columns) {
                for (int da = -1; da <= 1; da++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        add(fr, column + da, face, (int) row + dy, "polished_blackstone");
                    }
                    add(fr, column + da, face, (int) row + 3, "gold_block");
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = f - 2; y <= f + 17; y++) {
                add(fr, side * 11, face, y, "polished_blackstone_bricks");
            }
            add(fr, side * 11, face, f + 18, "lantern[hanging=false]");
        }
        double[] front = {fr.px(0, -1) - fr.px(0, 0), fr.pz(0, -1) - fr.pz(0, 0)};
        wall = new Wall(new Point(fr.px(columns[0], face) + 0.5 + front[0] * 0.6, rows[0], fr.pz(columns[0], face) + 0.5 + front[1] * 0.6,
                fr.frontYaw(), 0), columns.length, 4, 4);
        holograms.put("hall-of-fame", new Point(fr.px(0, face) + 0.5 + front[0] * 0.8, f + 14.4, fr.pz(0, face) + 0.5 + front[1] * 0.8, 0, 0));
        // Podium: champion on gold, runners-up on iron and copper.
        for (int y = f + 1; y <= f + 2; y++) {
            add(fr, 0, 2, y, "gold_block");
        }
        add(fr, -2, 2, f + 1, "iron_block");
        add(fr, 2, 2, f + 1, "waxed_copper_block");
        npcs.put("leaderboards", new Point(fr.px(0, 2) + 0.5, f + 3, fr.pz(0, 2) + 0.5, fr.frontYaw(), 0));
        emit("sparkle", fr.px(0, 2) + 0.5, f + 5.5, fr.pz(0, 2) + 0.5);
        BlockPos lectern = block(fr, 0, -4, f + 1);
        c.set(lectern.x(), lectern.y(), lectern.z(), "lectern[facing=" + fr.dir("front") + ",has_book=false,powered=false]");
        buttons.add(new Button(lectern, "leaderboards"));
        for (int side = -1; side <= 1; side += 2) {
            c.lanternPost(fr.x(side * 6, -5), f + 1, fr.z(side * 6, -5), "deepslate_brick_wall", 3, "lantern");
        }
        BlockPos corner = block(fr, 10, 6, f + 1);
        egg("hall-of-fame", corner.x(), corner.y(), corner.z(), "turtle_egg[eggs=1,hatch=0]");
    }

    private void cosmeticsShop(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (d <= 6) {
                return ((a + b) & 1) == 0 ? "purpur_block" : "smooth_quartz";
            }
            if (d <= 7) {
                return "amethyst_block";
            }
            return c.mix("smooth_quartz", "calcite", 0.25);
        });
        // Boutique: back wall with windows, a counter, shelves and a striped awning.
        for (int a = -8; a <= 8; a++) {
            for (int y = f + 1; y <= f + 6; y++) {
                boolean window = Math.abs(a) >= 3 && Math.abs(a) <= 6 && y >= f + 3 && y <= f + 4;
                add(fr, a, 9, y, window ? "magenta_stained_glass_pane" : Math.abs(a) % 4 == 0 ? "purpur_pillar[axis=y]" : "purpur_block");
            }
            add(fr, a, 8, f + 1, "bookshelf");
            add(fr, a, 8, f + 2, Math.abs(a) % 3 == 0 ? "decorated_pot[cracked=false,facing=" + fr.dir("front") + ",waterlogged=false]" : "bookshelf");
            if (a != 0) {
                add(fr, a, 4, f + 1, Math.abs(a) <= 5 ? "stripped_cherry_log[axis=" + axisA(fr) + "]" : "air");
            }
            for (int b = 0; b <= 9; b++) {
                if (Math.abs(a) <= 8) {
                    add(fr, a, b, f + 7, ((a + 8) / 2) % 2 == 0 ? "magenta_wool" : "white_wool");
                }
            }
            add(fr, a, -1, f + 6, ((a + 8) / 2) % 2 == 0 ? "magenta_carpet" : "white_carpet");
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = f + 1; y <= f + 6; y++) {
                add(fr, side * 8, 0, y, "cherry_fence");
                for (int b = 1; b <= 8; b++) {
                    add(fr, side * 8, b, y, y >= f + 3 && y <= f + 4 && b % 3 != 0 ? "magenta_stained_glass_pane" : "purpur_block");
                }
            }
            wallBanner(fr.x(side * 8, -1), f + 5, fr.z(side * 8, -1), fr.dir("front"), "magenta");
        }
        BlockPos table = block(fr, 0, 4, f + 1);
        c.set(table.x(), table.y(), table.z(), "enchanting_table");
        buttons.add(new Button(table, "cosmetics"));
        emit("enchant", table.x() + 0.5, f + 2.2, table.z() + 0.5);
        npcs.put("cosmetics", new Point(fr.px(0, 6) + 0.5, f + 1, fr.pz(0, 6) + 0.5, fr.frontYaw(), 0));
        holograms.put("cosmetics", at(fr, 0, -1.5, f + 8.2));
        // Display pedestals with crystals and candles.
        double[][] stands = {{-6, -5}, {6, -5}, {-3, -9}, {3, -9}};
        for (int i = 0; i < stands.length; i++) {
            add(fr, stands[i][0], stands[i][1], f + 1, "purpur_pillar[axis=y]");
            add(fr, stands[i][0], stands[i][1], f + 2, i % 2 == 0 ? "amethyst_cluster[facing=up,waterlogged=false]"
                    : "purple_candle[candles=3,lit=true,waterlogged=false]");
            emit("glow", fr.px(stands[i][0], stands[i][1]) + 0.5, f + 2.8, fr.pz(stands[i][0], stands[i][1]) + 0.5);
        }
        for (int side = -1; side <= 1; side += 2) {
            int x = fr.x(side * 10.5, -3);
            int z = fr.z(side * 10.5, -3);
            if (!c.isAir(x, f, z)) {
                c.broadTree(x, f + 1, z, 5, "cherry_log", "cherry_leaves");
                emit("leaves", x + 0.5, f + 5, z + 0.5);
            }
        }
        BlockPos hidden = block(fr, 7, 7, f + 1);
        egg("cosmetics", hidden.x(), hidden.y(), hidden.z(), "turtle_egg[eggs=2,hatch=0]");
    }

    private void infoPavilion(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (d <= 8.5) {
                return d > 7.5 ? "lime_concrete" : c.mix("smooth_stone", "polished_diorite", 0.3);
            }
            return "grass_block";
        });
        // Gazebo: birch posts carrying a green copper roof and a bell.
        for (int k = 0; k < 8; k++) {
            double angle = Math.toRadians(22.5 + 45 * k);
            int x = fr.x(7 * Math.sin(angle), 7 * Math.cos(angle));
            int z = fr.z(7 * Math.sin(angle), 7 * Math.cos(angle));
            c.column(x, z, f + 1, f + 5, "stripped_birch_log[axis=y]");
        }
        for (int a = -9; a <= 9; a++) {
            for (int b = -9; b <= 9; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d <= 8.5 && d > 5) {
                    add(fr, a, b, f + 6, "waxed_oxidized_cut_copper");
                }
                if (d <= 6.5 && d > 3) {
                    add(fr, a, b, f + 7, "waxed_oxidized_cut_copper");
                }
                if (d <= 4.5 && d > 1) {
                    add(fr, a, b, f + 8, "waxed_weathered_cut_copper");
                }
                if (d <= 2.5) {
                    add(fr, a, b, f + 9, "waxed_weathered_copper");
                }
            }
        }
        add(fr, 0, 0, f + 10, "lightning_rod[facing=up,powered=false,waterlogged=false]");
        BlockPos bell = block(fr, 0, 0, f + 8);
        c.set(bell.x(), bell.y(), bell.z(), "bell[attachment=ceiling,facing=" + fr.dir("front") + ",powered=false]");
        for (int side = -1; side <= 1; side += 2) {
            BlockPos lectern = block(fr, side * 2, -1, f + 1);
            c.set(lectern.x(), lectern.y(), lectern.z(), "lectern[facing=" + fr.dir("front") + ",has_book=false,powered=false]");
            buttons.add(new Button(lectern, side < 0 ? "message:lobby.rules" : "message:lobby.links"));
            // Notice boards either side of the gazebo, with their texts floating in front.
            for (int a = 9; a <= 11; a++) {
                for (int y = f + 1; y <= f + 4; y++) {
                    add(fr, side * a, 2, y, a == 10 && y > f + 1 ? "dark_oak_planks" : "dark_oak_log[axis=y]");
                }
            }
            add(fr, side * 10, 2, f + 5, "dark_oak_slab[type=bottom]");
        }
        holograms.put("rules", at(fr, -10, 0.9, f + 2.2));
        holograms.put("links", at(fr, 10, 0.9, f + 2.2));
        npcs.put("info", new Point(fr.px(0, 2) + 0.5, f + 1, fr.pz(0, 2) + 0.5, fr.frontYaw(), 0));
        // Flower beds around the gazebo.
        for (int a = -12; a <= 12; a++) {
            for (int b = -12; b <= 12; b++) {
                double d = Math.sqrt(a * a + b * b);
                int x = fr.x(a, b);
                int z = fr.z(a, b);
                if (d > 9 && d < 12 && c.get(x, f, z).contains("grass") && c.isAir(x, f + 1, z) && !inCorridor(x, z, 2.6)) {
                    c.plant(x, f + 1, z, c.pick("lily_of_the_valley", "oxeye_daisy", "cornflower", "pink_tulip", "white_tulip"), "grass_block");
                }
            }
        }
        emit("spores", fr.px(0, 0) + 0.5, f + 11, fr.pz(0, 0) + 0.5);
        BlockPos bed = block(fr, 10, 7, f + 1);
        c.set(bed.x(), f, bed.z(), "grass_block");
        egg("info", bed.x(), bed.y(), bed.z(), "dragon_egg");
    }

    private void partyLounge(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            if (d <= 2) {
                return "cobblestone";
            }
            return ((int) d) % 3 == 0 ? "dark_oak_planks" : "spruce_planks";
        });
        // Campfire circle with stair seats.
        add(fr, 0, 2, f + 1, "campfire[facing=" + fr.dir("front") + ",lit=true,signal_fire=false,waterlogged=false]");
        for (int a = -5; a <= 5; a++) {
            for (int b = -3; b <= 7; b++) {
                double d = Math.sqrt(a * a + (b - 2) * (b - 2));
                if (d > 3.5 && d <= 4.5 && !(Math.abs(a) <= 1 && b < 2)) {
                    int x = fr.x(a, b);
                    int z = fr.z(a, b);
                    c.set(x, f + 1, z, "spruce_stairs[facing=" + Canvas.facingAway(x, z, fr.px(0, 2) + 0.5, fr.pz(0, 2) + 0.5) + ",half=bottom]");
                }
            }
        }
        emit("smoke", fr.px(0, 2) + 0.5, f + 2, fr.pz(0, 2) + 0.5);
        // String lights between four posts.
        int[][] posts = {{-8, -3}, {8, -3}, {-8, 8}, {8, 8}};
        for (int[] post : posts) {
            for (int y = f + 1; y <= f + 5; y++) {
                add(fr, post[0], post[1], y, "spruce_fence");
            }
        }
        for (int a = -7; a <= 7; a++) {
            for (int b : new int[]{-3, 8}) {
                add(fr, a, b, f + 5, "iron_chain[axis=" + axisA(fr) + "]");
                if (a % 3 == 0) {
                    add(fr, a, b, f + 4, "lantern[hanging=true]");
                }
            }
        }
        for (int b = -2; b <= 7; b++) {
            for (int a : new int[]{-8, 8}) {
                add(fr, a, b, f + 5, "iron_chain[axis=" + axisB(fr) + "]");
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            wallBanner(fr.x(side * 8, -4), f + 4, fr.z(side * 8, -4), fr.dir("front"), side < 0 ? "pink" : "red");
            // Tables with cake.
            add(fr, side * 6, 5, f + 1, "spruce_fence");
            add(fr, side * 6, 5, f + 2, "spruce_pressure_plate[powered=false]");
            add(fr, side * 7, 5, f + 1, "spruce_planks");
            add(fr, side * 7, 5, f + 2, "cake[bites=0]");
            add(fr, side * 9, 1, f + 1, "hay_block[axis=y]");
            add(fr, side * 9, 2, f + 1, "hay_block[axis=" + axisA(fr) + "]");
        }
        add(fr, 6, 9, f + 1, "jukebox[has_record=false]");
        add(fr, 7, 9, f + 1, "note_block[instrument=harp,note=0,powered=false]");
        emit("notes", fr.px(6.5, 9) + 0.5, f + 2.2, fr.pz(6.5, 9) + 0.5);
        BlockPos bell = block(fr, -6, 9, f + 1);
        c.set(bell.x(), bell.y(), bell.z(), "bell[attachment=floor,facing=" + fr.dir("front") + ",powered=false]");
        buttons.add(new Button(bell, "party-fight"));
        npcs.put("party", new Point(fr.px(0, -5) + 0.5, f + 1, fr.pz(0, -5) + 0.5, fr.frontYaw(), 0));
        holograms.put("party", at(fr, 0, 8, f + 6.8));
        BlockPos behind = block(fr, 9, 9, f + 1);
        egg("party", behind.x(), behind.y(), behind.z(), "turtle_egg[eggs=3,hatch=0]");
    }

    private void kitWorkshop(Frame fr) {
        floor(fr, 12.5, (a, b, d) -> {
            double n = c.noise.fractal(fr.x(a, b), fr.z(a, b), 4, 2);
            return n > 0.62 ? "cobblestone" : n < 0.35 ? "andesite" : "stone_bricks";
        });
        // Workshop shell: timber frame, plank walls, open front, slab roof.
        for (int a = -9; a <= 9; a++) {
            for (int b = 0; b <= 9; b++) {
                boolean side = Math.abs(a) == 9;
                boolean back = b == 9;
                if (!side && !back) {
                    add(fr, a, b, f + 7, "dark_oak_slab[type=bottom]");
                    continue;
                }
                for (int y = f + 1; y <= f + 6; y++) {
                    boolean post = (side && (b == 0 || b == 9 || b == 5)) || (back && Math.abs(a) % 4 == 1);
                    boolean window = side && y >= f + 3 && y <= f + 4 && (b == 2 || b == 3 || b == 7);
                    add(fr, a, b, y, post ? "stripped_spruce_log[axis=y]" : window ? "glass_pane" : "spruce_planks");
                }
                add(fr, a, b, f + 7, "dark_oak_planks");
            }
            add(fr, a, -1, f + 7, "dark_oak_slab[type=bottom]");
        }
        // Chimney with a signal campfire on top for a tall smoke column.
        for (int y = f + 1; y <= f + 11; y++) {
            add(fr, -7, 8, y, "bricks");
        }
        add(fr, -7, 8, f + 12, "campfire[facing=" + fr.dir("front") + ",lit=true,signal_fire=true,waterlogged=false]");
        add(fr, -7, 7, f + 1, "blast_furnace[facing=" + fr.dir("front") + ",lit=true]");
        emit("flame", fr.px(-7, 6.4) + 0.5, f + 1.5, fr.pz(-7, 6.4) + 0.5);
        add(fr, -5, 7, f + 1, "grindstone[face=floor,facing=" + fr.dir("front") + "]");
        add(fr, -3, 8, f + 1, "water_cauldron[level=3]");
        add(fr, 7, 8, f + 1, "barrel[facing=up,open=false]");
        add(fr, 8, 8, f + 1, "barrel[facing=up,open=false]");
        add(fr, 8, 7, f + 1, "barrel[facing=up,open=false]");
        add(fr, 8, 8, f + 2, "barrel[facing=up,open=false]");
        for (int a = -2; a <= 5; a++) {
            add(fr, a, 8, f + 1, a == 0 ? "smithing_table" : a == 3 ? "crafting_table" : "bookshelf");
            add(fr, a, 8, f + 2, "bookshelf");
        }
        String[][] clickable = {{"-3", "3", "anvil[facing=" + fr.dir("right") + "]"}, {"3", "3", "anvil[facing=" + fr.dir("right") + "]"},
            {"0", "8", "smithing_table"}, {"3", "8", "crafting_table"}};
        for (String[] item : clickable) {
            BlockPos pos = block(fr, Integer.parseInt(item[0]), Integer.parseInt(item[1]), f + 1);
            c.set(pos.x(), pos.y(), pos.z(), item[2]);
            buttons.add(new Button(pos, "kit-editor"));
        }
        for (int side = -1; side <= 1; side += 2) {
            c.lanternPost(fr.x(side * 10, -2), f + 1, fr.z(side * 10, -2), "spruce_fence", 3, "lantern");
            wallBanner(fr.x(side * 9, -1), f + 5, fr.z(side * 9, -1), fr.dir("front"), "brown");
        }
        add(fr, 0, 4, f + 6, "iron_chain[axis=y]");
        add(fr, 0, 4, f + 5, "lantern[hanging=true]");
        holograms.put("kit-editor", at(fr, 0, -1.5, f + 8.4));
        BlockPos hidden = block(fr, 8, 7, f + 2);
        egg("kit-editor", hidden.x(), hidden.y(), hidden.z(), "turtle_egg[eggs=1,hatch=0]");
    }

    // ------------------------------------------------------------------ parkour

    /**
     * Spiral sky parkour around a crystal spire: a launch pad from the plaza reaches the start island, two loops of
     * jumps with checkpoints climb to a summit platform with the finish plate and a pad back down.
     */
    private void parkour() {
        double[] axis = polar(PARKOUR_ANGLE, PARKOUR_AXIS);
        double ax = Math.floor(axis[0]) + 0.5;
        double az = Math.floor(axis[1]) + 0.5;
        int base = f + 14;
        // Start island on the plaza side of the spiral.
        double towardPlaza = Math.toDegrees(Math.atan2(MID - ax, -(MID - az)));
        double startAngle = towardPlaza;
        double sx = ax + 13 * Math.sin(Math.toRadians(startAngle));
        double sz = az - 13 * Math.cos(Math.toRadians(startAngle));
        c.disc(sx, base, sz, 4.2, "polished_andesite");
        c.ring(sx, base, sz, 3.2, 4.2, "stone_bricks");
        c.underside(sx, base - 1, sz, 4.2, 6, "stone", "andesite", "moss_block");
        int startX = (int) Math.floor(sx);
        int startZ = (int) Math.floor(sz);
        BlockPos start = new BlockPos(startX, base + 1, startZ);
        c.set(startX, base + 1, startZ, "light_weighted_pressure_plate[power=0]");
        c.set(startX, base, startZ, "emerald_block");
        holograms.put("parkour", Point.of(startX + 0.5, base + 3.4, startZ + 0.5));
        zones.add(new Zone("parkour", ax, az, PARKOUR_RADIUS + 6, base - 2));

        // Spire at the axis, up to the summit platform.
        int count = 30;
        int summit = base + count / 2 + 1;
        for (int y = base - 12; y < summit; y++) {
            double radius = 2.2 - (y - base + 12) * 0.02;
            for (int x = (int) ax - 3; x <= (int) ax + 3; x++) {
                for (int z = (int) az - 3; z <= (int) az + 3; z++) {
                    if (Canvas.dist(x, z, ax, az) <= radius) {
                        c.set(x, y, z, (y - base) % 6 == 0 ? "sea_lantern" : c.mix("calcite", "amethyst_block", 0.3));
                    }
                }
            }
        }
        // Stones: two loops rising one block every other jump, a checkpoint every eight stones. Each stone goes where
        // the gap from the previous one is closest to a comfortable jump (shorter when it climbs).
        List<BlockPos> checkpoints = new ArrayList<>();
        double angle = startAngle;
        int y = base;
        BlockPos previous = start.add(0, -1, 0);
        double previousHalf = 3.7;
        String[] kinds = {"quartz_block", "prismarine_bricks", "stone_bricks", "polished_andesite", "purpur_block", "smooth_stone"};
        for (int i = 1; i <= count; i++) {
            boolean rising = i % 2 == 0;
            boolean checkpoint = i % 8 == 0;
            double half = checkpoint ? 1.5 : 0.5;
            double radius = PARKOUR_RADIUS + (i % 3 == 0 && i < count ? 0.6 : 0);
            double ideal = rising ? 2.0 : 2.6;
            double bestAngle = angle + 20;
            double bestError = Double.MAX_VALUE;
            for (double candidate = angle + 8; candidate <= angle + 45; candidate += 0.5) {
                int x = (int) Math.floor(ax + radius * Math.sin(Math.toRadians(candidate)));
                int z = (int) Math.floor(az - radius * Math.cos(Math.toRadians(candidate)));
                double gap = Math.hypot(x - previous.x(), z - previous.z()) - previousHalf - half;
                if (gap >= 1.0 && Math.abs(gap - ideal) < bestError) {
                    bestError = Math.abs(gap - ideal);
                    bestAngle = candidate;
                }
            }
            angle = bestAngle;
            if (rising) {
                y++;
            }
            int x = (int) Math.floor(ax + radius * Math.sin(Math.toRadians(angle)));
            int z = (int) Math.floor(az - radius * Math.cos(Math.toRadians(angle)));
            stones.add(new BlockPos(x, y, z));
            previous = new BlockPos(x, y, z);
            previousHalf = half;
            if (checkpoint) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        c.set(x + dx, y, z + dz, "chiseled_stone_bricks");
                    }
                }
                c.set(x, y, z, "gold_block");
                c.set(x, y + 1, z, "light_weighted_pressure_plate[power=0]");
                checkpoints.add(new BlockPos(x, y + 1, z));
            } else {
                String kind = kinds[i % kinds.length];
                if (i % 5 == 3) {
                    c.set(x, y, z, kind.equals("purpur_block") ? "purpur_slab[type=top]" : "stone_brick_slab[type=top]");
                } else {
                    c.set(x, y, z, kind);
                }
                if (i % 7 == 4) {
                    c.set(x, y - 1, z, "iron_chain[axis=y]");
                    c.set(x, y - 2, z, "lantern[hanging=true]");
                }
            }
        }
        // Summit platform on top of the spire, reachable from the last stone.
        c.disc(ax, summit, az, 6.6, "polished_blackstone");
        c.ring(ax, summit, az, 5.6, 6.6, "gold_block");
        c.underside(ax, summit - 1, az, 6.4, 4, "calcite", "amethyst_block");
        int fx = (int) Math.floor(ax);
        int fz = (int) Math.floor(az);
        c.set(fx, summit, fz, "diamond_block");
        c.set(fx, summit + 1, fz, "heavy_weighted_pressure_plate[power=0]");
        BlockPos finish = new BlockPos(fx, summit + 1, fz);
        for (int dx = -1; dx <= 1; dx += 2) {
            c.set(fx + dx * 3, summit + 1, fz, "end_rod[facing=up]");
            c.set(fx, summit + 1, fz + dx * 3, "end_rod[facing=up]");
        }
        emit("glow", ax, summit + 2, az);
        emit("sparkle", ax, summit + 3, az);
        egg("parkour-summit", fx + 2, summit + 1, fz + 2, "dragon_egg");
        parkour = new Parkour(start, checkpoints, finish, base - 3);

        // Pads: plaza to the start island, summit back to the plaza.
        double[] padFrom = polar(PARKOUR_ANGLE, 27.5);
        int px = (int) Math.floor(padFrom[0]);
        int pz = (int) Math.floor(padFrom[1]);
        launchPad(px, f, pz, startX + 0.5 + (px + 0.5 - sx) * 0.12, base + 1, startZ + 0.5 + (pz + 0.5 - sz) * 0.12, 3);
        double[] back = polar(PARKOUR_ANGLE, 25);
        double dir = Math.toDegrees(Math.atan2(back[0] - ax, -(back[1] - az)));
        int rx = (int) Math.floor(ax + 4.5 * Math.sin(Math.toRadians(dir)));
        int rz = (int) Math.floor(az - 4.5 * Math.cos(Math.toRadians(dir)));
        launchPad(rx, summit, rz, back[0], f + 1, back[1], 2);
    }

    private void launchPad(int x, int y, int z, double tx, double ty, double tz, double clearance) {
        c.set(x, y, z, "redstone_block");
        c.set(x, y + 1, z, "heavy_weighted_pressure_plate[power=0]");
        double[] v = LaunchMath.solve(x + 0.5, y + 1, z + 0.5, tx, ty, tz, clearance);
        pads.add(new LaunchPad(new BlockPos(x, y + 1, z), v[0], v[1], v[2]));
        emit("sparkle", x + 0.5, y + 1.3, z + 0.5);
    }

    /** Pads at the plaza exits towards the far halls, landing on each hall's entrance. */
    private void expressPads() {
        for (ZoneSpec zone : ZONES) {
            if (!List.of("ranked", "unranked", "ffa", "leaderboards", "kit-editor").contains(zone.id())) {
                continue;
            }
            double[] from = polar(zone.angle(), 26.5);
            double[] to = polar(zone.angle(), ZONE_DISTANCE - 8);
            launchPad((int) Math.floor(from[0]), f, (int) Math.floor(from[1]), to[0], f + 1, to[1], 3);
        }
    }

    // ------------------------------------------------------------------ skyline

    /** Decorative floating islands and crystal spires beyond the barrier. */
    private void skyline() {
        int islands = 12;
        for (int i = 0; i < islands; i++) {
            double angle = i * 360.0 / islands + 15 + (c.rnd.nextDouble() - 0.5) * 12;
            double distance = 84 + c.rnd.nextDouble() * 7;
            double radius = 3.5 + c.rnd.nextDouble() * 4;
            double[] p = polar(angle, distance);
            int top = f - 10 + c.rnd.nextInt(30);
            c.disc(p[0], top, p[1], radius, "grass_block");
            c.disc(p[0], top - 1, p[1], radius - 0.5, "dirt");
            c.underside(p[0], top - 2, p[1], radius, (int) (radius * 2.2), "stone", "andesite", "dirt", "tuff");
            int x = (int) Math.floor(p[0]);
            int z = (int) Math.floor(p[1]);
            switch (i % 4) {
                case 0 -> c.broadTree(x, top + 1, z, 5, c.pick("oak_log", "birch_log"), c.pick("oak_leaves", "birch_leaves"));
                case 1 -> c.conifer(x, top + 1, z, 8, "spruce_log", "spruce_leaves");
                case 2 -> {
                    for (int k = 0; k < 4; k++) {
                        int rx = x + (k < 2 ? -2 : 2);
                        int rz = z + (k % 2 == 0 ? -2 : 2);
                        c.column(rx, rz, top + 1, top + 1 + c.rnd.nextInt(5), c.pick("mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks"));
                    }
                }
                default -> c.broadTree(x, top + 1, z, 5, "cherry_log", "cherry_leaves");
            }
        }
        // Crystal spires in the corners.
        double[][] corners = {{22, 22}, {178, 22}, {22, 178}, {178, 178}};
        String[][] crystals = {{"amethyst_block", "purpur_block"}, {"prismarine", "sea_lantern"}, {"quartz_block", "calcite"},
            {"amethyst_block", "calcite"}};
        for (int i = 0; i < corners.length; i++) {
            double cx = corners[i][0];
            double cz = corners[i][1];
            int bottom = f - 18 + c.rnd.nextInt(8);
            c.disc(cx, bottom, cz, 7, "stone");
            c.underside(cx, bottom - 1, cz, 7, 10, "stone", "andesite", "tuff");
            int height = 38 + c.rnd.nextInt(18);
            for (int y = 0; y < height; y++) {
                double radius = 4.5 * (1 - (double) y / height) + 0.4;
                for (int x = (int) cx - 5; x <= (int) cx + 5; x++) {
                    for (int z = (int) cz - 5; z <= (int) cz + 5; z++) {
                        if (Canvas.dist(x, z, cx, cz) <= radius) {
                            c.set(x, bottom + 1 + y, z, y % 9 == 4 ? crystals[i][1] : crystals[i][0]);
                        }
                    }
                }
            }
        }
    }

    /** Invisible barrier cylinder around the playable area, from below the islands to the top of the template. */
    private void barrier() {
        int r = (int) Math.ceil(BARRIER_RADIUS) + 1;
        for (int x = CENTER - r; x <= CENTER + r; x++) {
            for (int z = CENTER - r; z <= CENTER + r; z++) {
                double d = Canvas.dist(x, z, MID, MID);
                if (d > BARRIER_RADIUS - 1.1 && d <= BARRIER_RADIUS) {
                    for (int y = f - 8; y < HEIGHT; y++) {
                        c.setIfAir(x, y, z, "barrier");
                    }
                }
            }
        }
    }
}
