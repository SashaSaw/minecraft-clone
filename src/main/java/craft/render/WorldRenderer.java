package craft.render;

import craft.world.Chunk;
import craft.world.World;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Owns section meshes (VAO/VBO per layer), schedules remeshing, draws the world. */
public class WorldRenderer {
    private static final int LAYER_OPAQUE = 0, LAYER_CUTOUT = 1, LAYER_WATER = 2;

    private static final String VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in float aSky;
            layout(location=3) in float aBlock;
            layout(location=4) in float aShade;
            uniform mat4 uPV;
            uniform vec3 uOffset;
            uniform vec3 uCam;
            out vec2 vUV;
            out float vSky;
            out float vBlock;
            out float vShade;
            out float vDist;
            void main() {
                vec3 w = aPos + uOffset;
                gl_Position = uPV * vec4(w, 1.0);
                vUV = aUV;
                vSky = aSky;
                vBlock = aBlock;
                vShade = aShade;
                vDist = distance(w, uCam);
            }
            """;

    private static final String FS = """
            #version 330 core
            in vec2 vUV;
            in float vSky;
            in float vBlock;
            in float vShade;
            in float vDist;
            out vec4 FragColor;
            uniform sampler2D uTex;
            uniform float uDayLight;
            uniform int uCutout;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uMinLight;
            void main() {
                vec4 c = texture(uTex, vUV);
                if (uCutout == 1 && c.a < 0.5) discard;
                float l = max(vBlock, vSky * uDayLight);
                l = max(l, uMinLight);
                vec3 col = c.rgb * l * vShade;
                float f = clamp((uFogEnd - vDist) / (uFogEnd - uFogStart), 0.0, 1.0);
                col = mix(uFogColor, col, f);
                FragColor = vec4(col, c.a);
            }
            """;

    private static class Section {
        final int cx, sy, cz;
        final int[] vao = {-1, -1, -1};
        final int[] vbo = {-1, -1, -1};
        final int[] count = {0, 0, 0};

        Section(int cx, int sy, int cz) {
            this.cx = cx;
            this.sy = sy;
            this.cz = cz;
        }
    }

    private final World world;
    private final ExecutorService meshPool;
    private final Shader shader;
    private final int atlasTex;

    private final Map<Long, Section> sections = new HashMap<>();
    private final HashSet<Long> inFlight = new HashSet<>();
    private final ConcurrentLinkedQueue<ChunkMesher.MeshData> uploads = new ConcurrentLinkedQueue<>();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f pv = new Matrix4f();
    private final List<Section> visible = new ArrayList<>();

    public WorldRenderer(World world, ExecutorService meshPool, int atlasTex) {
        this.world = world;
        this.meshPool = meshPool;
        this.atlasTex = atlasTex;
        this.shader = new Shader(VS, FS);
    }

    /** Called once per frame: free unloaded meshes, schedule remeshes, upload finished meshes. */
    public void update(int pcx, int pcz, int renderDist) {
        for (Chunk c : world.unloadedChunks) {
            for (int sy = 0; sy < Chunk.SECTIONS; sy++) {
                Section s = sections.remove(World.sectionKey(c.cx, sy, c.cz));
                if (s != null) free(s);
            }
        }
        world.unloadedChunks.clear();

        // schedule dirty sections whose chunks are meshable, nearest player first
        if (!world.dirtySections.isEmpty() && inFlight.size() < 12) {
            List<Long> ready = new ArrayList<>();
            for (long key : world.dirtySections) {
                int cx = World.sectionKeyX(key), cz = World.sectionKeyZ(key);
                // Don't drop out-of-range sections: chunks decorate (and dirty) out to
                // renderDist+1, so a section can be dirtied while just outside renderDist and
                // would never be re-dirtied once it scrolls into view. Leave it dirty until it
                // becomes meshable and in range; World clears dirty sections when chunks unload.
                if (!inFlight.contains(key) && world.isMeshable(cx, cz)) ready.add(key);
            }
            ready.sort((a, b) -> {
                int da = sq(World.sectionKeyX(a) - pcx) + sq(World.sectionKeyZ(a) - pcz);
                int db = sq(World.sectionKeyX(b) - pcx) + sq(World.sectionKeyZ(b) - pcz);
                return Integer.compare(da, db);
            });
            for (long key : ready) {
                if (inFlight.size() >= 12) break;
                world.dirtySections.remove(key);
                inFlight.add(key);
                int cx = World.sectionKeyX(key), sy = World.sectionKeyY(key), cz = World.sectionKeyZ(key);
                meshPool.submit(() -> {
                    try {
                        uploads.add(ChunkMesher.mesh(world, cx, sy, cz));
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
            }
        }

        // upload finished meshes (budgeted)
        for (int i = 0; i < 8; i++) {
            ChunkMesher.MeshData md = uploads.poll();
            if (md == null) break;
            long key = World.sectionKey(md.cx, md.sy, md.cz);
            inFlight.remove(key);
            Section s = sections.computeIfAbsent(key, k -> new Section(md.cx, md.sy, md.cz));
            uploadLayer(s, LAYER_OPAQUE, md.opaque);
            uploadLayer(s, LAYER_CUTOUT, md.cutout);
            uploadLayer(s, LAYER_WATER, md.water);
            if (s.count[0] == 0 && s.count[1] == 0 && s.count[2] == 0) {
                sections.remove(key);
                free(s);
            }
        }
    }

    private static int sq(int v) {
        return v * v;
    }

    private void uploadLayer(Section s, int layer, float[] data) {
        if (data.length == 0) {
            if (s.vao[layer] != -1) {
                glDeleteVertexArrays(s.vao[layer]);
                glDeleteBuffers(s.vbo[layer]);
                s.vao[layer] = -1;
                s.vbo[layer] = -1;
            }
            s.count[layer] = 0;
            return;
        }
        if (s.vao[layer] == -1) {
            s.vao[layer] = glGenVertexArrays();
            s.vbo[layer] = glGenBuffers();
            glBindVertexArray(s.vao[layer]);
            glBindBuffer(GL_ARRAY_BUFFER, s.vbo[layer]);
            int stride = ChunkMesher.FLOATS_PER_VERTEX * 4;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12);
            glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 20);
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 24);
            glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 28);
            for (int i = 0; i < 5; i++) glEnableVertexAttribArray(i);
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, s.vbo[layer]);
        }
        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        fb.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);
        s.count[layer] = data.length / ChunkMesher.FLOATS_PER_VERTEX;
    }

    private void free(Section s) {
        for (int i = 0; i < 3; i++) {
            if (s.vao[i] != -1) {
                glDeleteVertexArrays(s.vao[i]);
                glDeleteBuffers(s.vbo[i]);
            }
        }
    }

    /** Opaque + cutout passes. Call renderWater afterwards (overlays may draw between). */
    public void renderSolid(Matrix4f proj, Matrix4f view, Vector3f camPos,
                            float dayLight, float[] fogColor, float fogStart, float fogEnd) {
        proj.mul(view, pv);
        frustum.set(pv);

        visible.clear();
        for (Section s : sections.values()) {
            float x0 = s.cx << 4, y0 = s.sy << 4, z0 = s.cz << 4;
            if (frustum.testAab(x0, y0, z0, x0 + 16, y0 + 16, z0 + 16)) {
                visible.add(s);
            }
        }

        shader.bind();
        shader.setMat4("uPV", pv);
        shader.setVec3("uCam", camPos.x, camPos.y, camPos.z);
        shader.setFloat("uDayLight", dayLight);
        shader.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
        shader.setFloat("uFogStart", fogStart);
        shader.setFloat("uFogEnd", fogEnd);
        shader.setFloat("uMinLight", craft.Settings.minLight());
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        shader.setInt("uCutout", 0);
        for (Section s : visible) draw(s, LAYER_OPAQUE);

        shader.setInt("uCutout", 1);
        for (Section s : visible) draw(s, LAYER_CUTOUT);
    }

    /** Translucent water pass: blended, back-to-front by section distance. */
    public void renderWater(Vector3f camPos) {
        shader.bind();
        shader.setInt("uCutout", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        visible.sort((a, b) -> Float.compare(dist2(b, camPos), dist2(a, camPos)));
        for (Section s : visible) draw(s, LAYER_WATER);
        glDisable(GL_BLEND);
    }

    public Matrix4f pv() {
        return pv;
    }

    private static float dist2(Section s, Vector3f cam) {
        float dx = (s.cx << 4) + 8 - cam.x;
        float dy = (s.sy << 4) + 8 - cam.y;
        float dz = (s.cz << 4) + 8 - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void draw(Section s, int layer) {
        if (s.count[layer] == 0) return;
        shader.setVec3("uOffset", s.cx << 4, 0, s.cz << 4);
        glBindVertexArray(s.vao[layer]);
        glDrawArrays(GL_TRIANGLES, 0, s.count[layer]);
    }

    /** Frees all GL resources (when leaving a world). */
    public void dispose() {
        for (Section s : sections.values()) free(s);
        sections.clear();
        inFlight.clear();
        uploads.clear();
    }

    public int sectionCount() {
        return sections.size();
    }

    public int pendingMeshes() {
        return world.dirtySections.size() + inFlight.size();
    }
}
