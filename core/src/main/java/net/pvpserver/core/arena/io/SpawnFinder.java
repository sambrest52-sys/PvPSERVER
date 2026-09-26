package net.pvpserver.core.arena.io;

import net.pvpserver.core.arena.TemplateBuilder;

/**
 * Finds standing spots in a template: a solid block with two free blocks above. Used to place spawns in imported
 * arenas that have no marker signs.
 */
public final class SpawnFinder {

    private SpawnFinder() {
    }

    private static String id(String block) {
        int bracket = block.indexOf('[');
        String id = bracket < 0 ? block : block.substring(0, bracket);
        return id.substring(id.indexOf(':') + 1);
    }

    /**
     * @param block block data string
     * @return whether a player can stand on it
     */
    public static boolean standable(String block) {
        String id = id(block);
        return !id.equals("air") && !id.equals("water") && !id.equals("lava") && !id.equals("barrier") && !id.contains("carpet")
                && !id.contains("grass") || id.equals("grass_block");
    }

    /**
     * @param block block data string
     * @return whether a player's body fits in it
     */
    public static boolean free(String block) {
        String id = id(block);
        return id.equals("air") || id.equals("short_grass") || id.equals("fern") || id.equals("snow") || id.contains("carpet")
                || id.contains("flower") || id.equals("poppy") || id.equals("dandelion") || id.equals("light");
    }

    /**
     * Highest standing spot in the column, searching outward in rings up to the radius.
     *
     * @param b template
     * @param x column x
     * @param z column z
     * @param radius search radius
     * @return {x, y, z} of the feet position, or null
     */
    public static int[] near(TemplateBuilder b, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    if (!b.inside(cx, 0, cz)) {
                        continue;
                    }
                    for (int y = b.sizeY() - 2; y >= 1; y--) {
                        if (standable(b.get(cx, y - 1, cz)) && free(b.get(cx, y, cz)) && free(b.get(cx, y + 1, cz))) {
                            return new int[]{cx, y, cz};
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Guesses two facing spawns at a quarter and three quarters along the template's longer axis.
     *
     * @param b template
     * @return {{ax, ay, az}, {bx, by, bz}} or null when no standing spot exists
     */
    public static int[][] guessSpawns(TemplateBuilder b) {
        boolean alongX = b.sizeX() >= b.sizeZ();
        int length = alongX ? b.sizeX() : b.sizeZ();
        int across = (alongX ? b.sizeZ() : b.sizeX()) / 2;
        int first = length / 4;
        int second = length - 1 - length / 4;
        int radius = Math.max(3, length / 6);
        int[] a = alongX ? near(b, first, across, radius) : near(b, across, first, radius);
        int[] c = alongX ? near(b, second, across, radius) : near(b, across, second, radius);
        return a == null || c == null ? null : new int[][]{a, c};
    }
}
