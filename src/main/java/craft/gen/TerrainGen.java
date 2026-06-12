package craft.gen;

import craft.world.Block;
import craft.world.Chunk;

/**
 * Base terrain: continentalness spline (oceans vs plains) + detail noise.
 * Pure function of (x, z) — safe to run on worker threads, no cross-chunk writes.
 */
public class TerrainGen {
    public static final int SEA_LEVEL = 62;

    private final Perlin continental;
    private final Perlin detail;
    private final Perlin dirtNoise;
    private final Perlin gravelNoise;

    // Continentalness -> base height spline control points
    private static final double[] SPLINE_C = {-1.0, -0.5, -0.2, -0.05, 0.10, 1.0};
    private static final double[] SPLINE_H = {46, 48, 56, 62, 66, 70};

    public TerrainGen(long seed) {
        continental = new Perlin(seed ^ 0x5DEECE66DL);
        detail = new Perlin(seed ^ 0x2545F4914F6CDD1DL);
        dirtNoise = new Perlin(seed ^ 0x9E3779B97F4A7C15L);
        gravelNoise = new Perlin(seed ^ 0x6C078965L);
    }

    public int height(int x, int z) {
        double c = clamp(continental.fbm(x, z, 3, 1.0 / 800.0) * 1.55, -1, 1);
        double d = detail.fbm(x, z, 4, 1.0 / 128.0);
        double base = spline(c);
        double amp = lerpClamped(c, -0.2, 0.1, 2.0, 5.0);
        int h = (int) Math.round(base + d * amp);
        return Math.max(40, Math.min(90, h));
    }

    public void generate(Chunk chunk) {
        int baseX = chunk.cx << 4, baseZ = chunk.cz << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x, wz = baseZ + z;
                int h = height(wx, wz);
                int dirtDepth = dirtNoise.noise(wx * 0.034, wz * 0.034) > 0.3 ? 4 : 3;
                boolean beach = h >= SEA_LEVEL - 3 && h <= SEA_LEVEL + 1;
                boolean underwater = h < SEA_LEVEL - 3;

                chunk.setBlock(x, 0, z, Block.BEDROCK);
                for (int y = 1; y <= h; y++) {
                    byte id;
                    if (y < h - dirtDepth) {
                        id = Block.STONE;
                    } else if (y < h) {
                        id = (beach || underwater) ? Block.SAND : Block.DIRT;
                    } else { // surface
                        if (underwater) {
                            id = gravelNoise.noise(wx / 24.0, wz / 24.0) > 0.4 ? Block.GRAVEL : Block.SAND;
                        } else if (beach) {
                            id = Block.SAND;
                        } else {
                            id = Block.GRASS;
                        }
                    }
                    chunk.setBlock(x, y, z, id);
                }
                for (int y = h + 1; y <= SEA_LEVEL; y++) {
                    chunk.setBlock(x, y, z, Block.WATER);
                }
            }
        }
        chunk.state = Chunk.STATE_TERRAIN;
    }

    private static double spline(double c) {
        for (int i = 1; i < SPLINE_C.length; i++) {
            if (c <= SPLINE_C[i]) {
                double t = (c - SPLINE_C[i - 1]) / (SPLINE_C[i] - SPLINE_C[i - 1]);
                return SPLINE_H[i - 1] + t * (SPLINE_H[i] - SPLINE_H[i - 1]);
            }
        }
        return SPLINE_H[SPLINE_H.length - 1];
    }

    private static double lerpClamped(double v, double a, double b, double outA, double outB) {
        double t = (v - a) / (b - a);
        t = Math.max(0, Math.min(1, t));
        return outA + t * (outB - outA);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
