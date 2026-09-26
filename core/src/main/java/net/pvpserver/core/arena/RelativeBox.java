package net.pvpserver.core.arena;

import java.util.Locale;

/**
 * Axis-aligned block box relative to a template's minimum corner (inclusive on both ends). Used for an arena's
 * optional build area.
 *
 * @param minX min x
 * @param minY min y
 * @param minZ min z
 * @param maxX max x
 * @param maxY max y
 * @param maxZ max z
 */
public record RelativeBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * Normalises corners so min <= max on every axis.
     *
     * @param x1 corner 1 x
     * @param y1 corner 1 y
     * @param z1 corner 1 z
     * @param x2 corner 2 x
     * @param y2 corner 2 y
     * @param z2 corner 2 z
     * @return box
     */
    public static RelativeBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RelativeBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return whether the block is inside
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** @return {@code x1,y1,z1,x2,y2,z2} */
    public String serialize() {
        return String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d", minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * @param text serialised form
     * @return box or null when blank or malformed
     */
    public static RelativeBox parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] p = text.split(",");
        if (p.length != 6) {
            return null;
        }
        try {
            return of(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                    Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
