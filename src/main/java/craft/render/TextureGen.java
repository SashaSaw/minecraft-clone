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
        torch();
        craftingTable();
        furnace();
        wool();
        cracks();
        icons();
        mobSkins();
        playerSkin();
        armSkin();
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

    private void clearTile(int tile) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(tile, x, y, 0, 0);
    }

    private void torch() {
        Random r = rng(Tiles.TORCH);
        clearTile(Tiles.TORCH);
        for (int y = 6; y <= 15; y++) {
            px(Tiles.TORCH, 7, y, mul(0x6B5232, 0.92 + r.nextDouble() * 0.16));
            px(Tiles.TORCH, 8, y, mul(0x5A4527, 0.92 + r.nextDouble() * 0.16));
        }
        px(Tiles.TORCH, 7, 5, 0xFFD800);
        px(Tiles.TORCH, 8, 5, 0xFFD800);
        px(Tiles.TORCH, 7, 4, 0xFFF0A0);
        px(Tiles.TORCH, 8, 4, 0xFFF0A0);
        px(Tiles.TORCH, 7, 3, 0xFFFFD0, 200);
        px(Tiles.TORCH, 8, 3, 0xFFFFD0, 200);
    }

    private void craftingTable() {
        Random r = rng(Tiles.CRAFTING_TOP);
        // top: planks with a dark work-grid frame
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean frame = x == 0 || x == 15 || y == 0 || y == 15;
                boolean grid = (x == 7 || x == 8) && y > 1 && y < 14 || (y == 7 || y == 8) && x > 1 && x < 14;
                int c = frame ? 0x6E5126 : (grid ? 0x8A6C38 : 0xB8945F);
                px(Tiles.CRAFTING_TOP, x, y, mul(c, 0.94 + r.nextDouble() * 0.1));
            }
        }
        // side: planks with darker panel + "tools"
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean panel = x >= 3 && x <= 12 && y >= 4 && y <= 11;
                int c = panel ? 0x9A7B45 : 0xB8945F;
                if (y == 0) c = 0x8A6C38;
                px(Tiles.CRAFTING_SIDE, x, y, mul(c, 0.93 + r.nextDouble() * 0.12));
            }
        }
        for (int i = 0; i < 4; i++) {   // crossed tool silhouettes on the side panel
            px(Tiles.CRAFTING_SIDE, 5 + i, 5 + i, 0x55401E);
            px(Tiles.CRAFTING_SIDE, 10 - i, 5 + i, 0x55401E);
        }
    }

    private void furnace() {
        // top: smooth stone
        Random r = rng(Tiles.FURNACE_TOP);
        noiseFill(Tiles.FURNACE_TOP, 0x8A8A8A, 0.05, r);
        for (int i = 0; i < 16; i++) {
            px(Tiles.FURNACE_TOP, i, 0, 0x6E6E6E);
            px(Tiles.FURNACE_TOP, i, 15, 0x6E6E6E);
            px(Tiles.FURNACE_TOP, 0, i, 0x6E6E6E);
            px(Tiles.FURNACE_TOP, 15, i, 0x6E6E6E);
        }
        drawFurnaceFront(Tiles.FURNACE_FRONT, false);
        drawFurnaceFront(Tiles.FURNACE_FRONT_LIT, true);
    }

    private void drawFurnaceFront(int tile, boolean lit) {
        Random r = rng(tile);
        noiseFill(tile, 0x7E7E7E, 0.06, r);
        for (int i = 0; i < 16; i++) {
            px(tile, i, 15, 0x5E5E5E);
            px(tile, 0, i, 0x6A6A6A);
            px(tile, 15, i, 0x6A6A6A);
        }
        for (int y = 9; y <= 14; y++) {       // opening
            for (int x = 4; x <= 11; x++) {
                if (lit) {
                    int c = y >= 13 ? 0xFFC820 : (y >= 11 ? 0xE87818 : 0x281410);
                    px(tile, x, y, mul(c, 0.9 + r.nextDouble() * 0.2));
                } else {
                    px(tile, x, y, mul(0x1E1E1E, 0.8 + r.nextDouble() * 0.4));
                }
            }
        }
    }

    private void wool() {
        Random r = rng(Tiles.WOOL_WHITE);
        noiseFill(Tiles.WOOL_WHITE, 0xE8E3DC, 0.06, r);
    }

    private void cracks() {
        for (int s = 0; s < 10; s++) {
            int tile = Tiles.CRACK_0 + s;
            clearTile(tile);
            Random r = new Random(991 + s * 13);
            int segments = 3 + s * 2;
            for (int i = 0; i < segments; i++) {
                int x = r.nextInt(16), y = r.nextInt(16);
                int len = 3 + r.nextInt(5 + s);
                for (int j = 0; j < len; j++) {
                    if (x >= 0 && x < 16 && y >= 0 && y < 16) px(tile, x, y, 0x141414, 170);
                    x += r.nextInt(3) - 1;
                    y += r.nextInt(3) - 1;
                }
            }
        }
    }

    // ---------- item icons ----------

    private void icons() {
        stick(Tiles.ICON_STICK);
        blob(Tiles.ICON_COAL, 0x2A2A2A, 0x161616);
        blob(Tiles.ICON_CHARCOAL, 0x3A2A1A, 0x1E140C);
        ingot(Tiles.ICON_IRON_INGOT, 0xD8D8D8, 0xA8A8A8);
        blob(Tiles.ICON_RAW_IRON, 0xD8AF93, 0xB58A6C);
        apple(Tiles.ICON_APPLE);
        toolSet(Tiles.ICON_WOOD_PICK, 0xA8824E);
        toolSet(Tiles.ICON_STONE_PICK, 0x8A8A8A);
        toolSet(Tiles.ICON_IRON_PICK, 0xD8D8D8);
        food(Tiles.ICON_PORKCHOP, 0xF0A0A0, 0xE8E0D8);
        food(Tiles.ICON_COOKED_PORKCHOP, 0xC08050, 0xE8E0D8);
        food(Tiles.ICON_BEEF, 0xB04030, 0xE0C8B8);
        food(Tiles.ICON_COOKED_BEEF, 0x6E4226, 0xE0C8B8);
        food(Tiles.ICON_CHICKEN, 0xE8C8B0, 0xF0E8E0);
        food(Tiles.ICON_COOKED_CHICKEN, 0xC8853C, 0xF0E8E0);
        food(Tiles.ICON_MUTTON, 0xC05040, 0xE0C8B8);
        food(Tiles.ICON_COOKED_MUTTON, 0x8A5230, 0xE0C8B8);
        blob(Tiles.ICON_ROTTEN_FLESH, 0x7A5A3A, 0x4E6E2E);
        feather(Tiles.ICON_FEATHER);
        leather(Tiles.ICON_LEATHER);
        woolIcon(Tiles.ICON_WOOL);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px(Tiles.WHITE, x, y, 0xFFFFFF);
        hudIcons();
    }

    private static final String[] HEART_ART = {
            "  XX   XX  ",
            " XXXX XXXX ",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            " XXXXXXXXX ",
            "  XXXXXXX  ",
            "   XXXXX   ",
            "    XXX    ",
            "     X     ",
    };

    private void heart(int tile, int fillColor, boolean halfOnly) {
        clearTile(tile);
        for (int y = 0; y < HEART_ART.length; y++) {
            for (int x = 0; x < HEART_ART[y].length(); x++) {
                if (HEART_ART[y].charAt(x) != 'X') continue;
                boolean edge = isEdge(HEART_ART, x, y);
                int c = edge ? 0x1A0505 : fillColor;
                if (halfOnly && x > 5 && !edge) c = 0x3A3A3A;
                px(tile, 2 + x, 3 + y, c);
            }
        }
        if (!halfOnly && fillColor != 0x3A3A3A) {
            px(tile, 4, 5, mul(fillColor, 1.6));   // shine
            px(tile, 5, 5, mul(fillColor, 1.6));
        }
    }

    private static boolean isEdge(String[] art, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = y + dy, nx = x + dx;
                if (ny < 0 || ny >= art.length || nx < 0 || nx >= art[ny].length()
                        || art[ny].charAt(nx) != 'X') {
                    return true;
                }
            }
        }
        return false;
    }

    private void drumstick(int tile, int meatColor) {
        clearTile(tile);
        Random r = rng(tile);
        for (int y = 3; y <= 9; y++) {        // meat blob upper-right
            for (int x = 6; x <= 12; x++) {
                if (Math.hypot(x - 9, y - 6) < 3.4) {
                    px(tile, x, y, mul(meatColor, 0.9 + r.nextDouble() * 0.2));
                }
            }
        }
        px(tile, 5, 10, 0xEDE5D8);            // bone
        px(tile, 4, 11, 0xEDE5D8);
        px(tile, 3, 12, 0xEDE5D8);
        px(tile, 3, 13, 0xEDE5D8);
        px(tile, 4, 13, 0xEDE5D8);
    }

    private void hudIcons() {
        heart(Tiles.ICON_HEART, 0xE02020, false);
        heart(Tiles.ICON_HEART_HALF, 0xE02020, true);
        heart(Tiles.ICON_HEART_EMPTY, 0x3A3A3A, false);
        drumstick(Tiles.ICON_HUNGER, 0xB5651E);
        drumstick(Tiles.ICON_HUNGER_HALF, 0x6E4214);
        drumstick(Tiles.ICON_HUNGER_EMPTY, 0x3A3A3A);
        // bubble
        clearTile(Tiles.ICON_BUBBLE);
        Random r = rng(Tiles.ICON_BUBBLE);
        for (int y = 3; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d < 4.6 && d > 3.2) px(Tiles.ICON_BUBBLE, x, y, 0xC8E8F8);
                else if (d <= 3.2) px(Tiles.ICON_BUBBLE, x, y, 0x88B8E8, 120);
            }
        }
    }

    private void stick(int tile) {
        clearTile(tile);
        for (int i = 0; i < 9; i++) {
            px(tile, 4 + i, 12 - i, 0x6B5232);
            px(tile, 5 + i, 12 - i, 0x8A6C40);
        }
    }

    private void blob(int tile, int main, int dark) {
        Random r = rng(tile);
        clearTile(tile);
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 12; x++) {
                double d = Math.hypot(x - 8, y - 8);
                if (d < 4.2 + r.nextDouble()) {
                    px(tile, x, y, mul(r.nextInt(4) == 0 ? dark : main, 0.9 + r.nextDouble() * 0.2));
                }
            }
        }
    }

    private void ingot(int tile, int main, int shadow) {
        clearTile(tile);
        for (int y = 6; y <= 11; y++) {
            int inset = (11 - y);
            for (int x = 2 + inset / 2; x <= 13 - inset / 2; x++) {
                px(tile, x, y, y <= 7 ? mul(main, 1.1) : (y >= 10 ? shadow : main));
            }
        }
    }

    private void apple(int tile) {
        Random r = rng(tile);
        clearTile(tile);
        for (int y = 5; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) {
                double d = Math.hypot(x - 7.5, y - 8.5);
                if (d < 4.0) px(tile, x, y, mul(0xD02A1A, 0.9 + r.nextDouble() * 0.2));
            }
        }
        px(tile, 7, 4, 0x6B5232);
        px(tile, 8, 3, 0x4A8A2A);
        px(tile, 9, 3, 0x4A8A2A);
        px(tile, 5, 6, 0xF08070);   // shine
        px(tile, 6, 6, 0xF08070);
    }

    /** Draws pickaxe, axe, shovel, sword for one material (tiles are consecutive). */
    private void toolSet(int pickTile, int mat) {
        int handle = 0x8A6C40, handleDark = 0x6B5232;
        // pickaxe
        clearTile(pickTile);
        for (int i = 0; i < 9; i++) px(pickTile, 3 + i, 12 - i, i % 2 == 0 ? handle : handleDark);
        for (int i = 0; i < 9; i++) {
            int x = 4 + i;
            int y = 3 + (i < 3 ? (2 - i) : (i > 5 ? i - 5 : 0));
            px(pickTile, x, y, mat);
            px(pickTile, x, y + 1, mul(mat, 0.8));
        }
        // axe
        int t = pickTile + 1;
        clearTile(t);
        for (int i = 0; i < 9; i++) px(t, 3 + i, 12 - i, i % 2 == 0 ? handle : handleDark);
        for (int y = 2; y <= 6; y++) {
            for (int x = 6; x <= 10; x++) {
                if (x + y <= 14 && y - x <= -2) px(t, x, y, mul(mat, x > 8 ? 1.0 : 0.85));
            }
        }
        // shovel
        t = pickTile + 2;
        clearTile(t);
        for (int i = 0; i < 9; i++) px(t, 3 + i, 12 - i, i % 2 == 0 ? handle : handleDark);
        for (int y = 2; y <= 5; y++) {
            for (int x = 9; x <= 12; x++) {
                if (Math.abs((x - 10.5) + (y - 3.5)) < 2.5) px(t, x, y, mat);
            }
        }
        // sword
        t = pickTile + 3;
        clearTile(t);
        for (int i = 0; i < 9; i++) {
            px(t, 5 + i, 10 - i, mat);
            px(t, 6 + i, 10 - i, mul(mat, 0.8));
        }
        px(t, 4, 11, handleDark);   // guard
        px(t, 5, 12, handleDark);
        px(t, 6, 11, handleDark);
        px(t, 3, 13, handle);       // grip
        px(t, 2, 14, handle);
    }

    private void food(int tile, int meat, int bone) {
        Random r = rng(tile);
        clearTile(tile);
        for (int y = 4; y <= 11; y++) {
            for (int x = 5; x <= 12; x++) {
                double d = Math.hypot((x - 8.5) * 0.9, y - 7.5);
                if (d < 3.8) px(tile, x, y, mul(meat, 0.88 + r.nextDouble() * 0.24));
            }
        }
        px(tile, 4, 12, bone);   // bone / rind nub
        px(tile, 3, 13, bone);
        px(tile, 4, 13, bone);
    }

    private void feather(int tile) {
        clearTile(tile);
        for (int i = 0; i < 8; i++) {
            px(tile, 4 + i, 11 - i, 0xEDEDED);
            px(tile, 5 + i, 11 - i, 0xDADADA);
            if (i < 7) px(tile, 4 + i, 10 - i, 0xF8F8F8);
        }
        for (int i = 0; i < 3; i++) px(tile, 3 + i, 13 - i, 0xB0A890);   // quill
    }

    private void leather(int tile) {
        Random r = rng(tile);
        clearTile(tile);
        for (int y = 4; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                if ((x + y) % 9 != 0) px(tile, x, y, mul(0xB5824E, 0.9 + r.nextDouble() * 0.2));
            }
        }
    }

    private void woolIcon(int tile) {
        Random r = rng(tile);
        clearTile(tile);
        for (int y = 3; y <= 12; y++) {
            for (int x = 3; x <= 12; x++) {
                px(tile, x, y, mul(0xE8E3DC, 0.9 + r.nextDouble() * 0.18));
            }
        }
    }

    // ---------- mob skins ----------

    private void mobSkins() {
        // zombie
        noiseFill(Tiles.ZOMBIE_SKIN, 0x5B8731, 0.10, rng(Tiles.ZOMBIE_SKIN));
        noiseFill(Tiles.ZOMBIE_SHIRT, 0x00A8A8, 0.08, rng(Tiles.ZOMBIE_SHIRT));
        noiseFill(Tiles.ZOMBIE_PANTS, 0x34345E, 0.10, rng(Tiles.ZOMBIE_PANTS));
        noiseFill(Tiles.ZOMBIE_FACE, 0x5B8731, 0.10, rng(Tiles.ZOMBIE_FACE));
        eyes(Tiles.ZOMBIE_FACE, 0x101418, 0x101418);
        for (int x = 6; x <= 9; x++) px(Tiles.ZOMBIE_FACE, x, 11, 0x2A4A18);   // grim mouth

        // pig
        noiseFill(Tiles.PIG_SKIN, 0xF0A5A2, 0.06, rng(Tiles.PIG_SKIN));
        noiseFill(Tiles.PIG_FACE, 0xF0A5A2, 0.06, rng(Tiles.PIG_FACE));
        eyes(Tiles.PIG_FACE, 0xFFFFFF, 0x101418);
        for (int y = 8; y <= 11; y++) {                                        // snout
            for (int x = 5; x <= 10; x++) px(Tiles.PIG_FACE, x, y, 0xD8847E);
        }
        px(Tiles.PIG_FACE, 6, 9, 0x6E3A36);
        px(Tiles.PIG_FACE, 6, 10, 0x6E3A36);
        px(Tiles.PIG_FACE, 9, 9, 0x6E3A36);
        px(Tiles.PIG_FACE, 9, 10, 0x6E3A36);

        // cow: brown with white patches
        Random r = rng(Tiles.COW_BODY);
        noiseFill(Tiles.COW_BODY, 0x43342B, 0.10, r);
        for (int i = 0; i < 3; i++) {
            int bx = r.nextInt(10), by = r.nextInt(10);
            for (int y = by; y < by + 4 + r.nextInt(3); y++) {
                for (int x = bx; x < bx + 4 + r.nextInt(3); x++) {
                    if (x < 16 && y < 16) px(Tiles.COW_BODY, x, y, mul(0xE8E3DC, 0.92 + r.nextDouble() * 0.12));
                }
            }
        }
        noiseFill(Tiles.COW_FACE, 0x43342B, 0.10, rng(Tiles.COW_FACE));
        for (int y = 7; y < 16; y++) {                                          // white blaze
            for (int x = 6; x <= 9; x++) px(Tiles.COW_FACE, x, y, 0xE8E3DC);
        }
        eyes(Tiles.COW_FACE, 0xFFFFFF, 0x101418);
        px(Tiles.COW_FACE, 5, 13, 0xD8A0A8);                                    // nose
        px(Tiles.COW_FACE, 10, 13, 0xD8A0A8);
        noiseFill(Tiles.COW_LEG, 0x3A2E26, 0.08, rng(Tiles.COW_LEG));

        // sheep
        Random rs = rng(Tiles.SHEEP_WOOL);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double f = 0.88 + rs.nextDouble() * 0.18;
                if ((x + y * 3) % 7 == 0) f *= 0.93;   // curls
                px(Tiles.SHEEP_WOOL, x, y, mul(0xE8E3DC, f));
            }
        }
        noiseFill(Tiles.SHEEP_FACE, 0xB5917A, 0.08, rng(Tiles.SHEEP_FACE));
        eyes(Tiles.SHEEP_FACE, 0xFFFFFF, 0x101418);
        px(Tiles.SHEEP_FACE, 7, 12, 0x8A6E5A);
        px(Tiles.SHEEP_FACE, 8, 12, 0x8A6E5A);
        noiseFill(Tiles.SHEEP_LEG, 0xB5917A, 0.08, rng(Tiles.SHEEP_LEG));

        // chicken
        noiseFill(Tiles.CHICKEN_BODY, 0xE8E8E8, 0.07, rng(Tiles.CHICKEN_BODY));
        noiseFill(Tiles.CHICKEN_FACE, 0xE8E8E8, 0.07, rng(Tiles.CHICKEN_FACE));
        px(Tiles.CHICKEN_FACE, 4, 6, 0x101418);
        px(Tiles.CHICKEN_FACE, 11, 6, 0x101418);
        noiseFill(Tiles.YELLOW, 0xF2C14E, 0.08, rng(Tiles.YELLOW));
        noiseFill(Tiles.RED, 0xB02020, 0.08, rng(Tiles.RED));
    }

    /** Steve-style player skin: skin tone, teal shirt, blue trousers, simple face. */
    private void playerSkin() {
        noiseFill(Tiles.PLAYER_SKIN, 0xC8967A, 0.06, rng(Tiles.PLAYER_SKIN));
        noiseFill(Tiles.PLAYER_SHIRT, 0x2E9A9A, 0.07, rng(Tiles.PLAYER_SHIRT));
        noiseFill(Tiles.PLAYER_PANTS, 0x3A4A8A, 0.08, rng(Tiles.PLAYER_PANTS));
        noiseFill(Tiles.PLAYER_FACE, 0xC8967A, 0.06, rng(Tiles.PLAYER_FACE));
        // hair fringe across the top
        for (int y = 0; y < 4; y++) for (int x = 0; x < 16; x++) px(Tiles.PLAYER_FACE, x, y, 0x4A3520);
        eyes(Tiles.PLAYER_FACE, 0xFFFFFF, 0x3A2C6E);
        for (int x = 6; x <= 9; x++) px(Tiles.PLAYER_FACE, x, 12, 0x8A5E48);   // mouth
    }

    /**
     * First-person forearm. Side faces: skin with the cyan sleeve cuff on the BOTTOM
     * rows, which the box() UV maps to the shoulder (far) end so the visible fist end
     * stays skin. End caps (ARM_HAND): solid skin so the fist is never sleeve-coloured.
     */
    private void armSkin() {
        Random r = rng(Tiles.ARM_SKIN);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = y >= 12 ? 0x00A8A8 : 0xE0A070;   // bottom 4 rows = sleeve cuff
                double f = 0.92 + r.nextDouble() * 0.14;
                px(Tiles.ARM_SKIN, x, y, mul(c, f));
            }
        }
        Random rh = rng(Tiles.ARM_HAND);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                px(Tiles.ARM_HAND, x, y, mul(0xE0A070, 0.92 + rh.nextDouble() * 0.14));
            }
        }
    }

    private void eyes(int tile, int white, int pupil) {
        px(tile, 3, 6, white);
        px(tile, 4, 6, pupil);
        px(tile, 11, 6, pupil);
        px(tile, 12, 6, white);
        px(tile, 3, 7, white);
        px(tile, 4, 7, pupil);
        px(tile, 11, 7, pupil);
        px(tile, 12, 7, white);
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
