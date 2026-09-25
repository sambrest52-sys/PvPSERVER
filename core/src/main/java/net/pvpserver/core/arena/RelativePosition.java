package net.pvpserver.core.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Position relative to a template's minimum corner.
 *
 * @param x relative x
 * @param y relative y
 * @param z relative z
 * @param yaw yaw
 * @param pitch pitch
 */
public record RelativePosition(double x, double y, double z, float yaw, float pitch) {

    /**
     * @param world world of the instance
     * @param originX instance origin x
     * @param originY instance origin y
     * @param originZ instance origin z
     * @return absolute location
     */
    public Location toLocation(World world, int originX, int originY, int originZ) {
        return new Location(world, originX + x, originY + y, originZ + z, yaw, pitch);
    }

    /**
     * @param location absolute location
     * @param minX template min x in that world
     * @param minY template min y
     * @param minZ template min z
     * @return relative position
     */
    public static RelativePosition of(Location location, int minX, int minY, int minZ) {
        return new RelativePosition(location.getX() - minX, location.getY() - minY, location.getZ() - minZ,
                location.getYaw(), location.getPitch());
    }

    /** @return {@code x,y,z,yaw,pitch} */
    public String serialize() {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.2f,%.2f", x, y, z, yaw, pitch);
    }

    /**
     * @param text serialised form
     * @return parsed position or null
     */
    public static RelativePosition parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] p = text.split(",");
        try {
            return new RelativePosition(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    p.length > 3 ? Float.parseFloat(p[3]) : 0f, p.length > 4 ? Float.parseFloat(p[4]) : 0f);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
