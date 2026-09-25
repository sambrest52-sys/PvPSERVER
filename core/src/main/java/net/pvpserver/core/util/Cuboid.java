package net.pvpserver.core.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Immutable axis aligned integer box, world-agnostic.
 *
 * @param minX min x
 * @param minY min y
 * @param minZ min z
 * @param maxX max x
 * @param maxY max y
 * @param maxZ max z
 */
public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * Builds a cuboid from two arbitrary corners.
     *
     * @param a first corner
     * @param b second corner
     * @return normalised cuboid
     */
    public static Cuboid of(Location a, Location b) {
        return new Cuboid(Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    /**
     * @param x block x
     * @param y block y
     * @param z block z
     * @return whether the block is inside
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * @param location location (world ignored)
     * @return whether the location is inside
     */
    public boolean contains(Location location) {
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Horizontal containment only (ignores y), used for safe zones.
     *
     * @param location location
     * @return whether x/z are inside
     */
    public boolean containsXZ(Location location) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** @return size along x */
    public int sizeX() {
        return maxX - minX + 1;
    }

    /** @return size along y */
    public int sizeY() {
        return maxY - minY + 1;
    }

    /** @return size along z */
    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** @return total block volume */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /**
     * @param offset translation
     * @return shifted copy
     */
    public Cuboid shift(Vector offset) {
        return new Cuboid(minX + offset.getBlockX(), minY + offset.getBlockY(), minZ + offset.getBlockZ(),
                maxX + offset.getBlockX(), maxY + offset.getBlockY(), maxZ + offset.getBlockZ());
    }

    /** @return {@code minX,minY,minZ,maxX,maxY,maxZ} */
    public String serialize() {
        return minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
    }

    /**
     * @param text output of {@link #serialize()}
     * @return cuboid or null when malformed
     */
    public static Cuboid parse(String text) {
        if (text == null) {
            return null;
        }
        String[] p = text.split(",");
        if (p.length != 6) {
            return null;
        }
        try {
            return new Cuboid(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                    Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()), Integer.parseInt(p[5].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
