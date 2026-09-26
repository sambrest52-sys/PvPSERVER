package net.pvpserver.lobby.gen;

/**
 * Local coordinates for a building that faces one of the four cardinal directions. {@code a} runs to the right of a
 * visitor standing in front of the building and looking at it, {@code b} runs from the front towards the back.
 */
final class Frame {

    private final int cx;
    private final int cz;
    /** Direction the front faces (towards visitors). */
    private final int fx;
    private final int fz;

    /**
     * @param cx origin x
     * @param cz origin z
     * @param front direction the front faces: north, east, south or west
     */
    Frame(int cx, int cz, String front) {
        this.cx = cx;
        this.cz = cz;
        switch (front) {
            case "north" -> {
                fx = 0;
                fz = -1;
            }
            case "east" -> {
                fx = 1;
                fz = 0;
            }
            case "south" -> {
                fx = 0;
                fz = 1;
            }
            case "west" -> {
                fx = -1;
                fz = 0;
            }
            default -> throw new IllegalArgumentException(front);
        }
    }

    // Right of a visitor looking at the front (looking along -f) is (f.z, -f.x); the back is -f.
    int x(double a, double b) {
        return (int) Math.floor(cx + a * fz - b * fx);
    }

    int z(double a, double b) {
        return (int) Math.floor(cz - a * fx - b * fz);
    }

    /** Exact (unfloored) world x of a local point. */
    double px(double a, double b) {
        return cx + a * fz - b * fx;
    }

    /** Exact (unfloored) world z of a local point. */
    double pz(double a, double b) {
        return cz - a * fx - b * fz;
    }

    /**
     * @param local front, back, left or right
     * @return world direction name
     */
    String dir(String local) {
        int dx;
        int dz;
        switch (local) {
            case "front" -> {
                dx = fx;
                dz = fz;
            }
            case "back" -> {
                dx = -fx;
                dz = -fz;
            }
            case "right" -> {
                dx = fz;
                dz = -fx;
            }
            case "left" -> {
                dx = -fz;
                dz = fx;
            }
            default -> throw new IllegalArgumentException(local);
        }
        if (dx == 1) {
            return "east";
        }
        if (dx == -1) {
            return "west";
        }
        return dz == 1 ? "south" : "north";
    }

    /** @return yaw of someone looking out of the front */
    float frontYaw() {
        return (float) Math.toDegrees(Math.atan2(-fx, fz));
    }

    /** @return yaw of someone looking at the front (towards the back) */
    float backYaw() {
        return (float) Math.toDegrees(Math.atan2(fx, -fz));
    }

    /** @return true when the building's a axis runs along world x */
    boolean aAlongX() {
        return fz != 0;
    }
}
