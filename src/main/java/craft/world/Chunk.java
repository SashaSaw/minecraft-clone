package craft.world;

/**
 * A 16x128x16 chunk column. Blocks and light are flat arrays indexed (y<<8)|(z<<4)|x.
 * Meshing happens per 16^3 section (8 sections per column).
 */
public class Chunk {
    public static final int W = 16;
    public static final int H = 128;
    public static final int SECTIONS = H / 16;

    public static final int STATE_EMPTY = 0;
    public static final int STATE_TERRAIN = 1;
    public static final int STATE_DECORATED = 2;   // decorated + initial light done

    public final int cx, cz;
    public final byte[] blocks = new byte[W * W * H];
    public final byte[] skyLight = new byte[W * W * H];
    public final byte[] blockLight = new byte[W * W * H];
    /** Highest y with non-zero light opacity per column, -1 if none. Index z*16+x. */
    public final int[] lightHeight = new int[W * W];

    public volatile int state = STATE_EMPTY;
    public boolean modified;     // touched by player (needs saving)
    public boolean diskLoaded;   // restored from save (skip decoration)

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public static int idx(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    public byte getBlock(int x, int y, int z) {
        return blocks[idx(x, y, z)];
    }

    public void setBlock(int x, int y, int z, byte id) {
        blocks[idx(x, y, z)] = id;
    }

    public int getSky(int x, int y, int z) {
        return skyLight[idx(x, y, z)];
    }

    public void setSky(int x, int y, int z, int v) {
        skyLight[idx(x, y, z)] = (byte) v;
    }

    public int getBlockLight(int x, int y, int z) {
        return blockLight[idx(x, y, z)];
    }

    public void setBlockLight(int x, int y, int z, int v) {
        blockLight[idx(x, y, z)] = (byte) v;
    }

    /** Highest non-air block y in the column, or -1. */
    public int surfaceY(int x, int z) {
        for (int y = H - 1; y >= 0; y--) {
            if (blocks[idx(x, y, z)] != Block.AIR) return y;
        }
        return -1;
    }
}
