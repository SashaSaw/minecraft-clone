package craft.entity;

import craft.world.Block;
import craft.world.World;

/** Base entity: AABB (width x height, centered on x/z, feet at y) with axis-clipped movement. */
public abstract class Entity {
    public double x, y, z;
    public double prevX, prevY, prevZ;
    public double vx, vy, vz;
    public float yaw, pitch;
    public boolean onGround;
    public boolean collidedHorizontally;
    public double fallDistance;
    public boolean removed;

    public final double width, height;

    protected Entity(double width, double height, double x, double y, double z) {
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
        this.z = z;
        prevX = x;
        prevY = y;
        prevZ = z;
    }

    public abstract void tick(World world);

    public void rememberPosition() {
        prevX = x;
        prevY = y;
        prevZ = z;
    }

    public boolean isInWater(World world) {
        int x0 = (int) Math.floor(x - width / 2), x1 = (int) Math.floor(x + width / 2);
        int z0 = (int) Math.floor(z - width / 2), z1 = (int) Math.floor(z + width / 2);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + height * 0.5);
        for (int by = y0; by <= y1; by++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int bx = x0; bx <= x1; bx++) {
                    if (world.getBlock(bx, by, bz) == Block.WATER) return true;
                }
            }
        }
        return false;
    }

    /** Axis-separated collision-clipped movement; updates onGround/collided flags and zeroes blocked velocity. */
    public void move(World world, double dx, double dy, double dz) {
        double origDy = dy, origDx = dx, origDz = dz;

        dy = clipAxis(world, 1, dy);
        y += dy;
        dx = clipAxis(world, 0, dx);
        x += dx;
        dz = clipAxis(world, 2, dz);
        z += dz;

        onGround = origDy < 0 && dy != origDy;
        if (onGround) {
            if (fallDistance > 0) onLand(fallDistance);
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
        double minX = x - width / 2, maxX = x + width / 2;
        double minY = y, maxY = y + height;
        double minZ = z - width / 2, maxZ = z + width / 2;

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

    /** Called when landing after a fall of `dist` blocks. */
    protected void onLand(double dist) {
    }

    public double distSq(double px, double py, double pz) {
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
