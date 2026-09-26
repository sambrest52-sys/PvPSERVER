package net.pvpserver.lobby.gen;

/**
 * Launch pad ballistics using the player's airborne physics: each tick the player moves by its velocity, then
 * vertical velocity becomes {@code (vy - 0.08) * 0.98} and horizontal velocity is multiplied by 0.91.
 */
public final class LaunchMath {

    /** Gravity per tick. */
    public static final double GRAVITY = 0.08;
    /** Vertical drag. */
    public static final double DRAG_Y = 0.98;
    /** Horizontal drag while airborne. */
    public static final double DRAG_XZ = 0.91;

    private LaunchMath() {
    }

    /**
     * Velocity that carries a player standing at (x, y, z) to land on (tx, ty, tz), arcing at least {@code clearance}
     * blocks above the higher of the two points.
     *
     * @param x start x (feet)
     * @param y start y (feet)
     * @param z start z (feet)
     * @param tx target x (feet)
     * @param ty target y (feet, i.e. top of the landing block)
     * @param tz target z (feet)
     * @param clearance minimum apex above the higher end
     * @return {vx, vy, vz}
     * @throws IllegalArgumentException when no velocity within Minecraft's limits reaches the target
     */
    public static double[] solve(double x, double y, double z, double tx, double ty, double tz, double clearance) {
        double dx = tx - x;
        double dz = tz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double rise = ty - y;
        for (double vy = 0.5; vy <= 3.5; vy += 0.02) {
            double apex = apex(vy);
            if (apex < Math.max(0, rise) + clearance) {
                continue;
            }
            int ticks = ticksUntilDescendingTo(vy, rise);
            if (ticks < 0) {
                continue;
            }
            double horizontal = distance * (1 - DRAG_XZ) / (1 - Math.pow(DRAG_XZ, ticks));
            if (horizontal > 3.0) {
                continue;
            }
            double scale = distance < 1e-9 ? 0 : horizontal / distance;
            return new double[]{round(dx * scale), round(vy), round(dz * scale)};
        }
        throw new IllegalArgumentException("no launch reaches " + distance + " blocks away and " + rise + " up");
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /**
     * @param vy initial vertical velocity
     * @return highest point above the start
     */
    public static double apex(double vy) {
        double height = 0;
        double best = 0;
        for (int i = 0; i < 400 && vy > 0; i++) {
            height += vy;
            best = Math.max(best, height);
            vy = (vy - GRAVITY) * DRAG_Y;
        }
        return best;
    }

    /** Ticks until the player, falling again, reaches {@code rise} above the start; -1 if it never gets that high. */
    private static int ticksUntilDescendingTo(double vy, double rise) {
        double height = 0;
        for (int tick = 1; tick < 600; tick++) {
            height += vy;
            vy = (vy - GRAVITY) * DRAG_Y;
            if (vy < 0 && height <= rise) {
                return tick;
            }
        }
        return -1;
    }

    /**
     * Simulates a launch and returns the position every tick until {@code ticks} have passed.
     *
     * @param x start x
     * @param y start y
     * @param z start z
     * @param velocity {vx, vy, vz}
     * @param ticks ticks
     * @return positions {x, y, z} per tick (index 0 = after the first tick)
     */
    public static double[][] trajectory(double x, double y, double z, double[] velocity, int ticks) {
        double[][] path = new double[ticks][];
        double vx = velocity[0];
        double vy = velocity[1];
        double vz = velocity[2];
        for (int i = 0; i < ticks; i++) {
            x += vx;
            y += vy;
            z += vz;
            path[i] = new double[]{x, y, z};
            vy = (vy - GRAVITY) * DRAG_Y;
            vx *= DRAG_XZ;
            vz *= DRAG_XZ;
        }
        return path;
    }
}
