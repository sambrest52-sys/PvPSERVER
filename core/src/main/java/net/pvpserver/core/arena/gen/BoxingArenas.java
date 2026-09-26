package net.pvpserver.core.arena.gen;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.WEST;
import static net.pvpserver.core.arena.gen.Canvas.spawn;
import static net.pvpserver.core.arena.gen.Canvas.view;

/**
 * Boxing rings (tag {@code boxing}): a square ring with corner posts and ropes, barriers above the ropes so nobody
 * leaves the ring, and a different venue around each one.
 */
final class BoxingArenas {

    private BoxingArenas() {
    }

    /**
     * Ropes (iron chains) and corner posts around a square ring whose edge cells are {@code min} and {@code max};
     * barriers above the ropes up to the top of the template.
     */
    private static void ropes(Canvas c, int min, int max, int canvasY, int ropeRows, String[] posts, String postCap) {
        for (int y = canvasY + 1; y <= canvasY + ropeRows; y++) {
            for (int i = min + 1; i < max; i++) {
                c.set(i, y, min, "iron_chain[axis=x]");
                c.set(i, y, max, "iron_chain[axis=x]");
                c.set(min, y, i, "iron_chain[axis=z]");
                c.set(max, y, i, "iron_chain[axis=z]");
            }
        }
        int[][] corners = {{min, min}, {max, max}, {min, max}, {max, min}};
        for (int i = 0; i < 4; i++) {
            c.column(corners[i][0], corners[i][1], canvasY, canvasY + ropeRows + 1, posts[i]);
            c.set(corners[i][0], canvasY + ropeRows + 2, corners[i][1], postCap);
        }
        for (int y = canvasY + ropeRows + 1; y < c.sy(); y++) {
            for (int i = min; i <= max; i++) {
                c.setIfAir(i, y, min, "barrier");
                c.setIfAir(i, y, max, "barrier");
                c.setIfAir(min, y, i, "barrier");
                c.setIfAir(max, y, i, "barrier");
            }
        }
    }

    /** Championship ring: white canvas with a red/blue centre logo, bleachers and a lighting rig. */
    static GeneratedArena championship() {
        Canvas c = new Canvas(29, 16, 29, 7001);
        int mid = 14;
        int min = 6;
        int max = 22;
        for (int x = 0; x < 29; x++) {
            for (int z = 0; z < 29; z++) {
                c.set(x, 0, z, (x + z) % 2 == 0 ? "polished_blackstone" : "blackstone");
            }
        }
        c.fill(min, 1, min, max, 1, max, "black_concrete");
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                int dx = x - mid;
                int dz = z - mid;
                String block;
                if (x == min || x == max || z == min || z == max) {
                    block = "gray_concrete";
                } else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                    block = dx == 0 && dz == 0 ? "white_concrete" : dx + dz < 0 ? "red_concrete" : dx + dz > 0 ? "blue_concrete" : "white_concrete";
                } else {
                    block = "white_concrete";
                }
                c.set(x, 2, z, block);
            }
        }
        ropes(c, min, max, 2, 3, new String[]{"red_concrete", "blue_concrete", "white_concrete", "white_concrete"}, "sea_lantern");
        // Bleachers on the east and west sides.
        for (int tier = 0; tier < 3; tier++) {
            int y = 1 + tier;
            for (int z = 4; z <= 24; z++) {
                c.fill(3 - tier, 1, z, 3 - tier, y - 1, z, "dark_oak_planks");
                c.set(3 - tier, y, z, "dark_oak_stairs[facing=west]");
                c.fill(25 + tier, 1, z, 25 + tier, y - 1, z, "dark_oak_planks");
                c.set(25 + tier, y, z, "dark_oak_stairs[facing=east]");
            }
        }
        // Lighting rig above the ring.
        c.fill(min, 13, mid, max, 13, mid, "dark_oak_planks");
        c.fill(mid, 13, min, mid, 13, max, "dark_oak_planks");
        for (int[] p : new int[][]{{mid - 5, mid}, {mid + 5, mid}, {mid, mid - 5}, {mid, mid + 5}}) {
            c.set(p[0], 12, p[1], "lantern[hanging=true]");
        }
        c.set(mid, 12, mid, "sea_lantern");
        c.perimeter(0, 0, 28, 28, 1, 5, "polished_blackstone_bricks");
        for (int i = 2; i < 28; i += 5) {
            c.set(i, 4, 0, "glowstone");
            c.set(i, 4, 28, "glowstone");
            c.set(0, 4, i, "glowstone");
            c.set(28, 4, i, "glowstone");
        }
        return new GeneratedArena.Builder("boxing_ring", c.b)
                .display("<red>Championship Ring", "RED_CONCRETE")
                .tags("boxing")
                .spawns(spawn(mid - 4, 3, mid, EAST), spawn(mid + 4, 3, mid, WEST))
                .spectator(view(14.5, 10, 2.5, SOUTH))
                .limits(8, -2)
                .build();
    }

    /** Old boxing gym: plank floor, fence ropes, brick walls with windows, punching bags and benches. */
    static GeneratedArena gym() {
        Canvas c = new Canvas(25, 13, 25, 7002);
        int mid = 12;
        int min = 5;
        int max = 19;
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                c.set(x, 0, z, x % 3 == 0 ? "oak_planks" : "spruce_planks");
            }
        }
        for (int x = min + 1; x < max; x++) {
            for (int z = min + 1; z < max; z++) {
                c.set(x, 0, z, Math.abs(x - mid) <= 1 && Math.abs(z - mid) <= 1 ? "red_wool" : "light_gray_wool");
            }
        }
        for (int i = min; i <= max; i++) {
            c.set(i, 1, min, "spruce_fence");
            c.set(i, 1, max, "spruce_fence");
            c.set(min, 1, i, "spruce_fence");
            c.set(max, 1, i, "spruce_fence");
        }
        for (int[] p : new int[][]{{min, min}, {max, min}, {min, max}, {max, max}}) {
            c.column(p[0], p[1], 1, 3, "spruce_log[axis=y]");
            c.set(p[0], 4, p[1], "lantern[hanging=false]");
        }
        for (int y = 2; y < c.sy(); y++) {
            for (int i = min; i <= max; i++) {
                c.setIfAir(i, y, min, "barrier");
                c.setIfAir(i, y, max, "barrier");
                c.setIfAir(min, y, i, "barrier");
                c.setIfAir(max, y, i, "barrier");
            }
        }
        // Brick walls with log pillars and windows.
        for (int i = 0; i < 25; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, 24}, {0, i}, {24, i}}) {
                boolean pillar = i % 6 == 0;
                for (int y = 1; y <= 8; y++) {
                    boolean window = !pillar && (y == 4 || y == 5) && i % 6 >= 2 && i % 6 <= 4;
                    c.set(p[0], y, p[1], pillar ? "stripped_spruce_log[axis=y]" : window ? "glass_pane" : "bricks");
                }
            }
        }
        // Ceiling beams with hanging lanterns and punching bags.
        for (int z = 6; z <= 18; z += 6) {
            c.fill(1, 9, z, 23, 9, z, "spruce_log[axis=x]");
            for (int x = 3; x <= 21; x += 6) {
                c.set(x, 8, z, "lantern[hanging=true]");
            }
        }
        for (int[] p : new int[][]{{2, 6}, {22, 6}, {2, 18}, {22, 18}}) {
            c.column(p[0], p[1], 6, 8, "iron_chain[axis=y]");
            c.column(p[0], p[1], 3, 5, "red_wool");
        }
        for (int x = 7; x <= 17; x++) {
            c.set(x, 1, 2, "spruce_stairs[facing=north]");
            c.set(x, 1, 22, "spruce_stairs[facing=south]");
        }
        for (int[] p : new int[][]{{2, 2}, {22, 2}, {2, 22}, {22, 22}, {3, 2}, {21, 22}}) {
            c.set(p[0], 1, p[1], "barrel[facing=up]");
        }
        return new GeneratedArena.Builder("boxing_gym", c.b)
                .display("<gold>Old Gym", "HAY_BLOCK")
                .tags("boxing")
                .spawns(spawn(mid - 4, 1, mid, EAST), spawn(mid + 4, 1, mid, WEST))
                .spectator(view(12.5, 8, 3.5, SOUTH))
                .limits(8, -3)
                .build();
    }

    /** Rooftop ring: black canvas with yellow lines, glass railings, AC units, antennas and neon. */
    static GeneratedArena rooftop() {
        Canvas c = new Canvas(25, 16, 25, 7003);
        int mid = 12;
        int min = 6;
        int max = 18;
        c.fill(0, 0, 0, 24, 0, 24, "gray_concrete");
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                c.set(x, 1, z, (x / 4 + z / 4) % 2 == 0 ? "light_gray_concrete" : "smooth_stone");
            }
        }
        c.fill(min, 1, min, max, 1, max, "magenta_stained_glass");
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                boolean edge = x == min || x == max || z == min || z == max;
                boolean line = x == min + 1 || x == max - 1 || z == min + 1 || z == max - 1;
                c.set(x, 2, z, edge ? "yellow_concrete" : line ? "yellow_concrete" : "black_concrete");
            }
        }
        ropes(c, min, max, 2, 3, new String[]{"iron_block", "iron_block", "iron_block", "iron_block"}, "end_rod[facing=up]");
        c.perimeter(0, 0, 24, 24, 2, 2, "gray_stained_glass_pane");
        for (int i = 0; i <= 24; i += 6) {
            for (int[] p : new int[][]{{i, 0}, {i, 24}, {0, i}, {24, i}}) {
                c.set(p[0], 2, p[1], "polished_andesite");
                c.set(p[0], 3, p[1], "sea_lantern");
            }
        }
        for (int[] p : new int[][]{{2, 2}, {20, 3}, {3, 20}}) {
            c.fill(p[0], 2, p[1], p[0] + 1, 3, p[1] + 1, "smooth_stone");
            c.fill(p[0], 4, p[1], p[0] + 1, 4, p[1] + 1, "smooth_stone_slab[type=bottom]");
            c.set(p[0], 3, p[1], "iron_trapdoor[facing=north,half=top,open=false]");
        }
        c.column(21, 21, 2, 8, "iron_bars");
        c.set(21, 9, 21, "lightning_rod");
        c.column(3, 13, 2, 5, "iron_bars");
        c.set(3, 6, 13, "lightning_rod");
        return new GeneratedArena.Builder("boxing_rooftop", c.b)
                .display("<light_purple>Rooftop", "MAGENTA_STAINED_GLASS")
                .tags("boxing")
                .spawns(spawn(mid - 3, 3, mid, EAST), spawn(mid + 3, 3, mid, WEST))
                .spectator(view(12.5, 10, 2.5, SOUTH))
                .limits(9, -2)
                .build();
    }
}
