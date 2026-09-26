package net.pvpserver.lobby.layout;

/**
 * An inclusive block box, written in layout.yml as two corners.
 *
 * @param min minimum corner
 * @param max maximum corner
 */
public record Box(BlockPos min, BlockPos max) {

    /**
     * @param a corner
     * @param b opposite corner
     * @return normalised box
     */
    public static Box of(BlockPos a, BlockPos b) {
        return new Box(new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    /**
     * @param x block x
     * @param y block y
     * @param z block z
     * @return whether the block is inside
     */
    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x() && y >= min.y() && y <= max.y() && z >= min.z() && z <= max.z();
    }

    /** @return number of blocks */
    public long volume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }

    /** @return centre point */
    public Point center() {
        return Point.of((min.x() + max.x() + 1) / 2.0, (min.y() + max.y() + 1) / 2.0, (min.z() + max.z() + 1) / 2.0);
    }
}
