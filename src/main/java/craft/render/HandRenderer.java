package craft.render;

import craft.item.Item;
import craft.item.ItemStack;
import craft.player.Player;
import craft.world.Block;
import craft.world.Face;
import craft.util.FloatList;
import craft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * First-person viewmodel: the player's right forearm plus the held item, drawn in
 * eye space (camera at origin looking down -Z) directly in front of the camera.
 * Held blocks render as a small 3D cube; tools/food render as a flat angled sprite.
 * Drawn last in the 3D pass after clearing depth, so it overlays the world and never
 * clips into nearby geometry.
 */
public class HandRenderer {
    private static final String VS = """
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
    private static final String FS = """
            #version 330 core
            in vec2 vUV;
            in float vLight;
            out vec4 FragColor;
            uniform sampler2D uTex;
            void main() {
                vec4 c = texture(uTex, vUV);
                if (c.a < 0.1) discard;
                FragColor = vec4(c.rgb * vLight, c.a);
            }
            """;

    private final Shader shader = new Shader(VS, FS);
    private final int vao, vbo;
    private final FloatList verts = new FloatList(2048);
    private final int atlasTex;

    private final Vector4f tmp = new Vector4f();
    private float light;

    public HandRenderer(int atlasTex) {
        this.atlasTex = atlasTex;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 24, 12);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 24, 20);
        for (int i = 0; i < 3; i++) glEnableVertexAttribArray(i);
    }

    /**
     * @param proj   the world perspective projection (used as-is; geometry is in eye space)
     * @param player whose held item / swing state drives the model
     * @param camPos interpolated eye position, for sampling local light
     */
    public void render(Matrix4f proj, Player player, World world, Vector3f camPos,
                       float dayLight, float partial) {
        // light at the camera, same curve as mobs/items
        int bx = (int) Math.floor(camPos.x), by = (int) Math.floor(camPos.y), bz = (int) Math.floor(camPos.z);
        float sky = world.getSky(bx, by, bz) / 15f;
        float blk = world.getBlockLight(bx, by, bz) / 15f;
        light = Math.max(craft.Settings.minLight(), Math.max(curve(blk), curve(sky) * dayLight));

        // swing arc: 0 at rest, peaks to 1 mid-swing
        float swingT = Math.max(0, (player.swingTicks - partial)) / Player.SWING_TICKS;
        float arc = (float) Math.sin(swingT * Math.PI);

        verts.clear();
        ItemStack held = player.inventory.held();
        boolean holdingBlock = held != null && held.item().isBlock();

        // base hand transform (eye space): lower-right, tilted toward centre.
        // The swing dips the whole hand down and rotates it forward.
        Matrix4f hand = new Matrix4f()
                .translate(0.46f, -0.42f + arc * -0.18f, -0.72f)
                .rotateY((float) Math.toRadians(-12 + arc * 18))
                .rotateX((float) Math.toRadians(arc * 55))
                .rotateZ((float) Math.toRadians(8));

        // arm: a forearm cuboid anchored at the wrist, extending down-right out of frame
        Matrix4f arm = new Matrix4f(hand)
                .rotateZ((float) Math.toRadians(38))
                .rotateX((float) Math.toRadians(18));
        int A = Tiles.ARM_SKIN;
        box(arm, -0.07f, -0.62f, -0.07f, 0.14f, 0.62f, 0.14f, A, A, A, A, A, A);

        if (held != null) {
            if (holdingBlock) {
                // 3D block cube at the hand, rotated so three faces show
                Block b = held.item().block();
                Matrix4f cube = new Matrix4f(hand)
                        .translate(-0.03f, 0.12f, 0f)
                        .rotateY((float) Math.toRadians(-35))
                        .rotateX((float) Math.toRadians(18));
                // Face order: TOP, BOTTOM, NORTH, SOUTH, WEST, EAST
                box(cube, -0.14f, -0.14f, -0.14f, 0.28f, 0.28f, 0.28f,
                        b.tileTop, b.tileBottom, b.tileSide, b.tileSide, b.tileSide, b.tileSide);
            } else {
                // flat item icon, angled like a held tool
                Item it = held.item();
                Matrix4f spr = new Matrix4f(hand)
                        .translate(0.02f, 0.06f, 0f)
                        .rotateZ((float) Math.toRadians(-45))
                        .rotateY((float) Math.toRadians(-8));
                quad(spr, it.icon);
            }
        }

        if (verts.size() == 0) return;

        // overlay everything: clear depth so the world never clips the hand
        glClear(GL_DEPTH_BUFFER_BIT);
        shader.bind();
        shader.setMat4("uPV", proj);
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        flush();
        glDisable(GL_BLEND);
    }

    /** Emits one box in local space, transformed by m. Tiles per face: top,bottom,north,south,west,east. */
    private void box(Matrix4f m, float minX, float minY, float minZ,
                     float w, float h, float d, int... tiles) {
        float s = 1f / Tiles.ATLAS_TILES;
        for (int face = 0; face < 6; face++) {
            int tile = tiles[face];
            float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
            float u1 = u0 + s, v1 = v0 + s;
            int[][] corners = Face.CORNERS[face];
            float faceLight = light * Face.SHADE[face];
            float[][] p = new float[4][3];
            for (int i = 0; i < 4; i++) {
                tmp.set(minX + corners[i][0] * w, minY + corners[i][1] * h, minZ + corners[i][2] * d, 1f);
                m.transform(tmp);
                p[i][0] = tmp.x;
                p[i][1] = tmp.y;
                p[i][2] = tmp.z;
            }
            float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
            int[] order = {0, 1, 2, 2, 3, 0};
            for (int oi : order) vert(p[oi], uv[oi][0], uv[oi][1], faceLight);
        }
    }

    /** Emits a flat unit quad (item icon) in the XY plane, transformed by m. */
    private void quad(Matrix4f m, int tile) {
        float s = 1f / Tiles.ATLAS_TILES;
        float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
        float u1 = u0 + s, v1 = v0 + s;
        float r = 0.22f;
        float[][] local = {{-r, -r, 0}, {-r, r, 0}, {r, r, 0}, {r, -r, 0}};
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            tmp.set(local[i][0], local[i][1], local[i][2], 1f);
            m.transform(tmp);
            p[i][0] = tmp.x;
            p[i][1] = tmp.y;
            p[i][2] = tmp.z;
        }
        float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        int[] order = {0, 1, 2, 2, 3, 0};
        for (int oi : order) vert(p[oi], uv[oi][0], uv[oi][1], light);
    }

    private void vert(float[] pos, float u, float v, float l) {
        verts.add(pos[0]);
        verts.add(pos[1]);
        verts.add(pos[2]);
        verts.add(u);
        verts.add(v);
        verts.add(l);
    }

    private static float curve(float a) {
        return a / (4f - 3f * a);
    }

    private void flush() {
        FloatBuffer fb = MemoryUtil.memAllocFloat(verts.size());
        fb.put(verts.raw(), 0, verts.size()).flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
        MemoryUtil.memFree(fb);
        glDisable(GL_CULL_FACE);
        glDrawArrays(GL_TRIANGLES, 0, verts.size() / 6);
        glEnable(GL_CULL_FACE);
    }
}
