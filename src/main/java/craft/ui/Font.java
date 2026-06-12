package craft.ui;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/** Tiny procedural 5x7 bitmap font baked into a 128x128 texture (8x8 cells, 16 per row). */
public class Font {
    public static final int GLYPH_W = 6;   // 5px + 1 spacing
    public static final int GLYPH_H = 8;

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();
    private static final String ORDER =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-./+%()!?'<>=,_*";

    public int texture;
    private final Map<Character, Integer> index = new HashMap<>();

    private static void g(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    static {
        g('A', " 111 ", "1   1", "1   1", "11111", "1   1", "1   1", "1   1");
        g('B', "1111 ", "1   1", "1111 ", "1   1", "1   1", "1   1", "1111 ");
        g('C', " 111 ", "1   1", "1    ", "1    ", "1    ", "1   1", " 111 ");
        g('D', "1111 ", "1   1", "1   1", "1   1", "1   1", "1   1", "1111 ");
        g('E', "11111", "1    ", "1111 ", "1    ", "1    ", "1    ", "11111");
        g('F', "11111", "1    ", "1111 ", "1    ", "1    ", "1    ", "1    ");
        g('G', " 111 ", "1   1", "1    ", "1 111", "1   1", "1   1", " 111 ");
        g('H', "1   1", "1   1", "11111", "1   1", "1   1", "1   1", "1   1");
        g('I', "11111", "  1  ", "  1  ", "  1  ", "  1  ", "  1  ", "11111");
        g('J', "11111", "   1 ", "   1 ", "   1 ", "   1 ", "1  1 ", " 11  ");
        g('K', "1   1", "1  1 ", "111  ", "1 1  ", "1  1 ", "1   1", "1   1");
        g('L', "1    ", "1    ", "1    ", "1    ", "1    ", "1    ", "11111");
        g('M', "1   1", "11 11", "1 1 1", "1 1 1", "1   1", "1   1", "1   1");
        g('N', "1   1", "11  1", "1 1 1", "1  11", "1   1", "1   1", "1   1");
        g('O', " 111 ", "1   1", "1   1", "1   1", "1   1", "1   1", " 111 ");
        g('P', "1111 ", "1   1", "1   1", "1111 ", "1    ", "1    ", "1    ");
        g('Q', " 111 ", "1   1", "1   1", "1   1", "1 1 1", "1  1 ", " 11 1");
        g('R', "1111 ", "1   1", "1   1", "1111 ", "1 1  ", "1  1 ", "1   1");
        g('S', " 1111", "1    ", "1    ", " 111 ", "    1", "    1", "1111 ");
        g('T', "11111", "  1  ", "  1  ", "  1  ", "  1  ", "  1  ", "  1  ");
        g('U', "1   1", "1   1", "1   1", "1   1", "1   1", "1   1", " 111 ");
        g('V', "1   1", "1   1", "1   1", "1   1", "1   1", " 1 1 ", "  1  ");
        g('W', "1   1", "1   1", "1   1", "1 1 1", "1 1 1", "11 11", "1   1");
        g('X', "1   1", "1   1", " 1 1 ", "  1  ", " 1 1 ", "1   1", "1   1");
        g('Y', "1   1", "1   1", " 1 1 ", "  1  ", "  1  ", "  1  ", "  1  ");
        g('Z', "11111", "    1", "   1 ", "  1  ", " 1   ", "1    ", "11111");
        g('0', " 111 ", "1   1", "1  11", "1 1 1", "11  1", "1   1", " 111 ");
        g('1', "  1  ", " 11  ", "  1  ", "  1  ", "  1  ", "  1  ", "11111");
        g('2', " 111 ", "1   1", "    1", "   1 ", "  1  ", " 1   ", "11111");
        g('3', " 111 ", "1   1", "    1", "  11 ", "    1", "1   1", " 111 ");
        g('4', "   11", "  1 1", " 1  1", "1   1", "11111", "    1", "    1");
        g('5', "11111", "1    ", "1111 ", "    1", "    1", "1   1", " 111 ");
        g('6', " 111 ", "1    ", "1    ", "1111 ", "1   1", "1   1", " 111 ");
        g('7', "11111", "    1", "   1 ", "  1  ", " 1   ", " 1   ", " 1   ");
        g('8', " 111 ", "1   1", "1   1", " 111 ", "1   1", "1   1", " 111 ");
        g('9', " 111 ", "1   1", "1   1", " 1111", "    1", "    1", " 111 ");
        g(':', "     ", "  1  ", "  1  ", "     ", "  1  ", "  1  ", "     ");
        g('-', "     ", "     ", "     ", "11111", "     ", "     ", "     ");
        g('.', "     ", "     ", "     ", "     ", "     ", " 11  ", " 11  ");
        g('/', "    1", "    1", "   1 ", "  1  ", " 1   ", "1    ", "1    ");
        g('+', "     ", "  1  ", "  1  ", "11111", "  1  ", "  1  ", "     ");
        g('%', "11  1", "11  1", "   1 ", "  1  ", " 1   ", "1  11", "1  11");
        g('(', "   1 ", "  1  ", " 1   ", " 1   ", " 1   ", "  1  ", "   1 ");
        g(')', " 1   ", "  1  ", "   1 ", "   1 ", "   1 ", "  1  ", " 1   ");
        g('!', "  1  ", "  1  ", "  1  ", "  1  ", "  1  ", "     ", "  1  ");
        g('?', " 111 ", "1   1", "    1", "   1 ", "  1  ", "     ", "  1  ");
        g('\'', "  1  ", "  1  ", " 1   ", "     ", "     ", "     ", "     ");
        g('<', "   1 ", "  1  ", " 1   ", "1    ", " 1   ", "  1  ", "   1 ");
        g('>', " 1   ", "  1  ", "   1 ", "    1", "   1 ", "  1  ", " 1   ");
        g('=', "     ", "     ", "11111", "     ", "11111", "     ", "     ");
        g(',', "     ", "     ", "     ", "     ", " 11  ", " 11  ", " 1   ");
        g('_', "     ", "     ", "     ", "     ", "     ", "     ", "11111");
        g('*', "     ", "1 1 1", " 111 ", "11111", " 111 ", "1 1 1", "     ");
    }

    public Font() {
        int size = 128;
        byte[] img = new byte[size * size * 4];
        for (int i = 0; i < ORDER.length(); i++) {
            char c = ORDER.charAt(i);
            index.put(c, i);
            String[] rows = GLYPHS.get(c);
            if (rows == null) continue;
            int cellX = (i % 16) * 8, cellY = (i / 16) * 8;
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if (rows[y].charAt(x) == '1') {
                        int p = ((cellY + y) * size + cellX + x) * 4;
                        img[p] = (byte) 255;
                        img[p + 1] = (byte) 255;
                        img[p + 2] = (byte) 255;
                        img[p + 3] = (byte) 255;
                    }
                }
            }
        }
        ByteBuffer buf = MemoryUtil.memAlloc(img.length);
        buf.put(img).flip();
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        MemoryUtil.memFree(buf);
    }

    /** Cell index for char (uppercased); -1 if missing. */
    public int glyph(char c) {
        Integer i = index.get(Character.toUpperCase(c));
        return i == null ? -1 : i;
    }

    public static int width(String s) {
        return s.length() * GLYPH_W;
    }
}
