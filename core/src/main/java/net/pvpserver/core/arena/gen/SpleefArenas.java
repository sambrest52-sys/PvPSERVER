package net.pvpserver.core.arena.gen;

import static net.pvpserver.core.arena.gen.Canvas.EAST;
import static net.pvpserver.core.arena.gen.Canvas.SOUTH;
import static net.pvpserver.core.arena.gen.Canvas.WEST;
import static net.pvpserver.core.arena.gen.Canvas.spawn;
import static net.pvpserver.core.arena.gen.Canvas.view;

/**
 * Spleef arenas (tag {@code spleef}): snow floors (the only blocks the spleef kit may break) above a drop that
 * ends the round.
 */
final class SpleefArenas {

    private SpleefArenas() {
    }

    /** One round snow floor (radius 13) inside a packed-ice bowl over the void. */
    static GeneratedArena classic() {
        Canvas c = new Canvas(33, 16, 33, 8001);
        double cx = 16.5;
        c.disc(cx, 8, cx, 13, "snow_block");
        for (int y = 5; y <= 11; y++) {
            c.ring(cx, y, cx, 13, 15, y == 9 ? "blue_ice" : "packed_ice");
        }
        c.ring(cx, 12, cx, 13, 15, "snow_block");
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            c.set((int) Math.floor(cx + Math.cos(a) * 14), 12, (int) Math.floor(cx + Math.sin(a) * 14), "sea_lantern");
        }
        c.barrierRing(cx, cx, 14, 13, c.sy() - 1);
        return new GeneratedArena.Builder("spleef_classic", c.b)
                .display("<white>Snow Bowl", "SNOW_BLOCK")
                .tags("spleef")
                .spawns(spawn(11, 9, 16, EAST), spawn(21, 9, 16, WEST))
                .spectator(view(16.5, 14, 4.5, SOUTH))
                .limits(9, 6)
                .build();
    }

    /** Three stacked snow layers in a glass tower: falling through one layer lands you on the next. */
    static GeneratedArena layers() {
        Canvas c = new Canvas(27, 22, 27, 8002);
        for (int y : new int[]{4, 10, 16}) {
            c.fill(2, y, 2, 24, y, 24, "snow_block");
        }
        for (int y = 1; y < c.sy(); y++) {
            c.perimeter(1, 1, 25, 25, y, y, "light_blue_stained_glass");
        }
        for (int[] p : new int[][]{{1, 1}, {25, 1}, {1, 25}, {25, 25}}) {
            for (int y = 1; y < c.sy(); y++) {
                c.set(p[0], y, p[1], y % 4 == 0 ? "sea_lantern" : "packed_ice");
            }
        }
        c.fill(1, 0, 1, 25, 0, 25, "air");
        return new GeneratedArena.Builder("spleef_layers", c.b)
                .display("<aqua>Snow Layers", "LIGHT_BLUE_STAINED_GLASS")
                .tags("spleef")
                .spawns(spawn(8, 17, 13, EAST), spawn(18, 17, 13, WEST))
                .spectator(view(13.5, 21, 3.5, SOUTH))
                .limits(20, 2)
                .build();
    }

    /** Snow floor over a lava pit, walled in nether bricks. */
    static GeneratedArena lavaPit() {
        Canvas c = new Canvas(29, 18, 29, 8003);
        double cx = 14.5;
        c.disc(cx, 0, cx, 14.5, "nether_bricks");
        c.disc(cx, 1, cx, 13, "lava");
        c.ring(cx, 1, cx, 13, 14.5, "nether_bricks");
        c.ring(cx, 2, cx, 13, 14.5, "nether_bricks");
        c.disc(cx, 7, cx, 12, "snow_block");
        for (int y = 3; y <= 10; y++) {
            c.ring(cx, y, cx, 12, 14.5, y == 6 ? "red_nether_bricks" : "nether_bricks");
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 + 15);
            c.set((int) Math.floor(cx + Math.cos(a) * 12.6), 9, (int) Math.floor(cx + Math.sin(a) * 12.6), "shroomlight");
        }
        for (int x = 0; x < 29; x++) {
            for (int z = 0; z < 29; z++) {
                double d = Canvas.dist(x, z, cx, cx);
                if (d > 13.5 && d <= 14.5 && (x + z) % 2 == 0) {
                    c.set(x, 11, z, "nether_brick_fence");
                }
            }
        }
        c.barrierRing(cx, cx, 13, 11, c.sy() - 1);
        return new GeneratedArena.Builder("spleef_lava", c.b)
                .display("<red>Lava Pit", "LAVA_BUCKET")
                .tags("spleef")
                .spawns(spawn(10, 8, 14, EAST), spawn(18, 8, 14, WEST))
                .spectator(view(14.5, 14, 3.5, SOUTH))
                .limits(9, 5)
                .build();
    }
}
