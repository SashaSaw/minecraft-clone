package craft.render;

import craft.entity.Entity;
import craft.entity.ItemEntity;
import craft.item.Item;
import craft.util.FloatList;
import craft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/** Selection wireframe, crack overlay, and item-entity billboards. */
public class OverlayRenderer {
    private static final String LINE_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            uniform mat4 uPV;
            void main() { gl_Position = uPV * vec4(aPos, 1.0); }
            """;
    private static final String LINE_FS = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 uColor;
            void main() { FragColor = uColor; }
            """;

    private static final String SPRITE_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in float aLight;
            uniform mat4 uPV;
            out vec2 vUV;
            out float vLight;
            void main() {
                gl_Position = uPV * vec4(aPos, 1.0);
                vUV = aUV;
                vLight = aLight;
            }
            """;
    private static final String SPRITE_FS = """
            #version 330 core
            in vec2 vUV;
            in float vLight;
            out vec4 FragColor;
            uniform sampler2D uTex;
            uniform float uAlpha;
            void main() {
                vec4 c = texture(uTex, vUV);
                if (c.a < 0.1) discard;
                FragColor = vec4(c.rgb * vLight, c.a * uAlpha);
            }
            """;

    private final Shader lineShader = new Shader(LINE_VS, LINE_FS);
    private final Shader spriteShader = new Shader(SPRITE_VS, SPRITE_FS);
    private final int lineVao, lineVbo;
    private final int spriteVao, spriteVbo;
    private final FloatList sprites = new FloatList(4096);
    private final int atlasTex;

    public OverlayRenderer(int atlasTex) {
        this.atlasTex = atlasTex;
        lineVao = glGenVertexArrays();
        lineVbo = glGenBuffers();
        glBindVertexArray(lineVao);
        glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0);
        glEnableVertexAttribArray(0);

        spriteVao = glGenVertexArrays();
        spriteVbo = glGenBuffers();
        glBindVertexArray(spriteVao);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 24, 12);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 24, 20);
        for (int i = 0; i < 3; i++) glEnableVertexAttribArray(i);
    }

    /** Black wireframe box around the targeted block. */
    public void renderSelection(Matrix4f pv, int x, int y, int z) {
        float e = 0.003f;
        float x0 = x - e, y0 = y - e, z0 = z - e;
        float x1 = x + 1 + e, y1 = y + 1 + e, z1 = z + 1 + e;
        float[] v = {
                x0, y0, z0, x1, y0, z0, x1, y0, z0, x1, y0, z1,
                x1, y0, z1, x0, y0, z1, x0, y0, z1, x0, y0, z0,
                x0, y1, z0, x1, y1, z0, x1, y1, z0, x1, y1, z1,
                x1, y1, z1, x0, y1, z1, x0, y1, z1, x0, y1, z0,
                x0, y0, z0, x0, y1, z0, x1, y0, z0, x1, y1, z0,
                x1, y0, z1, x1, y1, z1, x0, y0, z1, x0, y1, z1,
        };
        lineShader.bind();
        lineShader.setMat4("uPV", pv);
        lineShader.setVec4("uColor", 0, 0, 0, 0.45f);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        upload(lineVao, lineVbo, v);
        glDrawArrays(GL_LINES, 0, v.length / 3);
        glDisable(GL_BLEND);
    }

    /** Crack overlay cube on the breaking block. */
    public void renderCrack(Matrix4f pv, int x, int y, int z, float progress) {
        int stage = Math.min(9, (int) (progress * 10));
        int tile = Tiles.CRACK_0 + stage;
        float s = 1f / Tiles.ATLAS_TILES;
        float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
        float u1 = u0 + s, v1 = v0 + s;
        sprites.clear();
        float e = 0.004f;
        float ax = x - e, ay = y - e, az = z - e;
        float bx = x + 1 + e, by = y + 1 + e, bz = z + 1 + e;
        // 6 faces, both windings not needed (cull on)
        quad(ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, u0, v0, u1, v1, 1);  // bottom
        quad(ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, u0, v0, u1, v1, 1);  // top
        quad(ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, u0, v0, u1, v1, 1);  // north
        quad(bx, ay, bz, bx, by, bz, ax, by, bz, ax, ay, bz, u0, v0, u1, v1, 1);  // south
        quad(ax, ay, bz, ax, by, bz, ax, by, az, ax, ay, az, u0, v0, u1, v1, 1);  // west
        quad(bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, u0, v0, u1, v1, 1);  // east
        spriteShader.bind();
        spriteShader.setMat4("uPV", pv);
        spriteShader.setFloat("uAlpha", 0.85f);
        spriteShader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1, -1);
        flushSprites();
        glDisable(GL_POLYGON_OFFSET_FILL);
        glDisable(GL_BLEND);
    }

    /** Item drops as spinning billboards. */
    public void renderItems(Matrix4f pv, World world, List<Entity> entities,
                            float dayLight, float partial, Vector3f camPos) {
        sprites.clear();
        for (Entity e : entities) {
            if (!(e instanceof ItemEntity item)) continue;
            if (e.distSq(camPos.x, camPos.y, camPos.z) > 64 * 64) continue;
            double ix = e.prevX + (e.x - e.prevX) * partial;
            double iy = e.prevY + (e.y - e.prevY) * partial;
            double iz = e.prevZ + (e.z - e.prevZ) * partial;
            float t = item.age + partial;
            float bob = (float) Math.sin(t * 0.08) * 0.06f + 0.12f;
            float spin = t * 0.04f;
            float rx = (float) Math.cos(spin) * 0.18f, rz = (float) Math.sin(spin) * 0.18f;

            Item it = item.stack.item();
            int tile = it.isBlock() ? it.block().tileSide : it.icon;
            float s = 1f / Tiles.ATLAS_TILES;
            float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;

            int bx = (int) Math.floor(ix), by = (int) Math.floor(iy + 0.2), bz = (int) Math.floor(iz);
            float sky = world.getSky(bx, by, bz) / 15f;
            float blk = world.getBlockLight(bx, by, bz) / 15f;
            float light = Math.max(craft.Settings.minLight(), Math.max(curve(blk), curve(sky) * dayLight));

            float x0 = (float) (ix - rx), z0 = (float) (iz - rz);
            float x1 = (float) (ix + rx), z1 = (float) (iz + rz);
            float yB = (float) (iy + bob), yT = yB + 0.36f;
            quad(x0, yB, z0, x0, yT, z0, x1, yT, z1, x1, yB, z1, u0, v0, u0 + s, v0 + s, light);
            quad(x1, yB, z1, x1, yT, z1, x0, yT, z0, x0, yB, z0, u0, v0, u0 + s, v0 + s, light);
        }
        if (sprites.size() == 0) return;
        spriteShader.bind();
        spriteShader.setMat4("uPV", pv);
        spriteShader.setFloat("uAlpha", 1f);
        spriteShader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        flushSprites();
    }

    private static float curve(float a) {
        return a / (4f - 3f * a);
    }

    private void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3,
                      float u0, float v0, float u1, float v1, float light) {
        // texture corners: v0 = top of tile -> map to top vertices (1,2)
        svert(x0, y0, z0, u0, v1, light);
        svert(x1, y1, z1, u0, v0, light);
        svert(x2, y2, z2, u1, v0, light);
        svert(x2, y2, z2, u1, v0, light);
        svert(x3, y3, z3, u1, v1, light);
        svert(x0, y0, z0, u0, v1, light);
    }

    private void svert(float x, float y, float z, float u, float v, float l) {
        sprites.add(x);
        sprites.add(y);
        sprites.add(z);
        sprites.add(u);
        sprites.add(v);
        sprites.add(l);
    }

    private void flushSprites() {
        FloatBuffer fb = MemoryUtil.memAllocFloat(sprites.size());
        fb.put(sprites.raw(), 0, sprites.size()).flip();
        glBindVertexArray(spriteVao);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
        MemoryUtil.memFree(fb);
        glDisable(GL_CULL_FACE);
        glDrawArrays(GL_TRIANGLES, 0, sprites.size() / 6);
        glEnable(GL_CULL_FACE);
        sprites.clear();
    }

    private void upload(int vao, int vbo, float[] data) {
        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        fb.put(data).flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
        MemoryUtil.memFree(fb);
    }
}
