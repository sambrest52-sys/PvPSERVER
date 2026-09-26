package net.pvpserver.core.arena.gen;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.WEST;
import static net.pvpserver.core.arena.gen.Canvas.dist;
import static net.pvpserver.core.arena.gen.Canvas.spawn;
import static net.pvpserver.core.arena.gen.Canvas.view;

/**
 * Sumo platforms (tag {@code sumo}) in different sizes and shapes, with water or void below. The void Y sits just
 * under each platform, so leaving it (or touching water) loses the round.
 */
final class SumoArenas {

    private SumoArenas() {
    }

    /** 13x13 wooden dojo over a pool, with torii gates and stone lanterns. */
    static GeneratedArena dojo() {
        Canvas c = new Canvas(23, 13, 23, 6001);
        int mid = 11;
        c.fill(0, 0, 0, 22, 0, 22, "stone");
        c.perimeter(0, 0, 22, 22, 1, 3, "stone_bricks");
        for (int i = 0; i < 23; i++) {
            if (c.rnd.nextDouble() < 0.3) {
                c.set(i, 3, 0, "mossy_stone_bricks");
            }
            if (c.rnd.nextDouble() < 0.3) {
                c.set(0, 3, i, "mossy_stone_bricks");
            }
        }
        c.fill(1, 1, 1, 21, 2, 21, "water");
        for (int x = 5; x <= 17; x++) {
            for (int z = 5; z <= 17; z++) {
                boolean edge = x == 5 || x == 17 || z == 5 || z == 17;
                boolean inner = x == 6 || x == 16 || z == 6 || z == 16;
                String block = edge ? "red_wool" : inner ? "spruce_planks" : (x + z) % 2 == 0 ? "birch_planks" : "stripped_birch_log[axis=x]";
                c.set(x, 6, z, block);
            }
        }
        for (int z = 10; z <= 12; z++) {
            c.set(mid - 3, 6, z, "white_wool");
            c.set(mid + 3, 6, z, "white_wool");
        }
        for (int[] p : new int[][]{{5, 5}, {17, 5}, {5, 17}, {17, 17}, {11, 5}, {11, 17}, {5, 11}, {17, 11}}) {
            c.column(p[0], p[1], 1, 5, "dark_oak_log[axis=y]");
        }
        // Torii gates over the water at both ends.
        for (int z : new int[]{2, 20}) {
            c.column(mid - 3, z, 1, 9, "red_concrete");
            c.column(mid + 3, z, 1, 9, "red_concrete");
            c.fill(mid - 4, 8, z, mid + 4, 8, z, "red_concrete");
            c.fill(mid - 5, 10, z, mid + 5, 10, z, "dark_oak_planks");
            c.set(mid - 6, 10, z, "dark_oak_slab[type=bottom]");
            c.set(mid + 6, 10, z, "dark_oak_slab[type=bottom]");
            c.set(mid, 9, z, "dark_oak_planks");
            c.set(mid - 1, 7, z, "lantern[hanging=true]");
            c.set(mid + 1, 7, z, "lantern[hanging=true]");
        }
        for (int[] p : new int[][]{{0, 0}, {22, 0}, {0, 22}, {22, 22}}) {
            c.lanternPost(p[0], 4, p[1], "stone_brick_wall", 2, "lantern");
        }
        return new GeneratedArena.Builder("sumo_dojo", c.b)
                .display("<red>Sumo Dojo", "RED_WOOL")
                .tags("sumo")
                .spawns(spawn(mid - 3, 7, mid, EAST), spawn(mid + 3, 7, mid, WEST))
                .spectator(view(11.5, 12, 1.5, SOUTH))
                .limits(9, 5)
                .build();
    }

    /** Round stone lotus (radius 8) on a stem in a mossy pond with lily pads. */
    static GeneratedArena lotus() {
        Canvas c = new Canvas(25, 10, 25, 6002);
        double cx = 12.5;
        c.disc(cx, 0, cx, 12.5, "mud");
        c.ring(cx, 1, cx, 11.5, 12.5, "moss_block");
        c.ring(cx, 2, cx, 11.5, 12.5, c.pick("mossy_cobblestone", "moss_block"));
        c.disc(cx, 1, cx, 11.5, "water");
        c.disc(cx, 2, cx, 11.5, "water");
        for (int y = 1; y <= 4; y++) {
            c.disc(cx, y, cx, 2, "stone_bricks");
        }
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                double d = dist(x, z, cx, cx);
                if (d > 9.5 && d <= 11.2 && c.rnd.nextDouble() < 0.18) {
                    c.set(x, 3, z, "lily_pad");
                }
                if (d > 8) {
                    continue;
                }
                String block;
                if (d > 7) {
                    block = "white_concrete";
                } else if (d > 4.5 && d <= 5.5) {
                    block = "light_gray_concrete";
                } else if (d <= 1.3) {
                    block = "pink_terracotta";
                } else if (d <= 2.3) {
                    block = "white_terracotta";
                } else {
                    block = "smooth_stone";
                }
                c.set(x, 5, z, block);
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            int px = (int) Math.floor(cx + Math.cos(a) * 12);
            int pz = (int) Math.floor(cx + Math.sin(a) * 12);
            c.lanternPost(px, 3, pz, "stone_brick_wall", 2, "lantern");
        }
        return new GeneratedArena.Builder("sumo_lotus", c.b)
                .display("<light_purple>Lotus Pond", "LILY_PAD")
                .tags("sumo")
                .spawns(spawn(9, 6, 12, EAST), spawn(15, 6, 12, WEST))
                .spectator(view(12.5, 10, 1.5, SOUTH))
                .limits(9, 4)
                .build();
    }

    /** Large floating ring (radius 9) over the void with a rocky underside. */
    static GeneratedArena skyRing() {
        Canvas c = new Canvas(21, 9, 21, 6003);
        double cx = 10.5;
        c.underside(cx, 4, cx, 9.5, 5, "stone", "andesite", "cobblestone", "tuff");
        for (int x = 0; x < 21; x++) {
            for (int z = 0; z < 21; z++) {
                double d = dist(x, z, cx, cx);
                if (d > 9.5) {
                    continue;
                }
                String block;
                if (d > 8.5) {
                    block = "orange_terracotta";
                } else if (d > 7.5) {
                    block = "white_terracotta";
                } else if (x == 10 || z == 10) {
                    block = "polished_diorite";
                } else {
                    block = "polished_andesite";
                }
                c.set(x, 5, z, block);
            }
        }
        return new GeneratedArena.Builder("sumo_skyring", c.b)
                .display("<gold>Sky Ring", "ORANGE_TERRACOTTA")
                .tags("sumo")
                .spawns(spawn(7, 6, 10, EAST), spawn(13, 6, 10, WEST))
                .spectator(view(10.5, 11, 0.5, SOUTH))
                .limits(9, 3)
                .build();
    }

    /** Small mossy islet (radius 6.5) on a stalk in a shallow sea: little room to recover. */
    static GeneratedArena islet() {
        Canvas c = new Canvas(19, 8, 19, 6004);
        double cx = 9.5;
        c.fill(0, 0, 0, 18, 0, 18, "sandstone");
        c.perimeter(0, 0, 18, 18, 1, 2, "prismarine");
        c.fill(1, 1, 1, 17, 2, 17, "water");
        for (int y = 1; y <= 3; y++) {
            c.disc(cx, y, cx, 1.8, "mossy_stone_bricks");
        }
        for (int x = 0; x < 19; x++) {
            for (int z = 0; z < 19; z++) {
                double d = dist(x, z, cx, cx);
                if (d <= 6.5) {
                    c.set(x, 4, z, d > 5.6 ? "mossy_cobblestone" : c.mix("mossy_stone_bricks", "moss_block", 0.3));
                } else if (d > 7.5 && d < 8.8 && c.rnd.nextDouble() < 0.2) {
                    c.set(x, 3, z, "lily_pad");
                }
            }
        }
        for (int[] p : new int[][]{{0, 0}, {18, 0}, {0, 18}, {18, 18}}) {
            c.set(p[0], 3, p[1], "sea_lantern");
        }
        return new GeneratedArena.Builder("sumo_islet", c.b)
                .display("<dark_green>Islet", "MOSS_BLOCK")
                .tags("sumo")
                .spawns(spawn(7, 5, 9, EAST), spawn(11, 5, 9, WEST))
                .spectator(view(9.5, 9, 0.5, SOUTH))
                .limits(8, 3)
                .build();
    }
}
