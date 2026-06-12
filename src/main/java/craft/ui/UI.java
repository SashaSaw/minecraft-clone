package craft.ui;

import craft.item.Item;
import craft.item.ItemStack;
import craft.render.Tiles;
import craft.util.FloatList;
import craft.render.Shader;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Batched 2D renderer in virtual pixels (framebuffer / GUI_SCALE).
 * Quads carry pos2, uv2, rgba — batches flush per texture.
 */
public class UI {
    public static final int SCALE = 3;

    private static final String VS = """
            #version 330 core
            layout(location=0) in vec2 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec4 aColor;
            uniform vec2 uScreen;
            out vec2 vUV;
            out vec4 vColor;
            void main() {
                vec2 ndc = vec2(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
                vUV = aUV;
                vColor = aColor;
            }
            """;

    private static final String FS = """
            #version 330 core
            in vec2 vUV;
            in vec4 vColor;
            out vec4 FragColor;
            uniform sampler2D uTex;
            uniform int uUseTex;
            void main() {
                vec4 c = uUseTex == 1 ? texture(uTex, vUV) : vec4(1.0);
                FragColor = c * vColor;
                if (FragColor.a < 0.01) discard;
            }
            """;

    private final Shader shader = new Shader(VS, FS);
    private final int vao, vbo;
    private final FloatList batch = new FloatList(4096);
    private final int atlasTex;
    public final Font font = new Font();

    public int screenW, screenH;   // virtual pixels

    public UI(int atlasTex) {
        this.atlasTex = atlasTex;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 32, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 32, 8);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 32, 16);
        for (int i = 0; i < 3; i++) glEnableVertexAttribArray(i);
    }

    public void begin(int fbWidth, int fbHeight) {
        screenW = fbWidth / SCALE;
        screenH = fbHeight / SCALE;
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.setVec2("uScreen", screenW, screenH);
        shader.setInt("uTex", 0);
    }

    public void end() {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    // ---------- primitives ----------

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        pushQuad(x, y, w, h, 0, 0, 1, 1, r, g, b, a);
        flush(0, false);
    }

    /** Draws an atlas tile. */
    public void tile(int tile, float x, float y, float w, float h) {
        tile(tile, x, y, w, h, 1, 1, 1, 1);
    }

    public void tile(int tile, float x, float y, float w, float h, float r, float g, float b, float a) {
        float s = 1f / Tiles.ATLAS_TILES;
        float u0 = (tile % Tiles.ATLAS_TILES) * s;
        float v0 = (tile / Tiles.ATLAS_TILES) * s;
        pushQuad(x, y, w, h, u0, v0, u0 + s, v0 + s, r, g, b, a);
        flush(atlasTex, true);
    }

    public void text(String s, float x, float y, float r, float g, float b) {
        textScaled(s, x, y, 1, r, g, b);
    }

    public void textScaled(String s, float x, float y, float scale, float r, float g, float b) {
        float off = Math.max(1, scale * 0.4f);
        textRaw(s, x + off, y + off, scale, 0.16f, 0.16f, 0.16f, 1);   // shadow
        textRaw(s, x, y, scale, r, g, b, 1);
    }

    public void textCentered(String s, float cx, float y, float r, float g, float b) {
        text(s, cx - Font.width(s) / 2f, y, r, g, b);
    }

    public void textCenteredScaled(String s, float cx, float y, float scale, float r, float g, float b) {
        textScaled(s, cx - Font.width(s) * scale / 2f, y, scale, r, g, b);
    }

    private void textRaw(String s, float x, float y, float scale, float r, float g, float b, float a) {
        float cell = 8f / 128f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ') {
                int gi = font.glyph(c);
                if (gi >= 0) {
                    float u0 = (gi % 16) * cell, v0 = (gi / 16) * cell;
                    pushQuad(x + i * Font.GLYPH_W * scale, y, 8 * scale, 8 * scale,
                            u0, v0, u0 + cell, v0 + cell, r, g, b, a);
                }
            }
        }
        flush(font.texture, true);
    }

    /** Item icon + count at slot position (slot is 18x18, icon 16x16). */
    public void itemStack(ItemStack stack, float x, float y) {
        if (stack == null) return;
        Item it = stack.item();
        int tile = it.isBlock() ? it.block().tileSide : it.icon;
        tile(tile, x, y, 16, 16);
        if (stack.count > 1) {
            String n = String.valueOf(stack.count);
            text(n, x + 17 - Font.width(n), y + 9, 1, 1, 1);
        }
    }

    /** Standard slot background. */
    public void slot(float x, float y) {
        rect(x - 1, y - 1, 18, 18, 0.22f, 0.22f, 0.22f, 1f);
        rect(x, y, 16, 16, 0.55f, 0.55f, 0.55f, 1f);
    }

    // ---------- batching ----------

    private void pushQuad(float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a) {
        vert(x, y, u0, v0, r, g, b, a);
        vert(x, y + h, u0, v1, r, g, b, a);
        vert(x + w, y + h, u1, v1, r, g, b, a);
        vert(x + w, y + h, u1, v1, r, g, b, a);
        vert(x + w, y, u1, v0, r, g, b, a);
        vert(x, y, u0, v0, r, g, b, a);
    }

    private void vert(float x, float y, float u, float v, float r, float g, float b, float a) {
        batch.add(x);
        batch.add(y);
        batch.add(u);
        batch.add(v);
        batch.add(r);
        batch.add(g);
        batch.add(b);
        batch.add(a);
    }

    private void flush(int tex, boolean useTex) {
        if (batch.size() == 0) return;
        shader.bind();
        shader.setInt("uUseTex", useTex ? 1 : 0);
        if (useTex) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, tex);
        }
        FloatBuffer fb = MemoryUtil.memAllocFloat(batch.size());
        fb.put(batch.raw(), 0, batch.size()).flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
        MemoryUtil.memFree(fb);
        glDrawArrays(GL_TRIANGLES, 0, batch.size() / 8);
        batch.clear();
    }
}
