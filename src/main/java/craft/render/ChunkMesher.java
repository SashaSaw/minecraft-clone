package craft.render;

import craft.util.FloatList;
import craft.world.Block;
import craft.world.Face;
import craft.world.World;

/**
 * Culled-face mesher for one 16^3 section, with per-vertex ambient occlusion and
 * smooth lighting (0fps algorithm) and the anti-anisotropy quad flip.
 *
 * Vertex format (8 floats): pos.xyz (x,z section-local, y world), uv, sky, block, shade.
 * Runs on worker threads; reads world state only.
 */
public class ChunkMesher {
    public static final int FLOATS_PER_VERTEX = 8;
    private static final float[] AO_MUL = {0.4f, 0.6f, 0.8f, 1.0f};
    private static final float TILE_UV = 1.0f / Tiles.ATLAS_TILES;
    private static final float TEXEL = 1.0f / Tiles.ATLAS_PX;

    public static class MeshData {
        public final int cx, sy, cz;
        public final float[] opaque, cutout, water;

        MeshData(int cx, int sy, int cz, float[] opaque, float[] cutout, float[] water) {
            this.cx = cx;
            this.sy = sy;
            this.cz = cz;
            this.opaque = opaque;
            this.cutout = cutout;
            this.water = water;
        }
    }

    public static MeshData mesh(World world, int cx, int sy, int cz) {
        FloatList opaque = new FloatList(8192);
        FloatList cutout = new FloatList(2048);
        FloatList water = new FloatList(2048);
        int bx = cx << 4, by = sy << 4, bz = cz << 4;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    byte id = world.getBlock(bx + x, by + y, bz + z);
                    if (id == Block.AIR) continue;
                    Block b = Block.get(id);
                    switch (b.layer) {
                        case SOLID -> cube(world, opaque, b, id, x, by + y, z, bx, bz, false);
                        case CUTOUT -> cube(world, cutout, b, id, x, by + y, z, bx, bz, false);
                        case WATER -> cube(world, water, b, id, x, by + y, z, bx, bz, true);
                        case CROSS -> cross(world, cutout, b, x, by + y, z, bx, bz);
                        case NONE -> {
                        }
                    }
                }
            }
        }
        return new MeshData(cx, sy, cz,
                opaque.toArray(), cutout.toArray(), water.toArray());
    }

    private static void cube(World world, FloatList out, Block b, byte id,
                             int lx, int wy, int lz, int bx, int bz, boolean isWater) {
        int wx = bx + lx, wz = bz + lz;
        boolean topOpen = isWater && world.getBlock(wx, wy + 1, wz) != Block.WATER;

        for (int f = 0; f < 6; f++) {
            int[] n = Face.NORMAL[f];
            int nx = wx + n[0], ny = wy + n[1], nz = wz + n[2];
            byte nid = world.getBlock(nx, ny, nz);
            Block nb = Block.get(nid);
            if (nb.opaqueCube) continue;
            if (nid == id && b.cullSame) continue;
            if (isWater && nid == Block.WATER) continue;

            int tile = b.tileFor(f);
            float[] ao = new float[4];
            float[] skyL = new float[4];
            float[] blkL = new float[4];
            int[][] corners = Face.CORNERS[f];
            for (int v = 0; v < 4; v++) {
                sampleVertex(world, wx, wy, wz, f, corners[v], v, ao, skyL, blkL);
            }

            // water surface sits slightly below the block top
            float topY = (isWater && topOpen) ? 0.875f : 1.0f;

            boolean flip = ao[0] + ao[2] <= ao[1] + ao[3];
            int[] order = flip ? new int[]{1, 2, 3, 3, 0, 1} : new int[]{0, 1, 2, 2, 3, 0};
            float shadeBase = Face.SHADE[f];
            for (int oi : order) {
                int[] c = corners[oi];
                float vy = c[1] == 1 ? topY : 0;
                float u = uvU(f, c);
                float vv = uvV(f, c, topY);
                float au = (tileU(tile) + 0.5f * TEXEL) + u * (TILE_UV - TEXEL);
                float av = (tileV(tile) + 0.5f * TEXEL) + vv * (TILE_UV - TEXEL);
                push(out, lx + c[0], wy + vy, lz + c[2], au, av,
                        skyL[oi], blkL[oi], shadeBase * ao[oi]);
            }
        }
    }

    /** AO + smooth light for one face vertex (3 outside-plane neighbors + face cell). */
    private static void sampleVertex(World world, int wx, int wy, int wz, int face,
                                     int[] corner, int v, float[] ao, float[] skyL, float[] blkL) {
        int[] n = Face.NORMAL[face];
        int px = wx + n[0], py = wy + n[1], pz = wz + n[2];
        // tangent axes = the two axes that aren't the normal axis
        int a1 = n[0] != 0 ? 1 : 0;                  // first non-normal axis
        int a2 = n[2] != 0 ? 1 : 2;                  // second non-normal axis
        int o1 = (axis(corner, a1) == 0) ? -1 : 1;
        int o2 = (axis(corner, a2) == 0) ? -1 : 1;

        int s1x = px + (a1 == 0 ? o1 : 0), s1y = py + (a1 == 1 ? o1 : 0), s1z = pz + (a1 == 2 ? o1 : 0);
        int s2x = px + (a2 == 0 ? o2 : 0), s2y = py + (a2 == 1 ? o2 : 0), s2z = pz + (a2 == 2 ? o2 : 0);
        int cxx = px + (a1 == 0 ? o1 : 0) + (a2 == 0 ? o2 : 0);
        int cyy = py + (a1 == 1 ? o1 : 0) + (a2 == 1 ? o2 : 0);
        int czz = pz + (a1 == 2 ? o1 : 0) + (a2 == 2 ? o2 : 0);

        boolean op1 = solidOpaque(world, s1x, s1y, s1z);
        boolean op2 = solidOpaque(world, s2x, s2y, s2z);
        boolean opc = solidOpaque(world, cxx, cyy, czz);

        int aoLevel = (op1 && op2) ? 0 : 3 - ((op1 ? 1 : 0) + (op2 ? 1 : 0) + (opc ? 1 : 0));
        ao[v] = AO_MUL[aoLevel];

        float sky = 0, blk = 0;
        int count = 0;
        if (!solidOpaque(world, px, py, pz)) {
            sky += world.getSky(px, py, pz);
            blk += world.getBlockLight(px, py, pz);
            count++;
        }
        if (!op1) {
            sky += world.getSky(s1x, s1y, s1z);
            blk += world.getBlockLight(s1x, s1y, s1z);
            count++;
        }
        if (!op2) {
            sky += world.getSky(s2x, s2y, s2z);
            blk += world.getBlockLight(s2x, s2y, s2z);
            count++;
        }
        if (!opc && !(op1 && op2)) {
            sky += world.getSky(cxx, cyy, czz);
            blk += world.getBlockLight(cxx, cyy, czz);
            count++;
        }
        if (count == 0) {
            skyL[v] = 0;
            blkL[v] = 0;
        } else {
            skyL[v] = curve(sky / count / 15f);
            blkL[v] = curve(blk / count / 15f);
        }
    }

    private static int axis(int[] corner, int a) {
        return corner[a];
    }

    private static boolean solidOpaque(World world, int x, int y, int z) {
        return Block.get(world.getBlock(x, y, z)).opaqueCube;
    }

    /** Vanilla brightness curve. */
    private static float curve(float a) {
        return a / (4f - 3f * a);
    }

    private static void cross(World world, FloatList out, Block b,
                              int lx, int wy, int lz, int bx, int bz) {
        int wx = bx + lx, wz = bz + lz;
        float sky = curve(world.getSky(wx, wy, wz) / 15f);
        float blk = curve(world.getBlockLight(wx, wy, wz) / 15f);
        int tile = b.tileTop;
        float u0 = tileU(tile) + 0.5f * TEXEL, u1 = tileU(tile) + TILE_UV - 0.5f * TEXEL;
        float v0 = tileV(tile) + 0.5f * TEXEL, v1 = tileV(tile) + TILE_UV - 0.5f * TEXEL;

        // two diagonal quads, each emitted in both windings (visible from both sides)
        float[][] quads = {
                {0.07f, 0.07f, 0.93f, 0.93f},
                {0.93f, 0.07f, 0.07f, 0.93f},
        };
        for (float[] q : quads) {
            float x0 = lx + q[0], z0 = lz + q[1], x1 = lx + q[2], z1 = lz + q[3];
            quadBothSides(out, x0, wy, z0, x1, wy + 1, z1, u0, v0, u1, v1, sky, blk);
        }
    }

    private static void quadBothSides(FloatList out, float x0, float y0, float z0,
                                      float x1, float y1, float z1,
                                      float u0, float v0, float u1, float v1,
                                      float sky, float blk) {
        // corners: bottom-left, top-left, top-right, bottom-right (texture v0 = top)
        float[][] vs = {
                {x0, y0, z0, u0, v1},
                {x0, y1, z0, u0, v0},
                {x1, y1, z1, u1, v0},
                {x1, y0, z1, u1, v1},
        };
        int[][] orders = {{0, 1, 2, 2, 3, 0}, {0, 3, 2, 2, 1, 0}};
        for (int[] order : orders) {
            for (int oi : order) {
                float[] v = vs[oi];
                push(out, v[0], v[1], v[2], v[3], v[4], sky, blk, 1.0f);
            }
        }
    }

    private static float tileU(int tile) {
        return (tile % Tiles.ATLAS_TILES) * TILE_UV;
    }

    private static float tileV(int tile) {
        return (tile / Tiles.ATLAS_TILES) * TILE_UV;
    }

    /** Texture u in [0,1] for a face corner. */
    private static float uvU(int face, int[] c) {
        return switch (face) {
            case Face.TOP, Face.BOTTOM, Face.NORTH, Face.SOUTH -> c[0];
            default -> c[2];   // east/west walls map u along z
        };
    }

    /** Texture v in [0,1]; v=0 is the visual top of the tile. */
    private static float uvV(int face, int[] c, float topY) {
        return switch (face) {
            case Face.TOP, Face.BOTTOM -> c[2];
            default -> c[1] == 1 ? 1 - topY : 1;   // walls: cube top -> texture top
        };
    }

    private static void push(FloatList out, float x, float y, float z,
                             float u, float v, float sky, float blk, float shade) {
        out.add(x);
        out.add(y);
        out.add(z);
        out.add(u);
        out.add(v);
        out.add(sky);
        out.add(blk);
        out.add(shade);
    }
}
