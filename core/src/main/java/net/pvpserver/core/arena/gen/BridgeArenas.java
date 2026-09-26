package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.RelativeBox;
import net.pvpserver.core.arena.RelativePosition;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.NORTH;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.spawn;

/**
 * Bridge maps (tag {@code bridge}): two team bases with a goal pit at the back, a narrow bridge and a middle island
 * over the void (or water). Blocks may only be placed in the corridor between the bases, six blocks above and five
 * below the bridge, and never next to a goal.
 * <p>
 * Layout along z: team A base z 0-14 (red, goal at z 3), bridge z 15-55 with a middle island at z 31-39, team B
 * base z 56-70 (blue, goal at z 67). The playing surface is y 10 (players stand at y 11).
 */
final class BridgeArenas {

    private static final int SX = 21;
    private static final int SZ = 71;
    private static final int MID = 10;
    private static final int TOP = 10;

    private BridgeArenas() {
    }

    /** Materials for one map. */
    private record Style(String surface, String surfaceAlt, String underside, String redTrim, String blueTrim,
                         String bridge, String bridgeStripe, String island, String islandCentre, String pillarLight) {
    }

    static GeneratedArena classic() {
        return build("bridge_classic", "<aqua>The Bridge", "WHITE_TERRACOTTA", 10001, false,
                new Style("white_terracotta", "light_gray_terracotta", "smooth_stone", "red_terracotta", "blue_terracotta",
                        "white_terracotta", "light_gray_terracotta", "white_terracotta", "sea_lantern", "sea_lantern"));
    }

    static GeneratedArena ruins() {
        return build("bridge_ruins", "<gray>Sunken Ruins", "MOSSY_STONE_BRICKS", 10002, true,
                new Style("stone_bricks", "mossy_stone_bricks", "cobblestone", "red_concrete", "blue_concrete",
                        "polished_andesite", "andesite", "chiseled_stone_bricks", "sea_lantern", "lantern[hanging=false]"));
    }

    static GeneratedArena nether() {
        return build("bridge_nether", "<red>Nether Crossing", "POLISHED_BLACKSTONE_BRICKS", 10003, false,
                new Style("polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone", "red_nether_bricks",
                        "warped_wart_block", "polished_blackstone", "blackstone", "polished_blackstone_bricks", "shroomlight",
                        "soul_lantern[hanging=false]"));
    }

    private static GeneratedArena build(String name, String display, String icon, long seed, boolean water, Style style) {
        Canvas c = new Canvas(SX, 24, SZ, seed);
        if (water) {
            c.fill(0, 0, 0, SX - 1, 0, SZ - 1, "dark_prismarine");
            c.fill(0, 1, 0, SX - 1, 1, SZ - 1, "water");
            c.perimeter(0, 0, SX - 1, SZ - 1, 1, 1, "prismarine");
        }
        base(c, style, 0, true);
        base(c, style, SZ - 15, false);
        // The bridge: three wide with a centre stripe, and a middle island.
        for (int z = 15; z <= SZ - 16; z++) {
            for (int x = MID - 1; x <= MID + 1; x++) {
                c.set(x, TOP, z, x == MID ? style.bridgeStripe() : style.bridge());
            }
            c.set(MID, TOP - 1, z, style.underside());
        }
        for (int x = MID - 4; x <= MID + 4; x++) {
            for (int z = 31; z <= 39; z++) {
                boolean edge = x == MID - 4 || x == MID + 4 || z == 31 || z == 39;
                c.set(x, TOP, z, edge ? style.bridgeStripe() : style.island());
            }
        }
        c.set(MID, TOP, 35, style.islandCentre());
        c.underside(MID + 0.5, TOP - 1, 35.5, 5, 4, style.underside(), style.surfaceAlt());
        if (water) {
            // Stone piers from the water up to the bridge.
            for (int z : new int[]{20, 27, 43, 50}) {
                c.column(MID, z, 2, TOP - 2, "prismarine_bricks");
                c.set(MID, TOP - 1, z, "sea_lantern");
            }
        }
        RelativePosition goalA = new RelativePosition(MID + 0.5, TOP, 3.5, 0f, 0f);
        RelativePosition goalB = new RelativePosition(MID + 0.5, TOP, SZ - 3.5, 0f, 0f);
        return new GeneratedArena.Builder(name, c.b)
                .display(display, icon)
                .tags("bridge")
                .spawns(spawn(MID, TOP + 1, 10, SOUTH), spawn(MID, TOP + 1, SZ - 11, NORTH))
                .spectator(new RelativePosition(SX + 4, TOP + 10, SZ / 2.0, EAST + 180, 30f))
                .goals(goalA, goalB, 1.6)
                .limits(TOP + 6, water ? 5 : 3)
                .buildArea(RelativeBox.of(1, TOP - 5, 1, SX - 2, TOP + 6, SZ - 2))
                .build();
    }

    /** A 15x15 team base with a tapered underside, the goal pit at the back, trim and corner lights. */
    private static void base(Canvas c, Style style, int z0, boolean red) {
        String trim = red ? style.redTrim() : style.blueTrim();
        String glass = red ? "red_stained_glass" : "blue_stained_glass";
        int x0 = 3;
        int x1 = 17;
        int z1 = z0 + 14;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                c.set(x, TOP, z, edge ? trim : c.mix(style.surface(), style.surfaceAlt(), 0.15));
            }
        }
        for (int layer = 1; layer <= 3; layer++) {
            for (int x = x0 + layer; x <= x1 - layer; x++) {
                for (int z = z0 + layer; z <= z1 - layer; z++) {
                    c.set(x, TOP - layer, z, style.underside());
                }
            }
        }
        // Goal pit (3x3, two deep) at the back of the base, floored with team glass.
        int goalZ = red ? z0 + 3 : z1 - 3;
        for (int x = MID - 2; x <= MID + 2; x++) {
            for (int z = goalZ - 2; z <= goalZ + 2; z++) {
                c.set(x, TOP, z, trim);
            }
        }
        for (int x = MID - 1; x <= MID + 1; x++) {
            for (int z = goalZ - 1; z <= goalZ + 1; z++) {
                c.set(x, TOP, z, "air");
                c.set(x, TOP - 1, z, "air");
                c.set(x, TOP - 2, z, glass);
            }
        }
        // Spawn marker and corner light pillars.
        int spawnZ = red ? z0 + 10 : z1 - 10;
        c.set(MID, TOP, spawnZ, trim);
        for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            c.column(p[0], p[1], TOP + 1, TOP + 2, trim);
            c.set(p[0], TOP + 3, p[1], style.pillarLight());
        }
    }
}
