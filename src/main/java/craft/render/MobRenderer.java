package craft.render;

import craft.entity.Animals;
import craft.entity.Entity;
import craft.entity.Mob;
import craft.entity.Zombie;
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

/**
 * Renders mobs as textured box models (16 model units = 1 block) with vanilla-style
 * limb-swing animation and head tracking. Models face -Z at yaw 0.
 */
public class MobRenderer {
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
            uniform vec3 uTint;
            void main() {
                vec4 c = texture(uTex, vUV);
                if (c.a < 0.1) discard;
                FragColor = vec4(c.rgb * vLight * uTint, c.a);
            }
            """;

    private static final float[] FACE_SHADE = {1.0f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f};

    private final Shader shader = new Shader(VS, FS);
    private final int vao, vbo;
    private final FloatList verts = new FloatList(4096);
    private final int atlasTex;

    // per-mob transform state
    private double ex, ey, ez;
    private float bodyYawSin, bodyYawCos;
    private float light;

    public MobRenderer(int atlasTex) {
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

    public void render(Matrix4f pv, World world, List<Entity> entities,
                       float dayLight, float partial, Vector3f camPos) {
        shader.bind();
        shader.setMat4("uPV", pv);
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);

        for (Entity e : entities) {
            if (!(e instanceof Mob mob)) continue;
            if (e.distSq(camPos.x, camPos.y, camPos.z) > 80 * 80) continue;

            ex = mob.prevX + (mob.x - mob.prevX) * partial;
            ey = mob.prevY + (mob.y - mob.prevY) * partial;
            ez = mob.prevZ + (mob.z - mob.prevZ) * partial;
            float bYaw = mob.prevBodyYaw + Mob.wrapAngle(mob.bodyYaw - mob.prevBodyYaw) * partial;
            bodyYawSin = (float) Math.sin(bYaw);
            bodyYawCos = (float) Math.cos(bYaw);

            int bx = (int) Math.floor(ex), by = (int) Math.floor(ey + 0.5), bz = (int) Math.floor(ez);
            float sky = world.getSky(bx, by, bz) / 15f;
            float blk = world.getBlockLight(bx, by, bz) / 15f;
            light = Math.max(0.05f, Math.max(curve(blk), curve(sky) * dayLight));

            float swing = mob.limbSwing * 4.2f;
            float amt = mob.limbSwingAmount;
            float legA = (float) Math.cos(swing) * 1.4f * amt;
            float legB = (float) Math.cos(swing + Math.PI) * 1.4f * amt;
            float headYawRel = Mob.wrapAngle(mob.headYaw - bYaw);
            float headPitch = mob.headPitch;

            verts.clear();
            if (mob instanceof Zombie z) {
                zombie(z, legA, legB, headYawRel, headPitch);
            } else if (mob instanceof Animals.Pig) {
                pig(legA, legB, headYawRel, headPitch);
            } else if (mob instanceof Animals.Cow) {
                cow(legA, legB, headYawRel, headPitch);
            } else if (mob instanceof Animals.Sheep s) {
                sheep(s, legA, legB, headYawRel, headPitch);
            } else if (mob instanceof Animals.Chicken c) {
                chicken(c, legA, legB, headYawRel, headPitch, partial);
            }

            boolean flash = mob.hurtTicks > 0;
            boolean fire = mob instanceof Zombie z2 && z2.burning;
            if (flash) shader.setVec3("uTint", 1.6f, 0.5f, 0.5f);
            else if (fire) shader.setVec3("uTint", 1.6f, 1.0f, 0.6f);
            else shader.setVec3("uTint", 1, 1, 1);
            flushMob();
        }
    }

    // ---------- models ----------

    private void zombie(Zombie m, float legA, float legB, float hYaw, float hPitch) {
        float armBob = (float) Math.sin(m.age * 0.09) * 0.06f;
        int F = Tiles.ZOMBIE_FACE, S = Tiles.ZOMBIE_SKIN, SH = Tiles.ZOMBIE_SHIRT, P = Tiles.ZOMBIE_PANTS;
        // head (front face gets the zombie face)
        box(0, 24, 0, hPitch, hYaw, -4, 0, -4, 8, 8, 8, S, S, F, S, S, S);
        // body
        box(0, 12, 0, 0, 0, -4, 0, -2, 8, 12, 4, SH, SH, SH, SH, SH, SH);
        // arms stretched forward
        float armPitch = (float) (Math.PI / 2) + armBob;
        box(-6, 22, 0, armPitch, 0, -2, -10, -2, 4, 12, 4, S, S, S, S, S, S);
        box(6, 22, 0, armPitch - armBob * 2, 0, -2, -10, -2, 4, 12, 4, S, S, S, S, S, S);
        // legs
        box(-2, 12, 0, legA, 0, -2, -12, -2, 4, 12, 4, P, P, P, P, P, P);
        box(2, 12, 0, legB, 0, -2, -12, -2, 4, 12, 4, P, P, P, P, P, P);
    }

    private void pig(float legA, float legB, float hYaw, float hPitch) {
        int S = Tiles.PIG_SKIN, F = Tiles.PIG_FACE;
        box(0, 6, 0, 0, 0, -5, 0, -8, 10, 8, 16, S, S, S, S, S, S);     // body
        box(0, 10, -8, hPitch, hYaw, -4, -4, -8, 8, 8, 8, S, S, F, S, S, S);  // head
        leg(-3, 6, -5, legA, 6, S);
        leg(3, 6, -5, legB, 6, S);
        leg(-3, 6, 5, legB, 6, S);
        leg(3, 6, 5, legA, 6, S);
    }

    private void cow(float legA, float legB, float hYaw, float hPitch) {
        int B = Tiles.COW_BODY, F = Tiles.COW_FACE, L = Tiles.COW_LEG;
        box(0, 12, 0, 0, 0, -6, 0, -9, 12, 10, 18, B, B, B, B, B, B);
        box(0, 19, -9, hPitch, hYaw, -4, -3, -6, 8, 8, 6, B, B, F, B, B, B);
        // horns
        box(0, 19, -9, hPitch, hYaw, -5, 3, -3, 1, 3, 1, L, L, L, L, L, L);
        box(0, 19, -9, hPitch, hYaw, 4, 3, -3, 1, 3, 1, L, L, L, L, L, L);
        leg(-4, 12, -6, legA, 12, L);
        leg(4, 12, -6, legB, 12, L);
        leg(-4, 12, 6, legB, 12, L);
        leg(4, 12, 6, legA, 12, L);
    }

    private void sheep(Animals.Sheep m, float legA, float legB, float hYaw, float hPitch) {
        int W = Tiles.SHEEP_WOOL, F = Tiles.SHEEP_FACE, L = Tiles.SHEEP_LEG;
        box(0, 12, 0, 0, 0, -4.5f, 0, -8, 9, 10, 16, W, W, W, W, W, W);
        float graze = m.eatTimer > 0 ? 0.7f : 0;
        box(0, 18, -8, hPitch + graze, hYaw, -3, -3 - graze * 6, -6, 6, 6, 8, W, W, F, W, W, W);
        leg(-2.5f, 12, -5, legA, 12, L);
        leg(2.5f, 12, -5, legB, 12, L);
        leg(-2.5f, 12, 5, legB, 12, L);
        leg(2.5f, 12, 5, legA, 12, L);
    }

    private void chicken(Animals.Chicken m, float legA, float legB, float hYaw, float hPitch, float partial) {
        int B = Tiles.CHICKEN_BODY, F = Tiles.CHICKEN_FACE, Y = Tiles.YELLOW, R = Tiles.RED;
        box(0, 5, 0, 0, 0, -3, 0, -4, 6, 6, 8, B, B, B, B, B, B);       // body
        box(0, 10, -3, hPitch, hYaw, -2, 0, -3, 4, 6, 3, B, B, F, B, B, B);  // head
        box(0, 10, -3, hPitch, hYaw, -2, 2, -5, 4, 2, 2, Y, Y, Y, Y, Y, Y);  // beak
        box(0, 10, -3, hPitch, hYaw, -1, 0, -4.5f, 2, 2, 1, R, R, R, R, R, R); // wattle
        float flap = !m.onGround ? (float) Math.sin(m.wingFlap + partial) * 0.8f : 0;
        wing(-3.5f, 9, 0, flap, B);
        wing(3.5f, 9, 0, -flap, B);
        leg(-1.5f, 5, 0, legA, 5, Y);
        leg(1.5f, 5, 0, legB, 5, Y);
    }

    private void leg(float px, float py, float pz, float swing, int len, int tile) {
        box(px, py, pz, swing, 0, -2, -len, -2, 4, len, 4, tile, tile, tile, tile, tile, tile);
    }

    private void wing(float px, float py, float pz, float roll, int tile) {
        // roll approximated as pitch on a thin box
        box(px, py, pz, 0, 0, px < 0 ? -1 : 0, -4 + Math.abs(roll), -3, 1, 4, 6,
                tile, tile, tile, tile, tile, tile);
    }

    // ---------- box building ----------

    /**
     * Emits one box. Pivot in model units relative to entity feet center; box min corner
     * relative to pivot; rotation rx (pitch, about X) then ry (yaw, about Y) at the pivot;
     * then body yaw; then entity position. Tiles per face: top,bottom,north,south,west,east.
     */
    private void box(float pivX, float pivY, float pivZ, float rx, float ry,
                     float bx, float by, float bz, float w, float h, float d,
                     int... tiles) {
        float sinX = (float) Math.sin(rx), cosX = (float) Math.cos(rx);
        float sinY = (float) Math.sin(ry), cosY = (float) Math.cos(ry);
        float s = 1f / Tiles.ATLAS_TILES;

        for (int face = 0; face < 6; face++) {
            int tile = tiles[face];
            float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
            float u1 = u0 + s, v1 = v0 + s;
            int[][] corners = craft.world.Face.CORNERS[face];
            float faceLight = light * FACE_SHADE[face];
            // emit as two triangles, corner order 0,1,2, 2,3,0
            float[][] p = new float[4][3];
            for (int i = 0; i < 4; i++) {
                float lx = bx + corners[i][0] * w;
                float ly = by + corners[i][1] * h;
                float lz = bz + corners[i][2] * d;
                // part rotation: pitch then yaw
                float y1 = ly * cosX - lz * sinX;
                float z1 = ly * sinX + lz * cosX;
                float x1 = lx;
                float x2 = x1 * cosY + z1 * sinY;
                float z2 = -x1 * sinY + z1 * cosY;
                // to entity space (1/16) + pivot
                float mx = (pivX + x2) / 16f;
                float my = (pivY + y1) / 16f;
                float mz = (pivZ + z2) / 16f;
                // body yaw
                float wx = mx * bodyYawCos + mz * bodyYawSin;
                float wz = -mx * bodyYawSin + mz * bodyYawCos;
                p[i][0] = (float) (ex + wx);
                p[i][1] = (float) (ey + my);
                p[i][2] = (float) (ez + wz);
            }
            float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
            int[] order = {0, 1, 2, 2, 3, 0};
            for (int oi : order) {
                verts.add(p[oi][0]);
                verts.add(p[oi][1]);
                verts.add(p[oi][2]);
                verts.add(uv[oi][0]);
                verts.add(uv[oi][1]);
                verts.add(faceLight);
            }
        }
    }

    private static float curve(float a) {
        return a / (4f - 3f * a);
    }

    private void flushMob() {
        if (verts.size() == 0) return;
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
