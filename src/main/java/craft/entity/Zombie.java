package craft.entity;

import craft.item.Item;
import craft.item.ItemStack;
import craft.player.Player;
import craft.render.Sky;
import craft.world.World;

/** Hostile: chases the player within 16 blocks, melee 3 dmg, burns in daylight, despawns far away. */
public class Zombie extends Mob {
    private int attackCooldown;
    private int burnTimer;
    public boolean burning;

    public Zombie(double x, double y, double z) {
        super(0.6, 1.95, 20, 2.4, x, y, z);
    }

    @Override
    protected void aiStep(World world) {
        Player p = world.player;
        boolean chasing = p != null && !p.dead && distSq(p.x, p.y, p.z) < 16 * 16;

        if (attackCooldown > 0) attackCooldown--;

        if (chasing) {
            targetX = p.x;
            targetZ = p.z;
            walkTimer = 10;
            moveSpeed = 1f;
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            if (dx * dx + dy * dy + dz * dz < 2.0 * 2.0 && attackCooldown == 0) {
                attackCooldown = 20;
                p.damage(3);
                // knock the player back
                double len = Math.max(0.01, Math.hypot(dx, dz));
                p.vx += dx / len * 0.4;
                p.vz += dz / len * 0.4;
                if (p.onGround) p.vy = Math.max(p.vy, 0.35);
            }
        } else {
            super.aiStep(world);
        }

        // daylight burning
        if (age % 20 == 0) {
            boolean day = Sky.dayFactor(world.time) > 0.6f;
            if (day && seesSky(world) && !isInWater(world)) {
                burning = true;
                burnTimer = 160;
            }
        }
        if (burnTimer > 0) {
            burnTimer--;
            burning = burnTimer > 0;
            if (age % 20 == 0) {
                hurtTicks = 0;   // fire ignores i-frames
                damage(world, 1, x, z);
            }
        }

        // despawn far from player
        if (p != null) {
            double d2 = distSq(p.x, p.y, p.z);
            if (d2 > 128 * 128) removed = true;
            else if (d2 > 32 * 32 && rng.nextInt(800) == 0) removed = true;
        }
    }

    @Override
    protected void onLand(double dist) {
        int dmg = (int) Math.round(dist - 3);
        if (dmg > 0) {
            hp -= dmg;   // direct, no knockback
            hurtTicks = 10;
            if (hp <= 0) removed = true;
        }
    }

    @Override
    protected ItemStack[] deathDrops() {
        int n = rng.nextInt(3);
        return n == 0 ? new ItemStack[0]
                : new ItemStack[]{new ItemStack(Item.ROTTEN_FLESH, n)};
    }
}
