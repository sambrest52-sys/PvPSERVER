package net.pvpserver.lobby.layout;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * A position with a facing, written in layout.yml as {@code "x y z"} or {@code "x y z yaw pitch"}.
 *
 * @param x x
 * @param y y
 * @param z z
 * @param yaw yaw
 * @param pitch pitch
 */
public record Point(double x, double y, double z, float yaw, float pitch) {

    /**
     * @param x x
     * @param y y
     * @param z z
     * @return point without a facing
     */
    public static Point of(double x, double y, double z) {
        return new Point(x, y, z, 0f, 0f);
    }

    /**
     * @param text {@code "x y z [yaw [pitch]]"}, commas allowed as separators
     * @return point
     * @throws IllegalArgumentException when the text is not a point
     */
    public static Point parse(String text) {
        double[] v = LayoutNumbers.parse(text, 3, 5);
        return new Point(v[0], v[1], v[2], v.length > 3 ? (float) v[3] : 0f, v.length > 4 ? (float) v[4] : 0f);
    }

    /** @return {@code "x y z yaw pitch"} (facing omitted when zero) */
    public String format() {
        String base = LayoutNumbers.format(x) + " " + LayoutNumbers.format(y) + " " + LayoutNumbers.format(z);
        if (yaw == 0f && pitch == 0f) {
            return base;
        }
        return base + " " + LayoutNumbers.format(yaw) + " " + LayoutNumbers.format(pitch);
    }

    /**
     * @param dx x offset
     * @param dy y offset
     * @param dz z offset
     * @return moved copy
     */
    public Point add(double dx, double dy, double dz) {
        return new Point(x + dx, y + dy, z + dz, yaw, pitch);
    }

    /**
     * @param yaw new yaw
     * @param pitch new pitch
     * @return copy facing elsewhere
     */
    public Point facing(float yaw, float pitch) {
        return new Point(x, y, z, yaw, pitch);
    }

    /** @return block containing the point */
    public BlockPos block() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * @param world world
     * @return bukkit location
     */
    public Location at(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * @param location bukkit location
     * @return point rounded to a tenth of a block (and whole degrees) so layout.yml stays readable
     */
    public static Point from(Location location) {
        return new Point(round(location.getX()), round(location.getY()), round(location.getZ()),
                Math.round(location.getYaw()), Math.round(location.getPitch()));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * @param other other point
     * @return horizontal distance squared
     */
    public double distanceSquaredXZ(Point other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return dx * dx + dz * dz;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Point[%s]", format());
    }
}
