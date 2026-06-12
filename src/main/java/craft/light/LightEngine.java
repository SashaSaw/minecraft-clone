package craft.light;

import craft.util.LongQueue;
import craft.world.Block;
import craft.world.Chunk;
import craft.world.World;

/**
 * Minecraft-style 0-15 sky/block light with BFS flood fill.
 * Sky light: top-exposed cells are 15; downward propagation of level-15 light does not
 * decrement (sunlight shafts); all other steps cost max(1, opacity).
 * Removal uses the classic two-queue algorithm.
 */
public class LightEngine {
    private final World world;
    private final LongQueue skyQ = new LongQueue();
    private final LongQueue blockQ = new LongQueue();
    private final LongQueue removalQ = new LongQueue();   // carries (pos<<5)|oldLevel

    private static final int[][] DIRS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public LightEngine(World world) {
        this.world = world;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int unpackX(long p) {
        return (int) (p >> 38);
    }

    private static int unpackZ(long p) {
        return (int) ((p << 26) >> 38);
    }

    private static int unpackY(long p) {
        return (int) (p & 0xFFF);
    }

    /** Initial lighting for a freshly decorated chunk: column scan + BFS, pulling from neighbors. */
    public void initChunk(Chunk c) {
        int baseX = c.cx << 4, baseZ = c.cz << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = Chunk.H - 1;
                for (; y >= 0; y--) {
                    if (Block.get(c.getBlock(x, y, z)).opacity > 0) break;
                    c.setSky(x, y, z, 15);
                    skyQ.add(pack(baseX + x, y, baseZ + z));
                }
                c.lightHeight[(z << 4) | x] = y;
            }
        }
        // Pull light in from already-lit neighbor chunk borders
        pullBorder(c.cx - 1, c.cz, 15, -1);
        pullBorder(c.cx + 1, c.cz, 0, -1);
        pullBorder(c.cx, c.cz - 1, -1, 15);
        pullBorder(c.cx, c.cz + 1, -1, 0);
        propagate(skyQ, true);
        propagate(blockQ, false);
    }

    private void pullBorder(int cx, int cz, int localX, int localZ) {
        Chunk n = world.getChunk(cx, cz);
        if (n == null || n.state < Chunk.STATE_DECORATED) return;
        int baseX = cx << 4, baseZ = cz << 4;
        for (int i = 0; i < 16; i++) {
            int x = localX >= 0 ? localX : i;
            int z = localZ >= 0 ? localZ : i;
            for (int y = 0; y < Chunk.H; y++) {
                if (n.getSky(x, y, z) > 1) skyQ.add(pack(baseX + x, y, baseZ + z));
                if (n.getBlockLight(x, y, z) > 1) blockQ.add(pack(baseX + x, y, baseZ + z));
            }
        }
    }

    /** Incremental update when a block changes (player edits). */
    public void onBlockChanged(int x, int y, int z, byte oldId, byte newId) {
        Block oldB = Block.get(oldId), newB = Block.get(newId);

        // light emission (torches, lit furnaces)
        if (oldB.emission > 0 && newB.emission < oldB.emission) {
            removeLight(x, y, z, false);
        }
        if (newB.emission > 0) {
            setLight(x, y, z, newB.emission, false);
            blockQ.add(pack(x, y, z));
        }

        if (newB.opacity > oldB.opacity) {
            // Light got blocked: remove sky light at the cell and re-flood
            removeLight(x, y, z, true);
            removeLight(x, y, z, false);
        } else if (newB.opacity < oldB.opacity) {
            // More transparent: recompute the sky column above/at this cell
            Chunk c = world.getChunk(x >> 4, z >> 4);
            if (c != null) {
                int lx = x & 15, lz = z & 15;
                int yy = Chunk.H - 1;
                for (; yy >= 0; yy--) {
                    if (Block.get(c.getBlock(lx, yy, lz)).opacity > 0) break;
                    if (c.getSky(lx, yy, lz) != 15) {
                        c.setSky(lx, yy, lz, 15);
                        world.markDirtyAround(x, yy, z);
                    }
                    skyQ.add(pack(x, yy, z));
                }
                c.lightHeight[(lz << 4) | lx] = yy;
            }
            // Neighbors may now shine into this cell
            for (int[] d : DIRS) {
                skyQ.add(pack(x + d[0], y + d[1], z + d[2]));
                blockQ.add(pack(x + d[0], y + d[1], z + d[2]));
            }
        }
        propagate(skyQ, true);
        propagate(blockQ, false);
    }

    private void removeLight(int x, int y, int z, boolean sky) {
        int old = sky ? world.getSky(x, y, z) : world.getBlockLight(x, y, z);
        setLight(x, y, z, 0, sky);
        removalQ.add((pack(x, y, z) << 5) | old);
        LongQueue repropQ = sky ? skyQ : blockQ;

        while (!removalQ.isEmpty()) {
            long e = removalQ.poll();
            int level = (int) (e & 31);
            long p = e >>> 5;
            int px = unpackX(p), py = unpackY(p), pz = unpackZ(p);
            for (int[] d : DIRS) {
                int nx = px + d[0], ny = py + d[1], nz = pz + d[2];
                if (ny < 0 || ny >= Chunk.H) continue;
                int nl = sky ? world.getSky(nx, ny, nz) : world.getBlockLight(nx, ny, nz);
                if (nl == 0) continue;
                boolean downSun = sky && d[1] == -1 && level == 15 && nl == 15;
                if (nl < level || downSun) {
                    setLight(nx, ny, nz, 0, sky);
                    removalQ.add((pack(nx, ny, nz) << 5) | nl);
                } else {
                    repropQ.add(pack(nx, ny, nz));
                }
            }
        }
        // The removed cell's column may have been a sunlight shaft; recheck top exposure
        if (sky) {
            Chunk c = world.getChunk(x >> 4, z >> 4);
            if (c != null) {
                int lx = x & 15, lz = z & 15;
                int yy = Chunk.H - 1;
                for (; yy >= 0; yy--) {
                    if (Block.get(c.getBlock(lx, yy, lz)).opacity > 0) break;
                    c.setSky(lx, yy, lz, 15);
                    skyQ.add(pack(x, yy, z));
                }
                c.lightHeight[(lz << 4) | lx] = yy;
            }
        }
    }

    private void propagate(LongQueue q, boolean sky) {
        while (!q.isEmpty()) {
            long p = q.poll();
            int x = unpackX(p), y = unpackY(p), z = unpackZ(p);
            int level = sky ? world.getSky(x, y, z) : world.getBlockLight(x, y, z);
            if (level <= 1) continue;
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (ny < 0 || ny >= Chunk.H) continue;
                Chunk nc = world.getChunk(nx >> 4, nz >> 4);
                if (nc == null) continue;
                int op = Block.get(nc.getBlock(nx & 15, ny, nz & 15)).opacity;
                if (op >= 15) continue;
                int target;
                if (sky && d[1] == -1 && level == 15 && op == 0) {
                    target = 15;
                } else {
                    target = level - Math.max(1, op);
                }
                int cur = sky ? nc.getSky(nx & 15, ny, nz & 15) : nc.getBlockLight(nx & 15, ny, nz & 15);
                if (target > cur) {
                    setLight(nx, ny, nz, target, sky);
                    q.add(pack(nx, ny, nz));
                }
            }
        }
    }

    private void setLight(int x, int y, int z, int v, boolean sky) {
        Chunk c = world.getChunk(x >> 4, z >> 4);
        if (c == null) return;
        if (sky) c.setSky(x & 15, y, z & 15, v);
        else c.setBlockLight(x & 15, y, z & 15, v);
        if (c.state >= Chunk.STATE_DECORATED) world.markDirtyAround(x, y, z);
    }
}
