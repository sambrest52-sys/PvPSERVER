package net.pvpserver.lobby.layout;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * A block position, written in layout.yml as {@code "x y z"}.
 *
 * @param x x
 * @param y y
 * @param z z
 */
public record BlockPos(int x, int y, int z) {

    /**
     * @param text {@code "x y z"}
     * @return position
     * @throws IllegalArgumentException when the text is not three whole numbers
     */
    public static BlockPos parse(String text) {
        double[] v = LayoutNumbers.parse(text, 3, 3);
        for (double d : v) {
            if (d != Math.floor(d)) {
                throw new IllegalArgumentException("block positions need whole numbers: " + text);
            }
        }
        return new BlockPos((int) v[0], (int) v[1], (int) v[2]);
    }

    /** @return {@code "x y z"} */
    public String format() {
        return x + " " + y + " " + z;
    }

    /**
     * Packs the position into a long (26 bits x, 12 bits y, 26 bits z), the same scheme Minecraft uses.
     *
     * @return key
     */
    public long key() {
        return key(x, y, z);
    }

    /**
     * @param x x
     * @param y y
     * @param z z
     * @return packed key
     */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * @param block block
     * @return position
     */
    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /**
     * @param location location
     * @return block position
     */
    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * @param dx x offset
     * @param dy y offset
     * @param dz z offset
     * @return moved copy
     */
    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /** @return centre of the block's top face, where a player stands */
    public Point top() {
        return Point.of(x + 0.5, y + 1, z + 0.5);
    }

    /** @return centre of the block */
    public Point center() {
        return Point.of(x + 0.5, y + 0.5, z + 0.5);
    }

    /**
     * @param world world
     * @return block
     */
    public Block in(World world) {
        return world.getBlockAt(x, y, z);
    }
}
