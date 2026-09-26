package net.pvpserver.core.arena.gen;

/**
 * Seeded 2D value noise with smooth interpolation and fractal octaves. Deterministic: the same seed always builds
 * the same terrain, so generated arenas are reproducible.
 */
public final class Noise {

    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    private double lattice(int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * @param x x
     * @param z z
     * @return noise in [0, 1)
     */
    public double value(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = lattice(x0, z0);
        double b = lattice(x0 + 1, z0);
        double c = lattice(x0, z0 + 1);
        double d = lattice(x0 + 1, z0 + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /**
     * Fractal sum of octaves, normalised to [0, 1).
     *
     * @param x x
     * @param z z
     * @param scale feature size in blocks of the first octave
     * @param octaves number of octaves
     * @return noise in [0, 1)
     */
    public double fractal(double x, double z, double scale, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1 / scale;
        for (int i = 0; i < octaves; i++) {
            sum += value(x * frequency + i * 17.3, z * frequency - i * 9.1) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return sum / total;
    }
}
