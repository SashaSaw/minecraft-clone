package craft.util;

import craft.world.Block;
import craft.world.Face;
import craft.world.World;

/** Amanatides & Woo voxel grid traversal from the eye along the look vector. */
public final class Raycast {
    public static class Hit {
        public int x, y, z;
        public int face;   // Face constant of the struck side
    }

    public static Hit cast(World world, double ox, double oy, double oz,
                           double dx, double dy, double dz, double maxDist) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx != 0 ? Math.abs(1 / dx) : Double.MAX_VALUE;
        double tDeltaY = dy != 0 ? Math.abs(1 / dy) : Double.MAX_VALUE;
        double tDeltaZ = dz != 0 ? Math.abs(1 / dz) : Double.MAX_VALUE;
        double tMaxX = dx != 0 ? frac(ox, stepX) * tDeltaX : Double.MAX_VALUE;
        double tMaxY = dy != 0 ? frac(oy, stepY) * tDeltaY : Double.MAX_VALUE;
        double tMaxZ = dz != 0 ? frac(oz, stepZ) * tDeltaZ : Double.MAX_VALUE;

        int face = Face.TOP;
        for (int i = 0; i < 256; i++) {
            byte id = world.getBlock(x, y, z);
            if (targetable(id)) {
                Hit h = new Hit();
                h.x = x;
                h.y = y;
                h.z = z;
                h.face = face;
                return h;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > maxDist) return null;
                x += stepX;
                tMaxX += tDeltaX;
                face = stepX > 0 ? Face.WEST : Face.EAST;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > maxDist) return null;
                y += stepY;
                tMaxY += tDeltaY;
                face = stepY > 0 ? Face.BOTTOM : Face.TOP;
            } else {
                if (tMaxZ > maxDist) return null;
                z += stepZ;
                tMaxZ += tDeltaZ;
                face = stepZ > 0 ? Face.NORTH : Face.SOUTH;
            }
        }
        return null;
    }

    private static boolean targetable(byte id) {
        return id != Block.AIR && id != Block.WATER;
    }

    private static double frac(double v, int step) {
        double f = v - Math.floor(v);
        return step > 0 ? 1 - f : f;
    }

    private Raycast() {
    }
}
