package net.pvpserver.core.arena.gen;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.WEST;
import static net.pvpserver.core.arena.gen.Canvas.dist;
import static net.pvpserver.core.arena.gen.Canvas.spawn;
import static net.pvpserver.core.arena.gen.Canvas.view;

/**
 * Open fighting arenas for the potion, gapple, combo, classic, soup and archer kits (tag {@code standard}):
 * cover, pillars, height changes and walls, each in its own theme.
 */
final class StandardArenas {

    private StandardArenas() {
    }

    /** Sandstone colosseum: tiered seating, a stepped central dais and a ring of (partly broken) pillars. */
    static GeneratedArena colosseum() {
        Canvas c = new Canvas(61, 22, 61, 1001);
        double cx = 30.5;
        double cz = 30.5;
        c.disc(cx, 0, cz, 29.5, "sandstone");
        // Arena floor with a medallion, a ring and eight spokes.
        for (int x = 0; x < c.sx(); x++) {
            for (int z = 0; z < c.sz(); z++) {
                double d = dist(x, z, cx, cz);
                if (d > 22) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(z + 0.5 - cz, x + 0.5 - cx)) + 360;
                double off = Math.abs(((angle + 22.5) % 45) - 22.5);
                String block;
                if (d <= 2.5) {
                    block = "chiseled_sandstone";
                } else if (d <= 3.5 || (d > 10.5 && d <= 11.5)) {
                    block = "cut_sandstone";
                } else if (off < 3.2 * (6 / Math.max(d, 6))) {
                    block = "sandstone";
                } else {
                    block = c.mix("smooth_sandstone", "sandstone", 0.07);
                }
                c.set(x, 1, z, block);
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 + 15);
            c.set((int) Math.floor(cx + Math.cos(a) * 16), 1, (int) Math.floor(cz + Math.sin(a) * 16), "glowstone");
        }
        // Stepped dais in the middle.
        c.disc(cx, 2, cz, 5, "smooth_sandstone");
        stairRing(c, cx, cz, 5, 6, 2, "smooth_sandstone_stairs", true);
        c.disc(cx, 3, cz, 2, "chiseled_sandstone");
        stairRing(c, cx, cz, 2, 3, 3, "smooth_sandstone_stairs", true);
        c.set(30, 3, 30, "sea_lantern");
        // Eight pillars between the spokes; some are broken for uneven cover.
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            int px = (int) Math.floor(cx + Math.cos(a) * 15);
            int pz = (int) Math.floor(cz + Math.sin(a) * 15);
            boolean broken = i % 3 == 1;
            int height = broken ? 3 + c.rnd.nextInt(2) : 6;
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    c.set(px + dx, 2, pz + dz, "smooth_sandstone");
                    c.column(px + dx, pz + dz, 3, 1 + height, "cut_sandstone");
                    c.set(px + dx, 2 + height, pz + dz, broken ? "smooth_sandstone_slab[type=bottom]" : "chiseled_sandstone");
                }
            }
            if (!broken) {
                c.set(px, 3 + height, pz, "lantern[hanging=false]");
            }
        }
        // Low curved cover walls north and south of the dais (the spawn line runs east-west).
        for (int x = 0; x < c.sx(); x++) {
            for (int z = 0; z < c.sz(); z++) {
                double d = dist(x, z, cx, cz);
                double angle = Math.toDegrees(Math.atan2(z + 0.5 - cz, x + 0.5 - cx));
                boolean north = Math.abs(angle + 90) < 11;
                boolean south = Math.abs(angle - 90) < 11;
                if (d > 18.5 && d <= 19.5 && (north || south)) {
                    c.set(x, 2, z, "cut_sandstone");
                    c.set(x, 3, z, "smooth_sandstone_slab[type=bottom]");
                }
            }
        }
        // Arena wall with a chiseled band, then seating tiers rising outwards.
        for (int y = 1; y <= 8; y++) {
            c.ring(cx, y, cz, 22, 24, y == 5 ? "chiseled_sandstone" : "cut_sandstone");
        }
        c.ring(cx, 9, cz, 22, 24, "smooth_sandstone_slab[type=bottom]");
        for (int tier = 0; tier < 4; tier++) {
            double rIn = 24 + tier;
            for (int y = 1; y <= 8 + tier; y++) {
                c.ring(cx, y, cz, rIn, rIn + 1, "sandstone");
            }
            stairRing(c, cx, cz, rIn, rIn + 1, 9 + tier, "sandstone_stairs", false);
        }
        for (int y = 1; y <= 14; y++) {
            c.ring(cx, y, cz, 28, 29.5, y >= 13 ? "cut_sandstone" : "sandstone");
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15);
            c.set((int) Math.floor(cx + Math.cos(a) * 28.8), 15, (int) Math.floor(cz + Math.sin(a) * 28.8), "lantern[hanging=false]");
        }
        c.barrierRing(cx, cz, 23, 10, c.sy() - 1);
        return new GeneratedArena.Builder("colosseum", c.b)
                .display("<gold>Colosseum", "CHISELED_SANDSTONE")
                .tags("standard")
                .spawns(spawn(13, 2, 30, EAST), spawn(47, 2, 30, WEST))
                .spectator(view(30.5, 13, 12.5, SOUTH))
                .limits(14, -4)
                .build();
    }

    /** Overgrown ruins: gentle hills, broken walls and pillars, a fallen arch, bushes and trees. */
    static GeneratedArena mossyRuins() {
        Canvas c = new Canvas(57, 22, 57, 2002);
        int size = 57;
        int mid = 28;
        int[][] spawns = {{7, mid}, {49, mid}};
        int[][] height = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double n = c.noise.fractal(x, z, 16, 3);
                int h = 4 + (int) Math.round(n * 3) - 1;
                double nearSpawn = Math.min(dist(x, z, spawns[0][0] + 0.5, mid + 0.5), dist(x, z, spawns[1][0] + 0.5, mid + 0.5));
                if (nearSpawn < 5) {
                    h = 4;
                } else if (nearSpawn < 8) {
                    h = Math.min(Math.max(h, 4), 5);
                }
                height[x][z] = h;
                c.set(x, 0, z, "stone");
                for (int y = 1; y < h - 1; y++) {
                    c.set(x, y, z, c.mix("dirt", "stone", 0.25));
                }
                boolean path = Math.abs(x - mid) <= 1 || Math.abs(z - mid) <= 1;
                String top;
                if (path) {
                    top = c.pick("cobblestone", "mossy_cobblestone", "mossy_cobblestone", "gravel");
                } else {
                    double r = c.rnd.nextDouble();
                    top = r < 0.62 ? "grass_block" : r < 0.82 ? "moss_block" : r < 0.93 ? "coarse_dirt" : "rooted_dirt";
                }
                c.set(x, h - 1, z, top);
            }
        }
        for (int[] s : spawns) {
            for (int x = s[0] - 1; x <= s[0] + 1; x++) {
                for (int z = s[1] - 1; z <= s[1] + 1; z++) {
                    c.set(x, 3, z, x == s[0] && z == s[1] ? "chiseled_stone_bricks" : "stone_bricks");
                }
            }
        }
        // Border wall with a ruined, uneven top.
        String[] bricks = {"stone_bricks", "mossy_stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"};
        for (int i = 0; i < size; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, size - 1}, {0, i}, {size - 1, i}}) {
                int top = 9 + c.rnd.nextInt(3);
                for (int y = 0; y <= top; y++) {
                    c.set(p[0], y, p[1], c.pick(bricks));
                }
                c.set(p[0], top + 1, p[1], "mossy_stone_brick_wall");
            }
        }
        // Ruined wall segments and pillars scattered away from the spawns.
        for (int i = 0; i < 7; i++) {
            int x0 = 8 + c.rnd.nextInt(size - 16);
            int z0 = 8 + c.rnd.nextInt(size - 16);
            boolean alongX = c.rnd.nextBoolean();
            int length = 4 + c.rnd.nextInt(5);
            for (int k = 0; k < length; k++) {
                int x = alongX ? x0 + k : x0;
                int z = alongX ? z0 : z0 + k;
                if (farFrom(x, z, spawns, 7) && Math.abs(x - mid) > 1 && Math.abs(z - mid) > 1) {
                    int base = height[Math.min(x, size - 1)][Math.min(z, size - 1)];
                    int top = base + 1 + c.rnd.nextInt(3);
                    for (int y = base; y <= top; y++) {
                        if (!(y == base + 1 && c.rnd.nextDouble() < 0.15)) {
                            c.set(x, y, z, c.pick(bricks));
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            int x = 6 + c.rnd.nextInt(size - 12);
            int z = 6 + c.rnd.nextInt(size - 12);
            if (!farFrom(x, z, spawns, 6) || Math.abs(x - mid) <= 1 || Math.abs(z - mid) <= 1) {
                continue;
            }
            int base = height[x][z];
            int top = base + 1 + c.rnd.nextInt(5);
            c.column(x, z, base, top, c.pick("stone_bricks", "mossy_stone_bricks"));
            c.set(x, top + 1, z, c.mix("chiseled_stone_bricks", "mossy_stone_brick_slab[type=bottom]", 0.5));
        }
        // Fallen arch over the central crossing.
        int base = height[mid][mid];
        for (int side : new int[]{-4, 4}) {
            c.set(mid - 1, base - 1, mid + side, "stone_bricks");
            c.column(mid - 1, mid + side, base, base + 4, "mossy_stone_bricks");
            c.column(mid + 1, mid + side, base, base + 4, "stone_bricks");
        }
        for (int z = mid - 4; z <= mid + 4; z++) {
            if (z != mid + 1 && z != mid + 2) {
                c.set(mid - 1, base + 5, z, c.pick(bricks));
                c.set(mid + 1, base + 5, z, c.pick(bricks));
            }
        }
        c.set(mid + 2, base, mid + 2, "cracked_stone_bricks");
        c.set(mid + 3, base, mid + 1, "mossy_stone_brick_stairs[facing=east]");
        // Trees, bushes, plants and lantern posts.
        for (int[] t : new int[][]{{16, 12}, {41, 44}, {15, 45}, {43, 11}}) {
            c.broadTree(t[0], height[t[0]][t[1]], t[1], 4 + c.rnd.nextInt(2), "oak_log", "oak_leaves");
        }
        for (int i = 0; i < 10; i++) {
            int x = 5 + c.rnd.nextInt(size - 10);
            int z = 5 + c.rnd.nextInt(size - 10);
            if (farFrom(x, z, spawns, 5)) {
                c.ball(x + 0.5, height[x][z] + 0.3, z + 0.5, 1.3, true, "oak_leaves[persistent=true]", "azalea_leaves[persistent=true]");
            }
        }
        for (int x = 1; x < size - 1; x++) {
            for (int z = 1; z < size - 1; z++) {
                if (!farFrom(x, z, spawns, 3)) {
                    continue;
                }
                int y = height[x][z];
                double r = c.rnd.nextDouble();
                if (r < 0.12) {
                    c.plant(x, y, z, "short_grass", "grass_block");
                } else if (r < 0.15) {
                    c.plant(x, y, z, "fern", "grass_block");
                } else if (r < 0.17) {
                    c.plant(x, y, z, c.pick("poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower"), "grass_block");
                } else if (r < 0.30) {
                    c.plant(x, y, z, "moss_carpet", "moss_block");
                }
            }
        }
        for (int[] s : spawns) {
            c.lanternPost(s[0], 4, s[1] - 3, "mossy_cobblestone_wall", 2, "lantern");
            c.lanternPost(s[0], 4, s[1] + 3, "mossy_cobblestone_wall", 2, "lantern");
        }
        fillBarrierAbove(c, 1);
        return new GeneratedArena.Builder("mossy_ruins", c.b)
                .display("<green>Mossy Ruins", "MOSSY_STONE_BRICKS")
                .tags("standard")
                .spawns(spawn(7, 4, mid, EAST), spawn(49, 4, mid, WEST))
                .spectator(view(28.5, 14, 10.5, SOUTH))
                .limits(16, -3)
                .build();
    }

    /** Snowfield around a frozen pond with ice spikes, spruce trees and a log fence. */
    static GeneratedArena frozenLake() {
        Canvas c = new Canvas(55, 22, 55, 3003);
        int size = 55;
        double mid = 27.5;
        int[][] spawns = {{7, 27}, {47, 27}};
        int[][] height = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double d = dist(x, z, mid, mid);
                double n = c.noise.fractal(x, z, 14, 3);
                int h = 4 + (int) Math.round(n * 2.2) - 1;
                boolean pond = d < 7.5 + c.noise.value(x / 4.0, z / 4.0) * 2;
                if (pond) {
                    h = 3;
                } else if (d < 11) {
                    h = Math.min(h, 4);
                }
                if (!farFrom(x, z, spawns, 5)) {
                    h = 4;
                }
                height[x][z] = h;
                c.set(x, 0, z, "stone");
                for (int y = 1; y < h - 1; y++) {
                    c.set(x, y, z, c.mix("snow_block", "packed_ice", 0.1));
                }
                c.set(x, h - 1, z, pond ? c.mix("packed_ice", "ice", 0.15) : "snow_block");
                if (!pond && farFrom(x, z, spawns, 4) && c.rnd.nextDouble() < 0.1) {
                    c.set(x, h, z, "snow[layers=1]");
                }
            }
        }
        for (int[] s : spawns) {
            for (int x = s[0] - 1; x <= s[0] + 1; x++) {
                for (int z = s[1] - 1; z <= s[1] + 1; z++) {
                    c.set(x, 3, z, "spruce_planks");
                    c.set(x, 4, z, "air");
                }
            }
        }
        // Ice spikes and packed-ice boulders for cover.
        int[][] spikes = {{18, 14}, {37, 16}, {15, 38}, {39, 40}, {27, 9}, {28, 46}};
        for (int[] s : spikes) {
            int h = 5 + c.rnd.nextInt(5);
            int base = height[s[0]][s[1]];
            for (int y = 0; y < h; y++) {
                double r = 1.6 * (1 - (double) y / (h + 1)) + 0.3;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.sqrt(dx * dx + dz * dz) <= r) {
                            c.set(s[0] + dx, base + y, s[1] + dz, "packed_ice");
                        }
                    }
                }
            }
        }
        for (int[] t : new int[][]{{10, 10}, {45, 9}, {9, 45}, {46, 46}, {22, 44}, {33, 10}, {12, 20}, {43, 34}}) {
            c.conifer(t[0], height[t[0]][t[1]], t[1], 6 + c.rnd.nextInt(3), "spruce_log", "spruce_leaves");
        }
        for (int[] b : new int[][]{{20, 24}, {35, 31}}) {
            c.ball(b[0] + 0.5, height[b[0]][b[1]], b[1] + 0.5, 1.6, true, "packed_ice", "snow_block");
        }
        // Log posts with fence panels and lanterns around the edge.
        for (int i = 0; i < size; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, size - 1}, {0, i}, {size - 1, i}}) {
                int top = height[p[0]][p[1]] + 2;
                boolean post = i % 4 == 0;
                c.column(p[0], p[1], 0, top, post ? "spruce_log[axis=y]" : "spruce_planks");
                if (post) {
                    c.set(p[0], top + 1, p[1], "spruce_log[axis=y]");
                    c.set(p[0], top + 2, p[1], "lantern[hanging=false]");
                } else {
                    c.set(p[0], top + 1, p[1], "spruce_fence");
                }
            }
        }
        for (int[] s : spawns) {
            c.lanternPost(s[0] + (s[0] < 27 ? -2 : 2), 4, s[1] + 2, "spruce_fence", 2, "lantern");
        }
        fillBarrierAbove(c, 1);
        return new GeneratedArena.Builder("frozen_lake", c.b)
                .display("<aqua>Frozen Lake", "PACKED_ICE")
                .tags("standard")
                .spawns(spawn(7, 4, 27, EAST), spawn(47, 4, 27, WEST))
                .spectator(view(27.5, 14, 9.5, SOUTH))
                .limits(16, -3)
                .build();
    }

    /** Nether keep: raised walkways with ramps, basalt pillars, corner towers and a lava moat outside the walls. */
    static GeneratedArena netherKeep() {
        Canvas c = new Canvas(55, 24, 55, 4004);
        int size = 55;
        int mid = 27;
        int[][] spawns = {{9, mid}, {45, mid}};
        c.fill(0, 0, 0, size - 1, 1, size - 1, "blackstone");
        for (int x = 3; x <= size - 4; x++) {
            for (int z = 3; z <= size - 4; z++) {
                String block;
                double n = c.noise.fractal(x, z, 9, 2);
                if (Math.abs(x - mid) <= 1 || Math.abs(z - mid) <= 1) {
                    block = "polished_blackstone_bricks";
                } else if ((x - 3) % 6 == 0 || (z - 3) % 6 == 0) {
                    block = "red_nether_bricks";
                } else if (n > 0.7 && farFrom(x, z, spawns, 5)) {
                    block = x < mid ? "crimson_nylium" : "warped_nylium";
                } else {
                    block = c.mix("nether_bricks", "cracked_nether_bricks", 0.08);
                }
                c.set(x, 2, z, block);
                if (((x - 3) % 12 == 0) && ((z - 3) % 12 == 0)) {
                    c.set(x, 2, z, "shroomlight");
                }
            }
        }
        // Lava moat between the rim and the wall; contained by solid blocks on every side.
        for (int i = 1; i < size - 1; i++) {
            for (int[] p : new int[][]{{i, 1}, {i, size - 2}, {1, i}, {size - 2, i}}) {
                c.set(p[0], 1, p[1], "lava");
            }
        }
        for (int i = 0; i < size; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, size - 1}, {0, i}, {size - 1, i}}) {
                c.column(p[0], p[1], 1, 2, "blackstone");
            }
        }
        // Keep wall with a red band and fence crenellations.
        for (int y = 2; y <= 8; y++) {
            c.perimeter(2, 2, size - 3, size - 3, y, y, y == 6 ? "red_nether_bricks" : "nether_bricks");
        }
        for (int i = 2; i <= size - 3; i += 2) {
            for (int[] p : new int[][]{{i, 2}, {i, size - 3}, {2, i}, {size - 3, i}}) {
                c.set(p[0], 9, p[1], "nether_brick_fence");
            }
        }
        // Raised walkways along the north and south walls with stair ramps at both ends.
        // Walkway surface is at y 6 (blocks 3-5); each ramp climbs it in three half-block stair steps.
        int west = 12;
        int east = size - 13;
        for (int[] band : new int[][]{{4, 8, 8}, {46, 50, 46}}) {
            for (int x = west; x <= east; x++) {
                for (int z = band[0]; z <= band[1]; z++) {
                    c.column(x, z, 3, 5, "polished_blackstone_bricks");
                }
                if (x % 8 != 1) {
                    c.set(x, 6, band[2], "nether_brick_fence");
                } else {
                    c.set(x, 6, band[2], "soul_lantern[hanging=false]");
                }
            }
            for (int z = band[0]; z <= band[1]; z++) {
                c.set(west - 3, 3, z, "nether_brick_stairs[facing=east]");
                c.set(west - 2, 3, z, "polished_blackstone_bricks");
                c.set(west - 2, 4, z, "nether_brick_stairs[facing=east]");
                c.column(west - 1, z, 3, 4, "polished_blackstone_bricks");
                c.set(west - 1, 5, z, "nether_brick_stairs[facing=east]");
                c.set(east + 3, 3, z, "nether_brick_stairs[facing=west]");
                c.set(east + 2, 3, z, "polished_blackstone_bricks");
                c.set(east + 2, 4, z, "nether_brick_stairs[facing=west]");
                c.column(east + 1, z, 3, 4, "polished_blackstone_bricks");
                c.set(east + 1, 5, z, "nether_brick_stairs[facing=west]");
            }
        }
        // Corner towers.
        for (int[] t : new int[][]{{3, 3}, {size - 8, 3}, {3, size - 8}, {size - 8, size - 8}}) {
            for (int x = t[0]; x < t[0] + 5; x++) {
                for (int z = t[1]; z < t[1] + 5; z++) {
                    boolean edge = x == t[0] || x == t[0] + 4 || z == t[1] || z == t[1] + 4;
                    c.column(x, z, 3, 10, "polished_blackstone_bricks");
                    c.set(x, 9, z, edge ? "crying_obsidian" : "polished_blackstone_bricks");
                    c.set(x, 11, z, edge && (x + z) % 2 == 0 ? "polished_blackstone_brick_wall" : "air");
                    if (edge && (x == t[0] + 2 || z == t[1] + 2)) {
                        c.set(x, 6, z, "gilded_blackstone");
                    }
                }
            }
            c.set(t[0] + 2, 11, t[1] + 2, "shroomlight");
        }
        // Basalt pillars and a raised central platform.
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + i * 60);
            int px = (int) Math.floor(mid + 0.5 + Math.cos(a) * 11);
            int pz = (int) Math.floor(mid + 0.5 + Math.sin(a) * 11);
            int h = 4 + c.rnd.nextInt(4);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    c.column(px + dx, pz + dz, 3, 2 + h - (dx + dz == 2 ? 1 : 0), dx == dz ? "polished_basalt[axis=y]" : "basalt[axis=y]");
                }
            }
            c.set(px, 3 + h, pz, "soul_lantern[hanging=false]");
        }
        for (int x = mid - 3; x <= mid + 3; x++) {
            for (int z = mid - 3; z <= mid + 3; z++) {
                boolean edge = Math.abs(x - mid) == 3 || Math.abs(z - mid) == 3;
                c.set(x, 3, z, edge ? "nether_brick_slab[type=bottom]" : "chiseled_nether_bricks");
            }
        }
        c.set(mid, 3, mid, "shroomlight");
        for (int x = 3; x <= size - 4; x++) {
            for (int z = 3; z <= size - 4; z++) {
                String ground = c.get(x, 2, z);
                if (c.isAir(x, 3, z) && ground.contains("nylium") && c.rnd.nextDouble() < 0.25) {
                    boolean crimson = ground.contains("crimson");
                    c.set(x, 3, z, c.rnd.nextDouble() < 0.3 ? (crimson ? "crimson_fungus" : "warped_fungus")
                            : (crimson ? "crimson_roots" : "warped_roots"));
                }
            }
        }
        for (int y = 9; y < c.sy(); y++) {
            for (int i = 2; i <= size - 3; i++) {
                c.setIfAir(i, y, 2, "barrier");
                c.setIfAir(i, y, size - 3, "barrier");
                c.setIfAir(2, y, i, "barrier");
                c.setIfAir(size - 3, y, i, "barrier");
            }
        }
        return new GeneratedArena.Builder("nether_keep", c.b)
                .display("<red>Nether Keep", "NETHER_BRICKS")
                .tags("standard")
                .spawns(spawn(spawns[0][0], 3, mid, EAST), spawn(spawns[1][0], 3, mid, WEST))
                .spectator(view(27.5, 15, 12.5, SOUTH))
                .limits(14, -3)
                .build();
    }

    /** Floating quartz temple: colonnade, raised balconies, a stepped altar and reflecting pools. */
    static GeneratedArena skyTemple() {
        Canvas c = new Canvas(53, 28, 53, 5005);
        int mid = 26;
        int lo = 4;
        int hi = 48;
        c.underside(mid + 0.5, 9, mid + 0.5, 26, 9, "stone", "andesite", "cobblestone", "tuff");
        for (int x = lo; x <= hi; x++) {
            for (int z = lo; z <= hi; z++) {
                int dx = Math.abs(x - mid);
                int dz = Math.abs(z - mid);
                String block;
                if (x == lo || x == hi || z == lo || z == hi) {
                    block = "chiseled_quartz_block";
                } else if ((x - lo) % 7 == 0 && (z - lo) % 7 == 0) {
                    block = "sea_lantern";
                } else if ((x - lo) % 7 == 0 || (z - lo) % 7 == 0) {
                    block = "quartz_bricks";
                } else if (dx + dz == 7) {
                    block = "dark_prismarine";
                } else if (dx + dz == 8) {
                    block = "prismarine_bricks";
                } else {
                    block = "smooth_quartz";
                }
                c.set(x, 10, z, block);
            }
        }
        // Four reflecting pools on the diagonals.
        for (int[] p : new int[][]{{-10, -10}, {10, -10}, {-10, 10}, {10, 10}}) {
            for (int x = mid + p[0] - 1; x <= mid + p[0] + 1; x++) {
                for (int z = mid + p[1] - 1; z <= mid + p[1] + 1; z++) {
                    c.set(x, 9, z, "dark_prismarine");
                    c.set(x, 10, z, "water");
                }
            }
        }
        // Stepped altar.
        c.disc(mid + 0.5, 11, mid + 0.5, 4, "quartz_block");
        stairRing(c, mid + 0.5, mid + 0.5, 4, 5, 11, "quartz_stairs", true);
        c.disc(mid + 0.5, 12, mid + 0.5, 2, "chiseled_quartz_block");
        stairRing(c, mid + 0.5, mid + 0.5, 2, 3, 12, "quartz_stairs", true);
        c.set(mid, 12, mid, "sea_lantern");
        c.set(mid, 13, mid, "end_rod[facing=up]");
        // Balconies on the north and south sides with central staircases.
        for (int side : new int[]{-1, 1}) {
            int zNear = side < 0 ? 9 : 43;
            int zFar = side < 0 ? 6 : 46;
            for (int x = mid - 6; x <= mid + 6; x++) {
                for (int z = Math.min(zNear, zFar); z <= Math.max(zNear, zFar); z++) {
                    c.column(x, z, 11, 13, "quartz_bricks");
                }
                if (Math.abs(x - mid) > 1) {
                    c.set(x, 14, zNear, "prismarine_wall");
                }
            }
            String facing = side < 0 ? "north" : "south";
            for (int x = mid - 1; x <= mid + 1; x++) {
                int step1 = zNear - side;
                int step2 = zNear - 2 * side;
                int step3 = zNear - 3 * side;
                c.column(x, step1, 11, 12, "quartz_bricks");
                c.set(x, step1, 13, "quartz_stairs[facing=" + facing + "]");
                c.set(x, step2, 11, "quartz_bricks");
                c.set(x, step2, 12, "quartz_stairs[facing=" + facing + "]");
                c.set(x, step3, 11, "quartz_stairs[facing=" + facing + "]");
            }
        }
        // Colonnade with a roof ring and hanging lanterns.
        for (int i = lo; i <= hi; i++) {
            for (int[] p : new int[][]{{i, lo}, {i, hi}, {lo, i}, {hi, i}}) {
                boolean pillar = (i - lo) % 4 == 0;
                if (pillar) {
                    c.column(p[0], p[1], 11, 16, "quartz_pillar[axis=y]");
                    c.set(p[0], 17, p[1], "chiseled_quartz_block");
                } else {
                    c.set(p[0], 11, p[1], "quartz_bricks");
                    if ((i - lo) % 4 == 2) {
                        c.set(p[0], 17, p[1], "lantern[hanging=true]");
                    }
                }
                c.set(p[0], 18, p[1], "smooth_quartz_slab[type=bottom]");
            }
        }
        for (int y = 12; y < c.sy(); y++) {
            for (int i = lo; i <= hi; i++) {
                for (int[] p : new int[][]{{i, lo}, {i, hi}, {lo, i}, {hi, i}}) {
                    c.setIfAir(p[0], y, p[1], "barrier");
                }
            }
        }
        return new GeneratedArena.Builder("sky_temple", c.b)
                .display("<white>Sky Temple", "QUARTZ_PILLAR")
                .tags("standard")
                .spawns(spawn(8, 11, mid, EAST), spawn(44, 11, mid, WEST))
                .spectator(view(26.5, 19, 14.5, SOUTH))
                .limits(22, 3)
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** Ring of stairs between two radii; {@code up} = the high side faces the centre (steps up inwards). */
    static void stairRing(Canvas c, double cx, double cz, double rInner, double rOuter, int y, String stairs, boolean up) {
        for (int x = (int) Math.floor(cx - rOuter - 1); x <= (int) Math.ceil(cx + rOuter + 1); x++) {
            for (int z = (int) Math.floor(cz - rOuter - 1); z <= (int) Math.ceil(cz + rOuter + 1); z++) {
                double d = dist(x, z, cx, cz);
                if (d > rInner && d <= rOuter) {
                    String facing = up ? Canvas.facingToward(x, z, cx, cz) : Canvas.facingAway(x, z, cx, cz);
                    c.set(x, y, z, stairs + "[facing=" + facing + "]");
                }
            }
        }
    }

    /** Whether (x, z) is at least {@code min} blocks from every point. */
    static boolean farFrom(int x, int z, int[][] points, double min) {
        for (int[] p : points) {
            if (dist(x, z, p[0] + 0.5, p[1] + 0.5) < min) {
                return false;
            }
        }
        return true;
    }

    /** Fills air above the outer edge columns with barrier so nobody can climb or pearl out. */
    static void fillBarrierAbove(Canvas c, int fromY) {
        for (int y = fromY; y < c.sy(); y++) {
            for (int i = 0; i < c.sx(); i++) {
                c.setIfAir(i, y, 0, "barrier");
                c.setIfAir(i, y, c.sz() - 1, "barrier");
            }
            for (int i = 0; i < c.sz(); i++) {
                c.setIfAir(0, y, i, "barrier");
                c.setIfAir(c.sx() - 1, y, i, "barrier");
            }
        }
    }
}
