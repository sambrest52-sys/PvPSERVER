package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.RelativePosition;
import net.pvpserver.core.arena.TemplateBuilder;

import java.util.Random;

/**
 * Drawing helpers on top of a {@link TemplateBuilder}: discs, rings, walls, pillars, trees, lights and barrier
 * shells. Every arena gets its own seeded {@link Random}, so generation is deterministic.
 */
public final class Canvas {

    /** Yaw facing +z. */
    public static final float SOUTH = 0f;
    /** Yaw facing -x. */
    public static final float WEST = 90f;
    /** Yaw facing -z. */
    public static final float NORTH = 180f;
    /** Yaw facing +x. */
    public static final float EAST = -90f;

    public final TemplateBuilder b;
    public final Random rnd;
    public final Noise noise;

    public Canvas(int sizeX, int sizeY, int sizeZ, long seed) {
        this.b = new TemplateBuilder(sizeX, sizeY, sizeZ);
        this.rnd = new Random(seed);
        this.noise = new Noise(seed * 31 + 7);
    }

    public int sx() {
        return b.sizeX();
    }

    public int sy() {
        return b.sizeY();
    }

    public int sz() {
        return b.sizeZ();
    }

    public void set(int x, int y, int z, String block) {
        b.set(x, y, z, block);
    }

    public void setIfAir(int x, int y, int z, String block) {
        b.setIfAir(x, y, z, block);
    }

    public boolean isAir(int x, int y, int z) {
        return b.isAir(x, y, z);
    }

    public String get(int x, int y, int z) {
        return b.get(x, y, z);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        b.fill(x1, y1, z1, x2, y2, z2, block);
    }

    /** Random element. */
    public String pick(String... options) {
        return options[rnd.nextInt(options.length)];
    }

    /** Returns {@code rare} with the given probability, else {@code common}. */
    public String mix(String common, String rare, double chance) {
        return rnd.nextDouble() < chance ? rare : common;
    }

    /** Spawn position standing on block (x, y - 1, z), centred. */
    public static RelativePosition spawn(int x, int y, int z, float yaw) {
        return new RelativePosition(x + 0.5, y, z + 0.5, yaw, 0f);
    }

    /** Spectator position looking down at the arena. */
    public static RelativePosition view(double x, double y, double z, float yaw) {
        return new RelativePosition(x, y, z, yaw, 35f);
    }

    /** Distance from a block centre to (cx, cz). */
    public static double dist(int x, int z, double cx, double cz) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Facing of a stair whose high side points away from (cx, cz). */
    public static String facingAway(int x, int z, double cx, double cz) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "east" : "west";
        }
        return dz >= 0 ? "south" : "north";
    }

    /** Facing of a stair whose high side points towards (cx, cz). */
    public static String facingToward(int x, int z, double cx, double cz) {
        return switch (facingAway(x, z, cx, cz)) {
            case "east" -> "west";
            case "west" -> "east";
            case "south" -> "north";
            default -> "south";
        };
    }

    // ------------------------------------------------------------------ shapes

    /** Filled circle at height y. */
    public void disc(double cx, int y, double cz, double r, String block) {
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++) {
            for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                if (dist(x, z, cx, cz) <= r) {
                    set(x, y, z, block);
                }
            }
        }
    }

    /** Annulus rInner < d <= rOuter at height y. */
    public void ring(double cx, int y, double cz, double rInner, double rOuter, String block) {
        for (int x = (int) Math.floor(cx - rOuter - 1); x <= (int) Math.ceil(cx + rOuter + 1); x++) {
            for (int z = (int) Math.floor(cz - rOuter - 1); z <= (int) Math.ceil(cz + rOuter + 1); z++) {
                double d = dist(x, z, cx, cz);
                if (d > rInner && d <= rOuter) {
                    set(x, y, z, block);
                }
            }
        }
    }

    /** Hollow rectangle (the four edges) from y1 to y2. */
    public void perimeter(int x1, int z1, int x2, int z2, int y1, int y2, String block) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, block);
                set(x, y, z2, block);
            }
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, block);
                set(x2, y, z, block);
            }
        }
    }

    /** Invisible barrier shell around the whole template from y1 to the top. */
    public void barrierShell(int y1) {
        perimeter(0, 0, sx() - 1, sz() - 1, y1, sy() - 1, "barrier");
    }

    /** Barrier ring (cylinder wall) at radius r from y1 to y2. */
    public void barrierRing(double cx, double cz, double r, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            ring(cx, y, cz, r - 1, r, "barrier");
        }
    }

    /** Vertical column. */
    public void column(int x, int z, int y1, int y2, String block) {
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, block);
        }
    }

    /** Inverted cone of rock under a floating platform (radius shrinks going down). */
    public void underside(double cx, int topY, double cz, double radius, int depth, String... blocks) {
        for (int i = 0; i < depth; i++) {
            double r = radius * (1 - (double) (i + 1) / (depth + 1)) + rnd.nextDouble() * 0.8;
            for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++) {
                for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                    if (dist(x, z, cx, cz) <= r - rnd.nextDouble() * 0.9) {
                        set(x, topY - i, z, pick(blocks));
                    }
                }
            }
        }
    }

    /** Solid ball (only replacing air when {@code onlyAir}). */
    public void ball(double cx, double cy, double cz, double r, boolean onlyAir, String... blocks) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
            for (int y = (int) Math.floor(cy - r); y <= (int) Math.ceil(cy + r); y++) {
                for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                    double dx = x + 0.5 - cx;
                    double dy = y + 0.5 - cy;
                    double dz = z + 0.5 - cz;
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        if (onlyAir) {
                            setIfAir(x, y, z, pick(blocks));
                        } else {
                            set(x, y, z, pick(blocks));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ decoration

    /** Post (fence/wall/log column) of the given height topped by a standing lantern. */
    public void lanternPost(int x, int y, int z, String post, int height, String lantern) {
        column(x, z, y, y + height - 1, post);
        set(x, y + height, z, lantern + "[hanging=false]");
    }

    /** A broad-leaf tree (oak, birch, acacia style) with persistent leaves. */
    public void broadTree(int x, int y, int z, int height, String log, String leaves) {
        String leaf = leaves + "[persistent=true]";
        int top = y + height - 1;
        for (int ly = top - 2; ly <= top + 1; ly++) {
            int r = ly <= top - 1 ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (ly == top + 1 || rnd.nextBoolean())) {
                        continue;
                    }
                    setIfAir(x + dx, ly, z + dz, leaf);
                }
            }
        }
        column(x, z, y, top, log + "[axis=y]");
    }

    /** A conical conifer with persistent leaves. */
    public void conifer(int x, int y, int z, int height, String log, String leaves) {
        String leaf = leaves + "[persistent=true]";
        int top = y + height;
        set(x, top, z, leaf);
        set(x, top - 1, z, leaf);
        for (int i = 0; i < height - 2; i++) {
            int ly = top - 2 - i;
            int r = (i % 3 == 2) ? Math.max(1, i / 3) : Math.min(3, 1 + i / 2);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= r + (r > 1 ? 1 : 0) && !(dx == 0 && dz == 0)) {
                        setIfAir(x + dx, ly, z + dz, leaf);
                    }
                }
            }
        }
        column(x, z, y, top - 1, log + "[axis=y]");
    }

    /** A plant on top of the block at (x, y - 1, z) when that block is one of the accepted grounds. */
    public void plant(int x, int y, int z, String plant, String... grounds) {
        String below = get(x, y - 1, z);
        for (String ground : grounds) {
            if (below.startsWith("minecraft:" + ground)) {
                setIfAir(x, y, z, plant);
                return;
            }
        }
    }

    /** Flattens a square pad of the given radius so the top solid block is at {@code y - 1} and clears above it. */
    public void pad(int cx, int y, int cz, int radius, String top, String under) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                fill(x, 0, z, x, y - 2, z, under);
                set(x, y - 1, z, top);
                fill(x, y, z, x, sy() - 1, z, "air");
            }
        }
    }

    /** Ring of stairs between two radii; {@code up} = the high side faces the centre (steps up inwards). */
    public void stairRing(double cx, double cz, double rInner, double rOuter, int y, String stairs, boolean up) {
        for (int x = (int) Math.floor(cx - rOuter - 1); x <= (int) Math.ceil(cx + rOuter + 1); x++) {
            for (int z = (int) Math.floor(cz - rOuter - 1); z <= (int) Math.ceil(cz + rOuter + 1); z++) {
                double d = dist(x, z, cx, cz);
                if (d > rInner && d <= rOuter) {
                    String facing = up ? facingToward(x, z, cx, cz) : facingAway(x, z, cx, cz);
                    set(x, y, z, stairs + "[facing=" + facing + "]");
                }
            }
        }
    }

    /** Whether (x, z) is at least {@code min} blocks from every point. */
    public static boolean farFrom(int x, int z, int[][] points, double min) {
        for (int[] p : points) {
            if (dist(x, z, p[0] + 0.5, p[1] + 0.5) < min) {
                return false;
            }
        }
        return true;
    }

    /** Fills air above the outer edge columns with barrier so nobody can climb or pearl out. */
    public void fillBarrierAbove(int fromY) {
        for (int y = fromY; y < sy(); y++) {
            for (int i = 0; i < sx(); i++) {
                setIfAir(i, y, 0, "barrier");
                setIfAir(i, y, sz() - 1, "barrier");
            }
            for (int i = 0; i < sz(); i++) {
                setIfAir(0, y, i, "barrier");
                setIfAir(sx() - 1, y, i, "barrier");
            }
        }
    }
}
