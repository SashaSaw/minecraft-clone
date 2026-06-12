package craft.player;

import craft.Input;
import craft.world.Block;
import craft.world.World;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Vanilla-style player physics, per tick (20 TPS):
 *   vel += accel; pos += vel (collision-clipped); vy = (vy-0.08)*0.98; vxz *= friction.
 * Hitbox 0.6 x 1.8, eye 1.62. Walk 4.317 m/s, sprint 5.612, jump 0.42 -> 1.25 blocks.
 */
public class Player {
    public static final double WIDTH = 0.6, HEIGHT = 1.8, EYE = 1.62;

    public double x, y, z;             // feet center
    public double prevX, prevY, prevZ;
    public double vx, vy, vz;
    public float yaw, pitch;           // radians; yaw 0 = -Z (north), pitch + = down
    public boolean onGround, sprinting, sneaking;
    public boolean inWater;
    public double fallDistance;

    private boolean collidedHorizontally;
    private long lastWPressTick = -100;
    private boolean sprintLatch;
    public long tickCounter;

    public Player(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        prevX = x;
        prevY = y;
        prevZ = z;
    }

    /** Mouse look — call per frame. */
    public void turn(double dx, double dy, double sensitivity) {
        yaw += dx * sensitivity;
        pitch += dy * sensitivity;
        float limit = (float) Math.toRadians(89.9);
        if (pitch > limit) pitch = limit;
        if (pitch < -limit) pitch = -limit;
    }

    public void onKeyPress(int key) {
        if (key == GLFW_KEY_W) {
            if (tickCounter - lastWPressTick <= 7) sprintLatch = true;
            lastWPressTick = tickCounter;
        }
    }

    public void tick(Input in, World world) {
        tickCounter++;
        prevX = x;
        prevY = y;
        prevZ = z;

        double fwd = 0, strafe = 0;
        if (in.isDown(GLFW_KEY_W)) fwd += 1;
        if (in.isDown(GLFW_KEY_S)) fwd -= 1;
        if (in.isDown(GLFW_KEY_A)) strafe -= 1;
        if (in.isDown(GLFW_KEY_D)) strafe += 1;
        sneaking = in.isDown(GLFW_KEY_LEFT_SHIFT);

        boolean wantSprint = (in.isDown(GLFW_KEY_LEFT_CONTROL) || sprintLatch) && fwd > 0 && !sneaking;
        if (!in.isDown(GLFW_KEY_W)) sprintLatch = false;
        if (collidedHorizontally) sprintLatch = false;
        sprinting = wantSprint;

        // normalize input, rotate into world space
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
            if (in.isDown(GLFW_KEY_SPACE)) vy += 0.05;
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
            if (in.isDown(GLFW_KEY_SPACE) && onGround) {
                vy = 0.42;
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
    }

    private boolean isInWater(World world) {
        int x0 = (int) Math.floor(x - WIDTH / 2), x1 = (int) Math.floor(x + WIDTH / 2);
        int z0 = (int) Math.floor(z - WIDTH / 2), z1 = (int) Math.floor(z + WIDTH / 2);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + 0.8);
        for (int by = y0; by <= y1; by++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int bx = x0; bx <= x1; bx++) {
                    if (world.getBlock(bx, by, bz) == Block.WATER) return true;
                }
            }
        }
        return false;
    }

    /** Eye is underwater (for fog). */
    public boolean eyeInWater(World world) {
        return world.getBlock((int) Math.floor(x), (int) Math.floor(y + EYE), (int) Math.floor(z))
                == Block.WATER;
    }

    /** Axis-separated collision clipping against solid blocks. */
    private void move(World world, double dx, double dy, double dz) {
        double origDy = dy, origDx = dx, origDz = dz;

        dy = clipAxis(world, 1, dy);
        y += dy;
        dx = clipAxis(world, 0, dx);
        x += dx;
        dz = clipAxis(world, 2, dz);
        z += dz;

        onGround = origDy < 0 && dy != origDy;
        if (onGround) {
            if (fallDistance > 0) fallDistance = Math.max(0, fallDistance);
            fallDistance = 0;
        } else if (dy < 0) {
            fallDistance -= dy;
        }
        collidedHorizontally = dx != origDx || dz != origDz;
        if (dx != origDx) vx = 0;
        if (dy != origDy) vy = 0;
        if (dz != origDz) vz = 0;
    }

    private double clipAxis(World world, int axis, double delta) {
        if (delta == 0) return 0;
        double minX = x - WIDTH / 2, maxX = x + WIDTH / 2;
        double minY = y, maxY = y + HEIGHT;
        double minZ = z - WIDTH / 2, maxZ = z + WIDTH / 2;

        // swept region
        double sMinX = minX + (axis == 0 && delta < 0 ? delta : 0);
        double sMaxX = maxX + (axis == 0 && delta > 0 ? delta : 0);
        double sMinY = minY + (axis == 1 && delta < 0 ? delta : 0);
        double sMaxY = maxY + (axis == 1 && delta > 0 ? delta : 0);
        double sMinZ = minZ + (axis == 2 && delta < 0 ? delta : 0);
        double sMaxZ = maxZ + (axis == 2 && delta > 0 ? delta : 0);

        int bx0 = (int) Math.floor(sMinX), bx1 = (int) Math.floor(sMaxX);
        int by0 = (int) Math.floor(sMinY), by1 = (int) Math.floor(sMaxY);
        int bz0 = (int) Math.floor(sMinZ), bz1 = (int) Math.floor(sMaxZ);

        final double EPS = 1e-7;
        for (int by = by0; by <= by1; by++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    if (!Block.get(world.getBlock(bx, by, bz)).solid) continue;
                    // block AABB is (bx,by,bz)..(+1,+1,+1)
                    switch (axis) {
                        case 0 -> {
                            if (maxY > by && minY < by + 1 && maxZ > bz && minZ < bz + 1) {
                                if (delta > 0 && bx >= maxX) delta = Math.min(delta, bx - maxX - EPS);
                                if (delta < 0 && bx + 1 <= minX) delta = Math.max(delta, bx + 1 - minX + EPS);
                            }
                        }
                        case 1 -> {
                            if (maxX > bx && minX < bx + 1 && maxZ > bz && minZ < bz + 1) {
                                if (delta > 0 && by >= maxY) delta = Math.min(delta, by - maxY - EPS);
                                if (delta < 0 && by + 1 <= minY) delta = Math.max(delta, by + 1 - minY + EPS);
                            }
                        }
                        case 2 -> {
                            if (maxX > bx && minX < bx + 1 && maxY > by && minY < by + 1) {
                                if (delta > 0 && bz >= maxZ) delta = Math.min(delta, bz - maxZ - EPS);
                                if (delta < 0 && bz + 1 <= minZ) delta = Math.max(delta, bz + 1 - minZ + EPS);
                            }
                        }
                    }
                }
            }
        }
        return delta;
    }
}
