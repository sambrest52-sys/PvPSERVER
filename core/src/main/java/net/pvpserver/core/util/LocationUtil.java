package net.pvpserver.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Serialises locations to/from compact strings and config sections.
 */
public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * @param location location to serialise
     * @return {@code world;x;y;z;yaw;pitch}
     */
    public static String serialize(Location location) {
        if (location == null) {
            return "";
        }
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        return String.format(java.util.Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.2f;%.2f", world,
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Parses {@link #serialize(Location)} output. Missing worlds resolve to {@code null} world.
     *
     * @param text serialised text
     * @return location or {@code null}
     */
    public static Location deserialize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length < 4) {
            return null;
        }
        try {
            World world = parts[0].isEmpty() ? null : Bukkit.getWorld(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads a location written as a string at {@code path}.
     *
     * @param section config section
     * @param path key
     * @return location or null
     */
    public static Location read(ConfigurationSection section, String path) {
        return section == null ? null : deserialize(section.getString(path));
    }

    /**
     * Returns the world name of the serialised location string without resolving the world.
     *
     * @param text serialised location
     * @return world name or empty string
     */
    public static String worldName(String text) {
        if (text == null) {
            return "";
        }
        int idx = text.indexOf(';');
        return idx < 0 ? "" : text.substring(0, idx);
    }

    /**
     * Centers a location on its block (x.5, z.5) keeping y, yaw and pitch.
     *
     * @param location input
     * @return centred copy
     */
    public static Location center(Location location) {
        Location copy = location.clone();
        copy.setX(location.getBlockX() + 0.5);
        copy.setZ(location.getBlockZ() + 0.5);
        return copy;
    }
}
