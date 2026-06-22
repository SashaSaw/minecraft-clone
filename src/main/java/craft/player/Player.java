package craft.player;

import craft.Input;
import craft.entity.Entity;
import craft.entity.Mob;
import craft.item.Inventory;
import craft.world.Block;
import craft.world.World;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Vanilla-style player physics, per tick (20 TPS):
 *   vel += accel; pos += vel (collision-clipped); vy = (vy-0.08)*0.98; vxz *= friction.
 * Hitbox 0.6 x 1.8, eye 1.62. Walk 4.317 m/s, sprint 5.612, jump 0.42 -> 1.25 blocks.
 * Survival: 20 HP, 20 hunger + saturation, air 300 ticks, exhaustion model.
 */
public class Player extends Entity {
    public static final double EYE = 1.62;

    /** First-/third-person hand swing animation length, in ticks. */
    public static final int SWING_TICKS = 6;

    public boolean sprinting, sneaking;
    public boolean inWater;

    // third-person body animation
    public float bodyYaw, prevBodyYaw;
    public float limbSwing, limbSwingAmount;
    /** Counts down each tick while the arm is mid-swing (attack/break/place). */
    public int swingTicks;

    public final Inventory inventory = new Inventory();

    // survival state
    public int hp = 20;
    public int hunger = 20;
    public float saturation = 5;
    public float exhaustion;
    public int air = 300;
    public int invulnTicks;
    public boolean dead;
    public int hurtFlash;            // red vignette timer

    private long lastWPressTick = -100;
    private boolean sprintLatch;
    public long tickCounter;

    public Player(double x, double y, double z) {
        super(0.6, 1.8, x, y, z);
    }

    /** Mouse look — call per frame. */
    public void turn(double dx, double dy, double sensitivity) {
        yaw += dx * sensitivity;
        pitch += dy * sensitivity;
        float limit = (float) Math.toRadians(89.9);
        if (pitch > limit) pitch = limit;
        if (pitch < -limit) pitch = -limit;
    }

    /** Starts (or restarts) the arm swing shown by the hand/body model. */
    public void swing() {
        swingTicks = SWING_TICKS;
    }

    public void onKeyPress(int key) {
        if (key == GLFW_KEY_W) {
            if (tickCounter - lastWPressTick <= 7) sprintLatch = true;
            lastWPressTick = tickCounter;
        }
    }

    @Override
    public void tick(World world) {
        // movement handled in tick(Input, World); this satisfies Entity
    }

    public void tick(Input in, World world, boolean controls) {
        tickCounter++;
        rememberPosition();
        prevBodyYaw = bodyYaw;
        if (invulnTicks > 0) invulnTicks--;
        if (hurtFlash > 0) hurtFlash--;
        if (swingTicks > 0) swingTicks--;
        if (dead) return;

        double fwd = 0, strafe = 0;
        if (controls) {
            if (in.isDown(GLFW_KEY_W)) fwd += 1;
            if (in.isDown(GLFW_KEY_S)) fwd -= 1;
            if (in.isDown(GLFW_KEY_A)) strafe -= 1;
            if (in.isDown(GLFW_KEY_D)) strafe += 1;
        }
        sneaking = controls && in.isDown(GLFW_KEY_LEFT_SHIFT);

        boolean canSprint = hunger > 6;
        boolean wantSprint = controls && (in.isDown(GLFW_KEY_LEFT_CONTROL) || sprintLatch)
                && fwd > 0 && !sneaking && canSprint;
        if (!controls || !in.isDown(GLFW_KEY_W)) sprintLatch = false;
        if (collidedHorizontally) sprintLatch = false;
        sprinting = wantSprint;

        double len = Math.sqrt(fwd * fwd + strafe * strafe);
        if (len > 1) {
            fwd /= len;
            strafe /= len;
        }
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double wishX = (fwd * sin + strafe * cos) * 0.98;
        double wishZ = (-fwd * cos + strafe * sin) * 0.98;

        inWater = isInWater(world);

        if (inWater) {
            vx += wishX * 0.05;
            vz += wishZ * 0.05;
            if (controls && in.isDown(GLFW_KEY_SPACE)) vy += 0.05;
            move(world, vx, vy, vz);
            vx *= 0.8;
            vz *= 0.8;
            vy = vy * 0.8 - 0.02;
            fallDistance = 0;
        } else {
            double speedMult = sneaking ? 0.3 : (sprinting ? 1.3 : 1.0);
            double accel = onGround ? 0.1 * speedMult : 0.02 * (sprinting ? 1.3 : 1.0);
            vx += wishX * accel;
            vz += wishZ * accel;
            if (controls && in.isDown(GLFW_KEY_SPACE) && onGround) {
                vy = 0.42;
                exhaustion += sprinting ? 0.2f : 0.05f;
                if (sprinting) {
                    vx += sin * 0.2;
                    vz += -cos * 0.2;
                }
            }
            move(world, vx, vy, vz);
            vy = (vy - 0.08) * 0.98;
            double friction = onGround ? 0.546 : 0.91;
            vx *= friction;
            vz *= friction;
        }

        if (sprinting) {
            double moved = Math.hypot(x - prevX, z - prevZ);
            exhaustion += (float) (moved * 0.1);
        }

        // third-person walk animation: swing limbs by horizontal speed; the body turns
        // toward the direction of travel while walking, else eases to face the look dir
        double hs = Math.hypot(x - prevX, z - prevZ);
        limbSwingAmount += (float) ((Math.min(hs * 4, 1) - limbSwingAmount) * 0.4);
        limbSwing += (float) hs;
        float targetYaw = hs > 0.0025
                ? (float) Math.atan2(x - prevX, -(z - prevZ))
                : (float) yaw;
        float d = Mob.wrapAngle(targetYaw - bodyYaw);
        bodyYaw += Math.max(-0.35f, Math.min(0.35f, d));

        tickSurvival(world);
    }

    private void tickSurvival(World world) {
        // drowning
        if (eyeInWater(world)) {
            if (--air <= -20) {
                air = 0;
                damage(2);
            }
        } else {
            air = Math.min(300, air + 4);
        }

        // exhaustion -> hunger drain
        while (exhaustion >= 4f) {
            exhaustion -= 4f;
            if (saturation > 0) saturation = Math.max(0, saturation - 1);
            else if (hunger > 0) hunger--;
        }

        // regen / starvation
        if (tickCounter % 80 == 0) {
            if (hunger >= 18 && hp < 20) {
                hp++;
                exhaustion += 3f;
            } else if (hunger == 0 && hp > 1) {
                damage(1);
            }
        }
    }

    @Override
    protected void onLand(double dist) {
        int dmg = (int) Math.round(dist - 3);
        if (dmg > 0) damage(dmg);
    }

    public void damage(int amount) {
        if (dead || invulnTicks > 0) return;
        hp -= amount;
        invulnTicks = 10;
        hurtFlash = 10;
        if (hp <= 0) {
            hp = 0;
            dead = true;
        }
    }

    public boolean eat(int foodValue) {
        if (hunger >= 20) return false;
        hunger = Math.min(20, hunger + foodValue);
        saturation = Math.min(hunger, saturation + foodValue * 0.6f);
        return true;
    }

    public void respawn(int sx, int sy, int sz) {
        x = sx + 0.5;
        y = sy + 1;
        z = sz + 0.5;
        prevX = x;
        prevY = y;
        prevZ = z;
        vx = vy = vz = 0;
        hp = 20;
        hunger = 20;
        saturation = 5;
        air = 300;
        exhaustion = 0;
        fallDistance = 0;
        dead = false;
    }

    public boolean eyeInWater(World world) {
        return world.getBlock((int) Math.floor(x), (int) Math.floor(y + EYE), (int) Math.floor(z))
                == Block.WATER;
    }
}
