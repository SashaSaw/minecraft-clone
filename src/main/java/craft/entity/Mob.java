package craft.entity;

import craft.item.ItemStack;
import craft.world.Block;
import craft.world.World;

import java.util.Random;

/**
 * Base mob: vanilla-style ground physics, wander/panic AI, look-at-player,
 * knockback, hurt flash, auto-jump. Subclasses override aiStep for behavior.
 */
public abstract class Mob extends Entity {
    protected final Random rng = new Random();

    public int hp;
    public final int maxHp;
    public final double speedMps;          // cruise speed m/s
    public int hurtTicks;                  // red flash
    public float bodyYaw, prevBodyYaw;
    public float headYaw, headPitch;
    public float limbSwing, limbSwingAmount;
    public int age;

    // AI state
    protected double targetX, targetZ;
    protected int walkTimer;               // ticks left walking toward target
    protected int panicTicks;
    protected float moveSpeed;             // 0 = idle, 1 = walk, >1 = run

    protected Mob(double width, double height, int hp, double speedMps,
                  double x, double y, double z) {
        super(width, height, x, y, z);
        this.hp = hp;
        this.maxHp = hp;
        this.speedMps = speedMps;
        this.yaw = (float) (Math.random() * Math.PI * 2);
        this.bodyYaw = yaw;
    }

    @Override
    public void tick(World world) {
        rememberPosition();
        prevBodyYaw = bodyYaw;
        age++;
        if (hurtTicks > 0) hurtTicks--;

        moveSpeed = 0;
        aiStep(world);

        boolean inWater = isInWater(world);
        if (moveSpeed > 0) {
            // turn toward target point
            double dx = targetX - x, dz = targetZ - z;
            if (dx * dx + dz * dz > 0.4) {
                float want = (float) Math.atan2(dx, -dz);
                yaw = approachAngle(yaw, want, 0.25f);
            }
            double accel = speedMps * 0.0227 * moveSpeed;
            vx += Math.sin(yaw) * accel;
            vz += -Math.cos(yaw) * accel;
            if (collidedHorizontally && onGround) vy = 0.42;   // auto-jump
        }
        if (inWater) {
            vy += 0.05;   // bob/swim up
            fallDistance = 0;
        }

        move(world, vx, vy, vz);
        applyGravity();
        double friction = onGround ? 0.546 : 0.91;
        vx *= friction;
        vz *= friction;

        // body follows movement; head looks at player when close
        double hs = Math.hypot(x - prevX, z - prevZ);
        limbSwingAmount += (Math.min(hs * 4, 1) - limbSwingAmount) * 0.4f;
        limbSwing += (float) hs;
        if (hs > 0.005) bodyYaw = approachAngle(bodyYaw, yaw, 0.3f);

        Entity p = world.player;
        if (p != null && distSq(p.x, p.y, p.z) < 6 * 6) {
            double dx = p.x - x, dz = p.z - z;
            float lookYaw = (float) Math.atan2(dx, -dz);
            headYaw = clampAngle(lookYaw, bodyYaw, (float) Math.toRadians(75));
            double dy = (p.y + 1.6) - (y + height * 0.85);
            headPitch = (float) -Math.atan2(dy, Math.hypot(dx, dz));
        } else {
            headYaw = approachAngle(headYaw, bodyYaw, 0.15f);
            headPitch *= 0.9f;
        }
    }

    protected void applyGravity() {
        vy = (vy - 0.08) * 0.98;
    }

    /** Default passive AI: panic when hurt, otherwise wander occasionally. */
    protected void aiStep(World world) {
        if (panicTicks > 0) {
            panicTicks--;
            if (walkTimer <= 0 || reachedTarget()) pickRandomTarget(9);
            walkTimer--;
            moveSpeed = 1.6f;
        } else if (walkTimer > 0 && !reachedTarget()) {
            walkTimer--;
            moveSpeed = 1f;
        } else if (rng.nextInt(120) == 0) {
            pickRandomTarget(10);
        }
    }

    protected boolean reachedTarget() {
        double dx = targetX - x, dz = targetZ - z;
        return dx * dx + dz * dz < 1.0;
    }

    protected void pickRandomTarget(int range) {
        targetX = x + rng.nextInt(range * 2 + 1) - range;
        targetZ = z + rng.nextInt(range * 2 + 1) - range;
        walkTimer = 80 + rng.nextInt(80);
    }

    public void damage(World world, int amount, double fromX, double fromZ) {
        if (hurtTicks > 0) return;
        hp -= amount;
        hurtTicks = 10;
        panicTicks = 100;
        knockback(fromX, fromZ);
        if (hp <= 0) {
            removed = true;
            for (ItemStack drop : deathDrops()) {
                ItemEntity.scatter(world, x, y + height * 0.5, z, drop, rng);
            }
        }
    }

    public void knockback(double fromX, double fromZ) {
        double dx = x - fromX, dz = z - fromZ;
        double len = Math.max(0.01, Math.hypot(dx, dz));
        vx = vx / 2 + dx / len * 0.4;
        vz = vz / 2 + dz / len * 0.4;
        if (onGround) vy = Math.min(0.4, vy / 2 + 0.4);
    }

    protected abstract ItemStack[] deathDrops();

    /** True if standing under open sky (for zombie burning). */
    protected boolean seesSky(World world) {
        return world.getSky((int) Math.floor(x), (int) Math.floor(y + height), (int) Math.floor(z)) >= 14;
    }

    protected boolean onGrass(World world) {
        return world.getBlock((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z)) == Block.GRASS;
    }

    static float approachAngle(float cur, float want, float maxStep) {
        float d = wrapAngle(want - cur);
        if (d > maxStep) d = maxStep;
        if (d < -maxStep) d = -maxStep;
        return cur + d;
    }

    static float clampAngle(float want, float center, float limit) {
        float d = wrapAngle(want - center);
        if (d > limit) d = limit;
        if (d < -limit) d = -limit;
        return center + d;
    }

    public static float wrapAngle(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }
}
