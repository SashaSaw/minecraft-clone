package craft.gen;

import craft.world.Block;
import craft.world.Chunk;
import craft.world.World;

import java.util.Random;

/**
 * Chunk decoration: ores, trees, tall grass, flowers, sugar cane.
 * All writes stay inside the chunk (trees clamped to local [2,13]) so decoration
 * needs no neighbor chunks and stays deterministic per chunk seed.
 */
public class Decorator {
    private static final int PHASE_ORES = 1, PHASE_TREES = 2, PHASE_GRASS = 3,
            PHASE_FLOWERS = 4, PHASE_CANE = 5;

    private final long worldSeed;

    public Decorator(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    public void decorate(World world, Chunk chunk) {
        ores(chunk, Block.COAL_ORE, 12, 5, 60, 5, 12, rng(chunk, PHASE_ORES));
        ores(chunk, Block.IRON_ORE, 8, 5, 45, 3, 8, rng(chunk, PHASE_ORES + 100));
        trees(chunk, rng(chunk, PHASE_TREES));
        tallGrass(chunk, rng(chunk, PHASE_GRASS));
        flowers(chunk, rng(chunk, PHASE_FLOWERS));
        sugarCane(world, chunk, rng(chunk, PHASE_CANE));
    }

    private Random rng(Chunk c, int phase) {
        long s = worldSeed
                ^ (c.cx * 341873128712L)
                ^ (c.cz * 132897987541L)
                ^ (phase * 0x9E3779B97F4A7C15L);
        return new Random(splitmix64(s));
    }

    private static long splitmix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private void ores(Chunk c, byte ore, int attempts, int yMin, int yMax, int sMin, int sMax, Random r) {
        for (int i = 0; i < attempts; i++) {
            int x = r.nextInt(16), z = r.nextInt(16);
            int y = yMin + r.nextInt(yMax - yMin);
            int size = sMin + r.nextInt(sMax - sMin + 1);
            for (int j = 0; j < size; j++) {
                if (x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 1 && y < Chunk.H
                        && c.getBlock(x, y, z) == Block.STONE) {
                    c.setBlock(x, y, z, ore);
                }
                x += r.nextInt(3) - 1;
                y += r.nextInt(3) - 1;
                z += r.nextInt(3) - 1;
            }
        }
    }

    private void trees(Chunk c, Random r) {
        int count = r.nextInt(10) == 0 ? 1 + r.nextInt(2) : (r.nextInt(5) == 0 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            int x = 2 + r.nextInt(12), z = 2 + r.nextInt(12);
            int ground = c.surfaceY(x, z);
            if (ground < 0 || c.getBlock(x, ground, z) != Block.GRASS) continue;
            int trunk = 4 + r.nextInt(3);
            if (ground + trunk + 2 >= Chunk.H) continue;
            c.setBlock(x, ground, z, Block.DIRT);
            // Leaves: 4 layers, y = trunk-3..trunk above ground; vanilla corner randomness
            for (int ly = trunk - 3; ly <= trunk; ly++) {
                int rad = (ly >= trunk - 1) ? 1 : 2;
                boolean topLayer = ly == trunk;
                for (int dx = -rad; dx <= rad; dx++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
                        if (corner && (topLayer || r.nextInt(2) == 0)) continue;
                        int lx = x + dx, lz = z + dz, lyy = ground + 1 + ly;
                        if (c.getBlock(lx, lyy, lz) == Block.AIR) {
                            c.setBlock(lx, lyy, lz, Block.OAK_LEAVES);
                        }
                    }
                }
            }
            for (int ty = 1; ty <= trunk; ty++) {
                c.setBlock(x, ground + ty, z, Block.OAK_LOG);
            }
        }
    }

    private void tallGrass(Chunk c, Random r) {
        int patches = 3 + r.nextInt(3);
        for (int p = 0; p < patches; p++) {
            int cx = r.nextInt(16), cz = r.nextInt(16);
            for (int i = 0; i < 24; i++) {
                int x = cx + r.nextInt(8) - r.nextInt(8);
                int z = cz + r.nextInt(8) - r.nextInt(8);
                if (x < 0 || x > 15 || z < 0 || z > 15) continue;
                placePlantOnGrass(c, x, z, Block.TALL_GRASS);
            }
        }
    }

    private void flowers(Chunk c, Random r) {
        for (int p = 0; p < 2; p++) {
            if (r.nextBoolean()) continue;
            byte flower = r.nextBoolean() ? Block.DANDELION : Block.POPPY;
            int cx = r.nextInt(16), cz = r.nextInt(16);
            for (int i = 0; i < 8; i++) {
                int x = cx + r.nextInt(7) - 3;
                int z = cz + r.nextInt(7) - 3;
                if (x < 0 || x > 15 || z < 0 || z > 15) continue;
                placePlantOnGrass(c, x, z, flower);
            }
        }
    }

    private void placePlantOnGrass(Chunk c, int x, int z, byte plant) {
        int ground = c.surfaceY(x, z);
        if (ground < 0 || ground + 1 >= Chunk.H) return;
        if (c.getBlock(x, ground, z) == Block.GRASS && c.getBlock(x, ground + 1, z) == Block.AIR) {
            c.setBlock(x, ground + 1, z, plant);
        }
    }

    private void sugarCane(World world, Chunk c, Random r) {
        int baseX = c.cx << 4, baseZ = c.cz << 4;
        for (int i = 0; i < 6; i++) {
            int x = r.nextInt(16), z = r.nextInt(16);
            int ground = c.surfaceY(x, z);
            if (ground < 0 || ground + 3 >= Chunk.H) continue;
            byte g = c.getBlock(x, ground, z);
            if (g != Block.GRASS && g != Block.SAND) continue;
            if (c.getBlock(x, ground + 1, z) != Block.AIR) continue;
            // needs horizontally adjacent water at ground level (reads may cross chunks)
            boolean nearWater = false;
            int wx = baseX + x, wz = baseZ + z;
            if (world.getBlock(wx + 1, ground, wz) == Block.WATER) nearWater = true;
            else if (world.getBlock(wx - 1, ground, wz) == Block.WATER) nearWater = true;
            else if (world.getBlock(wx, ground, wz + 1) == Block.WATER) nearWater = true;
            else if (world.getBlock(wx, ground, wz - 1) == Block.WATER) nearWater = true;
            if (!nearWater) continue;
            int height = 2 + r.nextInt(2);
            for (int h = 1; h <= height; h++) {
                if (c.getBlock(x, ground + h, z) != Block.AIR) break;
                c.setBlock(x, ground + h, z, Block.SUGAR_CANE);
            }
        }
    }
}
