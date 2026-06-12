package craft.entity;

import craft.player.Player;
import craft.render.Sky;
import craft.world.Block;
import craft.world.Chunk;
import craft.world.World;

import java.util.Random;

/**
 * Passive herds spawn once per chunk at decoration time (weighted: sheep 12, pig 10,
 * chicken 10, cow 8). Zombies spawn at night, 24-64 blocks out, in low light, capped.
 */
public class MobSpawner {
    private static final int ZOMBIE_CAP = 24;

    private final Random rng = new Random();

    public void spawnHerd(World world, Chunk chunk, long worldSeed) {
        Random r = new Random(splitmix64(worldSeed
                ^ (chunk.cx * 341873128712L) ^ (chunk.cz * 132897987541L) ^ 0x6D0B5E));
        if (r.nextInt(8) != 0) return;
        int roll = r.nextInt(40);
        int type = roll < 12 ? 0 : roll < 22 ? 1 : roll < 32 ? 2 : 3;   // sheep/pig/chicken/cow
        int n = 2 + r.nextInt(3);
        int cx = r.nextInt(16), cz = r.nextInt(16);
        for (int i = 0; i < n; i++) {
            int lx = Math.max(0, Math.min(15, cx + r.nextInt(7) - 3));
            int lz = Math.max(0, Math.min(15, cz + r.nextInt(7) - 3));
            int surf = chunk.surfaceY(lx, lz);
            if (surf < 0 || chunk.getBlock(lx, surf, lz) != Block.GRASS) continue;
            double wx = (chunk.cx << 4) + lx + 0.5;
            double wy = surf + 1;
            double wz = (chunk.cz << 4) + lz + 0.5;
            world.entities.add(switch (type) {
                case 0 -> new Animals.Sheep(wx, wy, wz);
                case 1 -> new Animals.Pig(wx, wy, wz);
                case 2 -> new Animals.Chicken(wx, wy, wz);
                default -> new Animals.Cow(wx, wy, wz);
            });
        }
    }

    public void tickHostile(World world) {
        if (world.time % 20 != 0) return;
        Player p = world.player;
        if (p == null || p.dead) return;
        long t = world.time % Sky.DAY_TICKS;
        if (t < 13188 || t > 22812) return;   // surface monsters only at night

        int zombies = 0;
        for (Entity e : world.entities) {
            if (e instanceof Zombie) zombies++;
        }
        if (zombies >= ZOMBIE_CAP) return;

        for (int attempt = 0; attempt < 3; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 24 + rng.nextDouble() * 40;
            int x = (int) Math.floor(p.x + Math.cos(angle) * dist);
            int z = (int) Math.floor(p.z + Math.sin(angle) * dist);
            int surf = world.surfaceY(x, z);
            if (surf < 0 || surf + 3 >= Chunk.H) continue;
            if (!Block.get(world.getBlock(x, surf, z)).opaqueCube) continue;
            // light level check (torch-lit areas stay safe)
            int darken = Math.round((1 - Sky.dayFactor(world.time)) * 11);
            int light = Math.max(world.getBlockLight(x, surf + 1, z),
                    world.getSky(x, surf + 1, z) - darken);
            if (light > 7) continue;
            int pack = 1 + rng.nextInt(3);
            for (int i = 0; i < pack; i++) {
                world.entities.add(new Zombie(x + 0.5 + rng.nextInt(3) - 1, surf + 1,
                        z + 0.5 + rng.nextInt(3) - 1));
            }
            return;
        }
    }

    private static long splitmix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
