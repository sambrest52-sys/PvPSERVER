package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.RelativeBox;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.WEST;
import static net.pvpserver.core.arena.gen.Canvas.dist;
import static net.pvpserver.core.arena.gen.Canvas.spawn;
import static net.pvpserver.core.arena.gen.Canvas.view;

/**
 * BuildUHC maps (tag {@code build}): 81x81 terrain with hills, trees, a pond and a lava pocket, plus flat spawn pads.
 * Terrain cannot be broken (the kit only breaks placed blocks); everything placed is rolled back after the match.
 */
final class BuildUhcArenas {

    private static final int SIZE = 81;
    private static final int SPAWN_Y = 9;
    private static final int BUILD_LIMIT = 34;
    private static final int[][] SPAWNS = {{10, 40}, {70, 40}};
    private static final double[] POND = {40.5, 17.5};
    private static final double[] LAVA = {40.5, 63.5};

    private BuildUhcArenas() {
    }

    /** Biome-specific parts of a map. */
    private interface Theme {
        /** Surface block of a column. */
        String top(Canvas c, int x, int z, int h);

        /** Block {@code depth} blocks under the surface (1 = directly below) at absolute y. */
        String under(Canvas c, int y, int depth);

        /** Places a tree with its trunk base at (x, y, z). */
        void tree(Canvas c, int x, int y, int z);

        /** Adds a plant or small decoration on top of the column (y = first air block). */
        void decorate(Canvas c, int x, int y, int z);

        /** Pond surface (water, or ice for frozen ponds). */
        String pondSurface();

        /** Pond rim and bed block. */
        String pondRim();

        /** Terrain height noise to block height (before spawn/pond/lava blending). */
        int height(Canvas c, int x, int z);
    }

    static GeneratedArena plains() {
        return build("uhc_plains", "<green>Plains", "GRASS_BLOCK", 9001, 14, new Theme() {
            @Override
            public String top(Canvas c, int x, int z, int h) {
                return c.rnd.nextDouble() < 0.04 ? "coarse_dirt" : "grass_block";
            }

            @Override
            public String under(Canvas c, int y, int depth) {
                return depth <= 3 ? "dirt" : c.rnd.nextDouble() < 0.012 ? c.pick("coal_ore", "iron_ore") : c.mix("stone", "andesite", 0.1);
            }

            @Override
            public void tree(Canvas c, int x, int y, int z) {
                boolean birch = c.rnd.nextDouble() < 0.3;
                c.broadTree(x, y, z, 4 + c.rnd.nextInt(3), birch ? "birch_log" : "oak_log", birch ? "birch_leaves" : "oak_leaves");
            }

            @Override
            public void decorate(Canvas c, int x, int y, int z) {
                double r = c.rnd.nextDouble();
                if (r < 0.14) {
                    c.plant(x, y, z, "short_grass", "grass_block");
                } else if (r < 0.165 && c.isAir(x, y + 1, z)) {
                    c.plant(x, y, z, "tall_grass[half=lower]", "grass_block");
                    if (c.get(x, y, z).contains("tall_grass")) {
                        c.set(x, y + 1, z, "tall_grass[half=upper]");
                    }
                } else if (r < 0.2) {
                    c.plant(x, y, z, c.pick("poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "red_tulip"), "grass_block");
                }
            }

            @Override
            public String pondSurface() {
                return "water";
            }

            @Override
            public String pondRim() {
                return "sand";
            }

            @Override
            public int height(Canvas c, int x, int z) {
                return 6 + (int) Math.round(c.noise.fractal(x, z, 30, 4) * 9);
            }
        });
    }

    static GeneratedArena taiga() {
        return build("uhc_taiga", "<dark_green>Taiga", "SPRUCE_SAPLING", 9002, 18, new Theme() {
            @Override
            public String top(Canvas c, int x, int z, int h) {
                double r = c.rnd.nextDouble();
                return r < 0.4 ? "podzol" : r < 0.55 ? "coarse_dirt" : "grass_block[snowy=true]";
            }

            @Override
            public String under(Canvas c, int y, int depth) {
                return depth <= 3 ? "dirt" : c.mix("stone", c.pick("andesite", "diorite", "gravel"), 0.15);
            }

            @Override
            public void tree(Canvas c, int x, int y, int z) {
                c.conifer(x, y, z, 7 + c.rnd.nextInt(4), "spruce_log", "spruce_leaves");
            }

            @Override
            public void decorate(Canvas c, int x, int y, int z) {
                double r = c.rnd.nextDouble();
                String ground = c.get(x, y - 1, z);
                if (ground.contains("snowy=true") && r < 0.7) {
                    c.setIfAir(x, y, z, "snow[layers=1]");
                } else if (r < 0.1) {
                    c.plant(x, y, z, "fern", "podzol", "grass_block");
                } else if (r < 0.12) {
                    c.plant(x, y, z, c.pick("brown_mushroom", "red_mushroom"), "podzol");
                }
            }

            @Override
            public String pondSurface() {
                return "ice";
            }

            @Override
            public String pondRim() {
                return "gravel";
            }

            @Override
            public int height(Canvas c, int x, int z) {
                return 5 + (int) Math.round(c.noise.fractal(x, z, 24, 4) * 11);
            }
        });
    }

    static GeneratedArena mesa() {
        String[] bands = {"orange_terracotta", "terracotta", "yellow_terracotta", "terracotta", "white_terracotta",
                "brown_terracotta", "red_terracotta", "orange_terracotta"};
        return build("uhc_mesa", "<gold>Mesa", "RED_SAND", 9003, 10, new Theme() {
            @Override
            public String top(Canvas c, int x, int z, int h) {
                return c.rnd.nextDouble() < 0.1 ? "terracotta" : "red_sand";
            }

            @Override
            public String under(Canvas c, int y, int depth) {
                return depth == 1 ? "red_sandstone" : bands[y % bands.length];
            }

            @Override
            public void tree(Canvas c, int x, int y, int z) {
                c.broadTree(x, y, z, 5, "acacia_log", "acacia_leaves");
            }

            @Override
            public void decorate(Canvas c, int x, int y, int z) {
                if (c.rnd.nextDouble() < 0.035) {
                    c.plant(x, y, z, "dead_bush", "red_sand", "terracotta");
                }
            }

            @Override
            public String pondSurface() {
                return "water";
            }

            @Override
            public String pondRim() {
                return "sand";
            }

            @Override
            public int height(Canvas c, int x, int z) {
                double n = c.noise.fractal(x, z, 26, 3);
                // Terraced plateaus: quantise to two-block steps.
                return 6 + 2 * (int) Math.round(n * 5);
            }
        });
    }

    private static GeneratedArena build(String name, String display, String icon, long seed, int trees, Theme theme) {
        Canvas c = new Canvas(SIZE, BUILD_LIMIT + 4, SIZE, seed);
        int[][] height = new int[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int h = theme.height(c, x, z);
                double spawnDist = Math.min(dist(x, z, SPAWNS[0][0] + 0.5, SPAWNS[0][1] + 0.5), dist(x, z, SPAWNS[1][0] + 0.5, SPAWNS[1][1] + 0.5));
                if (spawnDist < 6) {
                    h = SPAWN_Y;
                } else if (spawnDist < 13) {
                    double t = (spawnDist - 6) / 7;
                    h = (int) Math.round(SPAWN_Y + (h - SPAWN_Y) * t);
                }
                if (dist(x, z, POND[0], POND[1]) < 8.5) {
                    h = 7;
                }
                if (dist(x, z, LAVA[0], LAVA[1]) < 4.5) {
                    h = 9;
                }
                h = Math.max(4, Math.min(h, 17));
                height[x][z] = h;
                c.set(x, 0, z, "bedrock");
                for (int y = 1; y < h - 1; y++) {
                    c.set(x, y, z, theme.under(c, y, h - 1 - y));
                }
                c.set(x, h - 1, z, theme.top(c, x, z, h));
            }
        }
        // Pond: a three-deep basin whose rim (up to y 6) holds the water in.
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z, POND[0], POND[1]) + c.noise.value(x / 3.0, z / 3.0) * 0.8;
                if (d < 8.5) {
                    c.set(x, 6, z, theme.pondRim());
                }
                if (d < 5.5) {
                    c.set(x, 3, z, theme.pondRim());
                    c.set(x, 4, z, "water");
                    c.set(x, 5, z, "water");
                    c.set(x, 6, z, theme.pondSurface());
                }
            }
        }
        // Lava pocket: two blocks of lava in a stone crater.
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z, LAVA[0], LAVA[1]);
                if (d < 4.5) {
                    c.set(x, 8, z, d < 3.5 ? c.pick("stone", "andesite", "cobblestone") : "cobblestone");
                }
                if (d < 2.3) {
                    c.set(x, 6, z, "stone");
                    c.set(x, 7, z, "lava");
                    c.set(x, 8, z, "lava");
                }
            }
        }
        // Spawn pads.
        for (int[] s : SPAWNS) {
            for (int x = s[0] - 2; x <= s[0] + 2; x++) {
                for (int z = s[1] - 2; z <= s[1] + 2; z++) {
                    c.set(x, SPAWN_Y - 1, z, Math.abs(x - s[0]) == 2 || Math.abs(z - s[1]) == 2 ? "stone_bricks" : "polished_andesite");
                }
            }
        }
        // Trees, kept away from spawns, the pond, the lava and the border.
        int placed = 0;
        for (int attempt = 0; attempt < trees * 30 && placed < trees; attempt++) {
            int x = 4 + c.rnd.nextInt(SIZE - 8);
            int z = 4 + c.rnd.nextInt(SIZE - 8);
            if (dist(x, z, POND[0], POND[1]) < 10 || dist(x, z, LAVA[0], LAVA[1]) < 7
                    || !StandardArenas.farFrom(x, z, SPAWNS, 9) || !c.isAir(x, height[x][z], z)
                    || !c.isAir(x + 1, height[x][z] + 3, z) || !c.isAir(x - 1, height[x][z] + 3, z)) {
                continue;
            }
            theme.tree(c, x, height[x][z], z);
            placed++;
        }
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                if (StandardArenas.farFrom(x, z, SPAWNS, 4) && dist(x, z, LAVA[0], LAVA[1]) > 5 && dist(x, z, POND[0], POND[1]) > 9) {
                    theme.decorate(c, x, height[x][z], z);
                }
            }
        }
        // Border: a low wall on the terrain and barriers up to the top so nobody builds or walks out.
        for (int i = 0; i < SIZE; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, SIZE - 1}, {0, i}, {SIZE - 1, i}}) {
                int h = height[p[0]][p[1]];
                c.set(p[0], h, p[1], "cobblestone_wall");
            }
        }
        StandardArenas.fillBarrierAbove(c, 1);
        return new GeneratedArena.Builder(name, c.b)
                .display(display, icon)
                .tags("build")
                .spawns(spawn(SPAWNS[0][0], SPAWN_Y, SPAWNS[0][1], EAST), spawn(SPAWNS[1][0], SPAWN_Y, SPAWNS[1][1], WEST))
                .spectator(view(40.5, 30, 12.5, SOUTH))
                .limits(BUILD_LIMIT, -4)
                .buildArea(RelativeBox.of(1, 1, 1, SIZE - 2, BUILD_LIMIT, SIZE - 2))
                .build();
    }
}
