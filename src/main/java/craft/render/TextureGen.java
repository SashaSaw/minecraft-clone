package craft.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Procedurally draws all block textures (original, Minecraft-style 16x16 pixel art)
 * into a 256x256 RGBA atlas at startup, plus sun/moon textures. No image assets.
 */
public class TextureGen {
    private final byte[] atlas = new byte[Tiles.ATLAS_PX * Tiles.ATLAS_PX * 4];

    public int atlasTex, sunTex, moonTex;

    public void buildAll() {
        grassTop();
        grassSide();
        dirt();
        stone(Tiles.STONE);
        cobble();
        sand();
        gravel();
        bedrock();
        logSide();
        logTop();
        planks();
        leaves();
        water();
        glassTile();
        ore(Tiles.COAL_ORE, 0x2A2A2A, 0x161616);
        ore(Tiles.IRON_ORE, 0xD8AF93, 0xB58A6C);
        tallGrass();
        flower(Tiles.DANDELION, 0xF0C832, 0xFFE890);
        flower(Tiles.POPPY, 0xC02A18, 0x3A1208);
        sugarCane();
        atlasTex = upload(atlas, Tiles.ATLAS_PX, Tiles.ATLAS_PX);
        sunTex = celestial(0xFFF0A0, 24);
        moonTex = celestial(0xD8D8C8, 18);
    }

    // ---------- atlas pixel helpers ----------

    private void px(int tile, int x, int y, int rgb, int a) {
        int ax = (tile % Tiles.ATLAS_TILES) * Tiles.TILE_PX + x;
        int ay = (tile / Tiles.ATLAS_TILES) * Tiles.TILE_PX + y;
        int i = (ay * Tiles.ATLAS_PX + ax) * 4;
        atlas[i] = (byte) ((rgb >> 16) & 0xFF);
        atlas[i + 1] = (byte) ((rgb >> 8) & 0xFF);
        atlas[i + 2] = (byte) (rgb & 0xFF);
        atlas[i + 3] = (byte) a;
    }

    private void px(int tile, int x, int y, int rgb) {
        px(tile, x, y, rgb, 255);
    }

    private static int mul(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private Random rng(int tile) {
        return new Random(0xC0FFEE ^ (tile * 7919L));
    }

    private void noiseFill(int tile, int base, double var, Random r) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                px(tile, x, y, mul(base, 1 + (r.nextDouble() * 2 - 1) * var));
            }
        }
    }

    // ---------- block tiles ----------

    private void grassTop() {
        Random r = rng(Tiles.GRASS_TOP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double f = 0.92 + r.nextDouble() * 0.16;
                if (r.nextInt(8) == 0) f *= 0.86;
                px(Tiles.GRASS_TOP, x, y, mul(0x7CBD45, f));
            }
        }
    }

    private void dirt() {
        Random r = rng(Tiles.DIRT);
        drawDirt(Tiles.DIRT, 0, 16, r);
    }

    private void drawDirt(int tile, int yFrom, int yTo, Random r) {
        for (int y = yFrom; y < yTo; y++) {
            for (int x = 0; x < 16; x++) {
                double f = 0.9 + r.nextDouble() * 0.2;
                int c = 0x866043;
                int roll = r.nextInt(10);
                if (roll == 0) c = 0x6B4A33;
                else if (roll == 1) c = 0x9B7653;
                px(tile, x, y, mul(c, f));
            }
        }
    }

    private void grassSide() {
        Random r = rng(Tiles.GRASS_SIDE);
        drawDirt(Tiles.GRASS_SIDE, 0, 16, r);
        for (int x = 0; x < 16; x++) {
            int depth = 2 + r.nextInt(3);   // ragged grass overhang 2-4 px
            for (int y = 0; y < depth; y++) {
                double f = 0.88 + r.nextDouble() * 0.2;
                px(Tiles.GRASS_SIDE, x, y, mul(y == depth - 1 ? 0x6BA63C : 0x7CBD45, f));
            }
        }
    }

    private void stone(int tile) {
        Random r = rng(tile);
        noiseFill(tile, 0x7E7E7E, 0.06, r);
        for (int i = 0; i < 9; i++) {   // subtle darker blotches
            int bx = r.nextInt(13), by = r.nextInt(13);
            int w = 2 + r.nextInt(3), h = 1 + r.nextInt(2);
            for (int y = by; y < by + h; y++) {
                for (int x = bx; x < bx + w; x++) {
                    px(tile, x, y, mul(0x747474, 0.95 + r.nextDouble() * 0.08));
                }
            }
        }
    }

    private void cobble() {
        Random r = rng(Tiles.COBBLE);
        int n = 6;
        int[] cxs = new int[n], cys = new int[n];
        double[] bright = new double[n];
        for (int i = 0; i < n; i++) {
            cxs[i] = r.nextInt(16);
            cys[i] = r.nextInt(16);
            bright[i] = 0.78 + r.nextDouble() * 0.32;
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d1 = 1e9, d2 = 1e9;
                int best = 0;
                for (int i = 0; i < n; i++) {
                    // toroidal distance so the tile wraps seamlessly
                    int dx = Math.min(Math.abs(x - cxs[i]), 16 - Math.abs(x - cxs[i]));
                    int dy = Math.min(Math.abs(y - cys[i]), 16 - Math.abs(y - cys[i]));
                    double d = dx * dx + dy * dy;
                    if (d < d1) {
                        d2 = d1;
                        d1 = d;
                        best = i;
                    } else if (d < d2) {
                        d2 = d;
                    }
                }
                if (Math.sqrt(d2) - Math.sqrt(d1) < 1.0) {
                    px(Tiles.COBBLE, x, y, mul(0x4A4A4A, 0.9 + r.nextDouble() * 0.2));
                } else {
                    px(Tiles.COBBLE, x, y, mul(0x8A8A8A, bright[best] * (0.95 + r.nextDouble() * 0.1)));
                }
            }
        }
    }

    private void sand() {
        noiseFill(Tiles.SAND, 0xDBCFA3, 0.07, rng(Tiles.SAND));
    }

    private void gravel() {
        Random r = rng(Tiles.GRAVEL);
        int[] palette = {0x7F7F7F, 0x9B9B9B, 0x6A5F5B, 0x8A7E76, 0x565656, 0x74808A};
        for (int y = 0; y < 16; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                int c = palette[r.nextInt(palette.length)];
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        px(Tiles.GRAVEL, x + dx, y + dy, mul(c, 0.92 + r.nextDouble() * 0.16));
                    }
                }
            }
        }
    }

    private void bedrock() {
        Random r = rng(Tiles.BEDROCK);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double roll = r.nextDouble();
                int c = roll < 0.3 ? 0x333333 : (roll > 0.82 ? 0x8F8F8F : 0x565656);
                px(Tiles.BEDROCK, x, y, mul(c, 0.9 + r.nextDouble() * 0.2));
            }
        }
    }

    private void logSide() {
        Random r = rng(Tiles.LOG_SIDE);
        int[] barks = {0x6B5232, 0x5A4527, 0x745A36, 0x64502F};
        for (int x = 0; x < 16; x++) {
            int c = barks[r.nextInt(barks.length)];
            for (int y = 0; y < 16; y++) {
                double f = 0.92 + r.nextDouble() * 0.16;
                if (r.nextInt(14) == 0) f *= 0.75;   // bark pits
                px(Tiles.LOG_SIDE, x, y, mul(c, f));
            }
        }
    }

    private void logTop() {
        Random r = rng(Tiles.LOG_TOP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                int c;
                if (d > 6) c = 0x6B5232;                       // bark rim
                else c = ((int) d % 2 == 0) ? 0xB8945F : 0xA5824C;  // rings
                px(Tiles.LOG_TOP, x, y, mul(c, 0.94 + r.nextDouble() * 0.12));
            }
        }
    }

    private void planks() {
        Random r = rng(Tiles.PLANKS);
        int[] jointX = new int[4];
        for (int i = 0; i < 4; i++) jointX[i] = r.nextInt(16);
        for (int y = 0; y < 16; y++) {
            int board = y / 4;
            boolean seam = (y & 3) == 3;
            for (int x = 0; x < 16; x++) {
                int c = seam || x == jointX[board] ? 0x7E6437 : 0xB8945F;
                double f = 0.93 + r.nextDouble() * 0.12;
                if (!seam && r.nextInt(6) == 0) f *= 0.94;   // grain
                px(Tiles.PLANKS, x, y, mul(c, f));
            }
        }
    }

    private void leaves() {
        Random r = rng(Tiles.LEAVES);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (r.nextDouble() < 0.18) {
                    px(Tiles.LEAVES, x, y, 0, 0);   // hole
                } else {
                    int c = r.nextInt(5) == 0 ? 0x5BA838 : 0x478A2B;
                    px(Tiles.LEAVES, x, y, mul(c, 0.85 + r.nextDouble() * 0.3));
                }
            }
        }
    }

    private void water() {
        Random r = rng(Tiles.WATER);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double f = 0.95 + r.nextDouble() * 0.1;
                if ((y + x / 5) % 5 == 0) f *= 1.1;   // faint wave bands
                px(Tiles.WATER, x, y, mul(0x3F76E4, f), 192);
            }
        }
    }

    private void glassTile() {
        Random r = rng(Tiles.GLASS);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean border = x == 0 || x == 15 || y == 0 || y == 15;
                if (border) px(Tiles.GLASS, x, y, 0xDCF0F2);
                else px(Tiles.GLASS, x, y, 0, 0);
            }
        }
        // diagonal sheen streaks
        for (int i = 0; i < 5; i++) px(Tiles.GLASS, 10 - i, 2 + i, 0xC7E4E8, 220);
        for (int i = 0; i < 3; i++) px(Tiles.GLASS, 13 - i, 5 + i, 0xC7E4E8, 200);
    }

    private void ore(int tile, int oreColor, int oreDark) {
        stone(tile);
        Random r = rng(tile + 50);
        for (int i = 0; i < 5; i++) {
            int bx = 1 + r.nextInt(12), by = 1 + r.nextInt(12);
            px(tile, bx, by, oreColor);
            px(tile, bx + 1, by, oreColor);
            px(tile, bx, by + 1, mul(oreColor, 0.9));
            px(tile, bx + 1, by + 1, oreDark);
            if (r.nextBoolean()) px(tile, bx + 2, by, mul(oreColor, 0.85));
        }
    }

    private void tallGrass() {
        Random r = rng(Tiles.TALL_GRASS);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(Tiles.TALL_GRASS, x, y, 0, 0);
        for (int blade = 0; blade < 9; blade++) {
            int x = 1 + r.nextInt(14);
            int h = 6 + r.nextInt(9);
            int bend = r.nextInt(3) - 1;
            for (int i = 0; i < h; i++) {
                int y = 15 - i;
                int bx = x + (i > h / 2 ? bend : 0);
                if (bx < 0 || bx > 15) continue;
                int c = i >= h - 2 ? 0x8BCC5A : 0x6BA63C;
                px(Tiles.TALL_GRASS, bx, y, mul(c, 0.9 + r.nextDouble() * 0.2));
            }
        }
    }

    private void flower(int tile, int petal, int center) {
        Random r = rng(tile);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(tile, x, y, 0, 0);
        for (int y = 7; y <= 15; y++) px(tile, 8, y, 0x3A7A1E);   // stem
        px(tile, 7, 11, 0x4A8A2A);                                 // leaf
        px(tile, 6, 11, 0x4A8A2A);
        px(tile, 9, 13, 0x4A8A2A);
        for (int dy = -1; dy <= 1; dy++) {                         // head
            for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) == 1 && Math.abs(dy) == 1 && r.nextBoolean()) continue;
                px(tile, 8 + dx, 5 + dy, petal);
            }
        }
        px(tile, 8, 5, center);
        px(tile, 7, 4, mul(petal, 1.12));
    }

    private void sugarCane() {
        Random r = rng(Tiles.SUGAR_CANE);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(Tiles.SUGAR_CANE, x, y, 0, 0);
        int[] stalks = {3, 8, 12};
        for (int sx : stalks) {
            int jointOffset = r.nextInt(4);
            for (int y = 0; y < 16; y++) {
                boolean joint = (y + jointOffset) % 5 == 0;
                int c = joint ? 0x8FAE54 : 0xA8C46A;
                px(Tiles.SUGAR_CANE, sx, y, mul(c, 0.92 + r.nextDouble() * 0.14));
                px(Tiles.SUGAR_CANE, sx + 1, y, mul(c, 0.88 + r.nextDouble() * 0.14));
            }
        }
    }

    // ---------- standalone textures ----------

    /** Soft-edged filled square for sun/moon, 32x32. */
    private int celestial(int color, int solidHalf) {
        byte[] img = new byte[32 * 32 * 4];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double d = Math.max(Math.abs(x - 15.5), Math.abs(y - 15.5));
                int a;
                if (d <= solidHalf / 2.0) a = 255;
                else a = (int) Math.max(0, 255 * (1 - (d - solidHalf / 2.0) / 4.0));
                int i = (y * 32 + x) * 4;
                img[i] = (byte) ((color >> 16) & 0xFF);
                img[i + 1] = (byte) ((color >> 8) & 0xFF);
                img[i + 2] = (byte) (color & 0xFF);
                img[i + 3] = (byte) a;
            }
        }
        return upload(img, 32, 32);
    }

    private int upload(byte[] data, int w, int h) {
        ByteBuffer buf = MemoryUtil.memAlloc(data.length);
        buf.put(data).flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        MemoryUtil.memFree(buf);
        return tex;
    }
}
