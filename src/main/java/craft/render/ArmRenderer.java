package craft.render;

import craft.item.Item;
import craft.item.ItemStack;
import craft.player.Player;
import craft.util.FloatList;
import craft.world.Block;
import craft.world.Face;
import craft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * First-person view-model renderer: draws the player's right forearm angled up from the
 * bottom-right of the screen, with the held block (as a 3D cube) or held item/tool (as a
 * flat sprite) sitting in the hand. The mining/attack swing animates the whole hand.
 *
 * <p>Everything is built directly in eye space (camera at the origin looking down -Z,
 * +X right, +Y up) and drawn with a dedicated fixed-FOV projection so the arm keeps a
 * stable size regardless of the world FOV (sprint boost etc.). The depth buffer is cleared
 * first so the arm always draws on top of the world, like vanilla.</p>
 *
 * <p>Two unit systems are used per transform: the outer placement (translate/rotate) is in
 * blocks (eye space); a {@code scale(1/16)} then switches into model-pixel space, in which
 * the cuboids and their child offsets are expressed (16px = 1 block, matching the atlas).</p>
 */
public class ArmRenderer {
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
    private final Matrix4f proj = new Matrix4f();
    private final Vector3f tmp = new Vector3f();

    public ArmRenderer(int atlasTex) {
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

    /** Draws the first-person arm + held item. Call after the world, before the 2D HUD. */
    public void render(float aspect, World world, Player player, float dayLight, float partial) {
        // light sampled at the player's head — same formula as the third-person body, so the
        // hand darkens in shadow/at night exactly like the rest of the player model
        int bx = (int) Math.floor(player.x), by = (int) Math.floor(player.y + 0.9), bz = (int) Math.floor(player.z);
        float sky = world.getSky(bx, by, bz) / 15f;
        float blk = world.getBlockLight(bx, by, bz) / 15f;
        float light = Math.max(craft.Settings.minLight(), Math.max(curve(blk), curve(sky) * dayLight));

        // swing progress runs 0 (idle / start of swing) -> 1 (end of swing)
        float remaining = Math.max(0, player.swingTicks - partial);
        float sp = (Player.SWING_TICKS - remaining) / Player.SWING_TICKS;
        boolean swinging = player.swingTicks > 0;
        float f = (float) Math.sin(sp * Math.PI);              // 0 at ends, 1 mid-swing
        float f1 = (float) Math.sin(Math.sqrt(sp) * Math.PI);  // peaks earlier (snappy chop)

        // walk/run bob: phase from accumulated limb-swing, intensity from walk speed.
        // Interpolate between ticks (partial) so it's smooth at the render frame rate rather
        // than stepping at the 20 Hz tick rate.
        float ls = player.prevLimbSwing + (player.limbSwing - player.prevLimbSwing) * partial;
        float lsa = player.prevLimbSwingAmount
                + (player.limbSwingAmount - player.prevLimbSwingAmount) * partial;
        float walk = ls * 2.6f;
        float amt = Math.min(1f, lsa);
        // vertical inertia while airborne (jumping/falling): hand lags the body's vertical move
        float airY = player.onGround ? 0f
                : Math.max(-0.09f, Math.min(0.09f, (float) -player.vy * 0.12f));

        proj.identity().perspective((float) Math.toRadians(70), aspect, 0.01f, 16f);

        verts.clear();
        buildArm(player.inventory.held(), swinging, f, f1, walk, amt, airY, light);

        shader.bind();
        shader.setMat4("uPV", proj);
        shader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);

        // clear depth so the hand overlays the world but still self-occludes correctly
        glDepthMask(true);
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        flush();
        glEnable(GL_CULL_FACE);
    }

    private void buildArm(ItemStack held, boolean swinging, float f, float f1,
                          float walk, float amt, float airY, float light) {
        int SKIN = Tiles.PLAYER_SKIN;   // same arm texture as the third-person body model

        // Shared root transform (eye space, blocks) for the whole hand layer, so the arm and the
        // held item move together. NB: it is pre-multiplied (rotates about the eye), so small
        // angles move the far end of the arm a lot — keep everything small/mostly translational.
        Matrix4f root = new Matrix4f();
        // walk/run view-bob: a subtle figure-8 sway that grows with movement speed
        if (amt > 0.001f) {
            root.translate((float) Math.sin(walk) * 0.025f * amt,
                    -(float) Math.abs(Math.cos(walk)) * 0.04f * amt, 0f);
            root.rotateZ((float) Math.toRadians(Math.sin(walk) * 1.6f * amt));
            root.rotateX((float) Math.toRadians(Math.abs(Math.cos(walk)) * 2.5f * amt));
        }
        // jump/fall inertia
        if (airY != 0f) root.translate(0f, airY, 0f);
        // attack / mining chop
        if (swinging) {
            root.translate(-0.04f * f1, -0.07f * f, -0.05f * f);
            root.rotateX((float) Math.toRadians(-12f * f1));
            root.rotateZ((float) Math.toRadians(5f * f));
        }

        // Shared hand anchor (eye space, blocks): the hand sits just below centre-right; the
        // forearm runs down from here off the bottom-right of the screen.
        Matrix4f base = new Matrix4f(root);
        base.translate(0.34f, -0.26f, -0.78f);

        // --- forearm: a long 4x4 cuboid (same cross-section as the third-person arm). The
        // hand end (y=0) is on screen; the upper arm/sleeve end runs well past the bottom
        // edge. The arm angles AWAY into the scene — the elbow end is the nearest part and it
        // recedes to the hand — rather than lying flat with its face toward the camera. ---
        Matrix4f arm = new Matrix4f(base);
        arm.rotateZ((float) Math.toRadians(33f));        // angle up from the bottom-right corner
        arm.rotateX((float) Math.toRadians(-28f));       // tilt the length away from the camera
        arm.rotateY((float) Math.toRadians(-6f));
        arm.scale(1f / 16f);                             // -> model-pixel space below
        armBox(arm, light);

        if (held == null) return;
        Item it = held.item();
        Matrix4f hand = new Matrix4f(base);
        hand.scale(1f / 16f);
        // (held item shares `base`, so it bobs/swings with the arm)
        // a full cube block renders as a 3D cube; everything else (tools, food, flowers and
        // other CROSS "blocks" like saplings/torches) renders as a flat sprite, matching the
        // hotbar icon rule
        boolean cube = it.isBlock() && it.block().layer != Block.Layer.CROSS;
        if (cube) {
            Block b = it.block();
            // held block: a real 3D cube, corner-on at 45deg yaw, resting in the hand
            Matrix4f bm = new Matrix4f(hand);
            bm.translate(1f, 2f, -2f);
            bm.rotateY((float) Math.toRadians(45));
            bm.rotateX((float) Math.toRadians(18));
            bm.scale(0.75f);
            cuboid(bm, -3, -3, -3, 6, 6, 6, light,
                    b.tileTop, b.tileBottom, b.tileSide, b.tileSide, b.tileSide, b.tileSide);
        } else {
            // flat icon extruded to a 1px-thick 3D sprite, hanging diagonally in the hand
            int t = it.isBlock() ? it.block().tileSide : it.icon;
            Matrix4f im = new Matrix4f(hand);
            im.translate(1f, 1f, 1f);
            im.rotateZ((float) Math.toRadians(20 + 180));   // handle into the hand, head up/out
            im.rotateY((float) Math.toRadians(-12 + 45));   // angled 45 clockwise about Y
            im.scale(-0.54f, 0.54f, 0.54f);                 // mirror across the Y axis
            sprite(im, t, light);
        }
    }

    /**
     * The forearm box (4x24x4 model px, hand at y=0, sleeve end at y=-24). Identical geometry
     * to a plain cuboid, but the PLAYER_SKIN tile is mapped at 1 texel per model pixel so the
     * skin pixels render SQUARE (a full-tile-per-face mapping stretches them on the 4x24 faces).
     * The four long faces show a 4x24 texel patch — since the tile is only 16 texels tall, the
     * skin is tiled along the length (a 16px segment then an 8px segment). End caps are 4x4.
     */
    private void armBox(Matrix4f m, float light) {
        int tile = Tiles.PLAYER_SKIN;
        float s = 1f / Tiles.ATLAS_TILES, tx = s / Tiles.TILE_PX;
        float bu = (tile % Tiles.ATLAS_TILES) * s, bv = (tile / Tiles.ATLAS_TILES) * s;
        float uA = bu + 6 * tx, uB = bu + 10 * tx;   // centred 4-texel-wide column
        float vTop = bv, vBot = bv + 16 * tx;        // full tile height (16px segment)
        float vHalf = bv + 8 * tx;                   // half tile (8px remainder segment)
        float ve0 = bv + 6 * tx, ve1 = bv + 10 * tx; // end-cap 4x4 region
        float ls = light * Face.SHADE[Face.NORTH], lwe = light * Face.SHADE[Face.WEST];
        float lt = light * Face.SHADE[Face.TOP], lb = light * Face.SHADE[Face.BOTTOM];
        // long faces (4 wide x 24 long): a = across (4px), b = along length; tiled 16 + 8
        for (int side = 0; side < 4; side++) {
            boolean depth = side >= 2;               // 0,1 = north/south (z); 2,3 = west/east (x)
            float fixed = (side == 1 || side == 3) ? 2 : -2;
            float l = depth ? lwe : ls;
            float ax = depth ? 0 : 4, az = depth ? 4 : 0;
            float ox = depth ? fixed : -2, oz = depth ? -2 : fixed;
            quad(m, l, ox, -16, oz, ax, 0, az, 0, 16, 0, uA, vTop, uB, vBot);  // hand segment
            quad(m, l, ox, -24, oz, ax, 0, az, 0, 8, 0, uA, vTop, uB, vHalf);  // sleeve segment
        }
        quad(m, lt, -2, 0, -2, 4, 0, 0, 0, 0, 4, uA, ve0, uB, ve1);     // hand cap (y=0)
        quad(m, lb, -2, -24, -2, 4, 0, 0, 0, 0, 4, uA, ve0, uB, ve1);   // sleeve cap (y=-24)
    }

    /**
     * A flat item icon extruded to a 1px-thick slab (16x16x1 model px, centred). The two big
     * faces show the full icon; the four thin rims sample the icon's 1px edge row/column so the
     * extruded sides match the sprite outline instead of stretching the whole tile.
     */
    private void sprite(Matrix4f m, int tile, float light) {
        float s = 1f / Tiles.ATLAS_TILES;
        float texel = s / Tiles.TILE_PX;
        float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
        float u1 = u0 + s, v1 = v0 + s;
        // front (+Z, toward camera) and back (-Z) faces: full icon, mapped IDENTICALLY (same
        // texel at each x,y) so the back sits exactly behind the front. Otherwise the back is a
        // mirror image and shows through the icon's transparent pixels as a doubled/flipped sprite.
        quad(m, light, -8, -8, 0.5f, 16, 0, 0, 0, 16, 0, u0, v1, u1, v0);
        quad(m, light, -8, -8, -0.5f, 16, 0, 0, 0, 16, 0, u0, v1, u1, v0);
        // four rims, each sampling a single edge line of the icon
        quad(m, light, -8, 8, 0.5f, 16, 0, 0, 0, 0, -1, u0, v0, u1, v0 + texel);          // top
        quad(m, light, -8, -8, -0.5f, 16, 0, 0, 0, 0, 1, u0, v1 - texel, u1, v1);         // bottom
        quad(m, light, -8, -8, 0.5f, 0, 16, 0, 0, 0, -1, u0, v1, u0 + texel, v0);         // left
        quad(m, light, 8, -8, -0.5f, 0, 16, 0, 0, 0, 1, u1 - texel, v1, u1, v0);          // right
    }

    /** Emits one quad from a corner + two edge vectors (model px) with explicit UV rect. */
    private void quad(Matrix4f m, float light, float ox, float oy, float oz,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float u0, float v0, float u1, float v1) {
        float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        float[][] off = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            tmp.set(ox + off[i][0] * ax + off[i][1] * bx,
                    oy + off[i][0] * ay + off[i][1] * by,
                    oz + off[i][0] * az + off[i][1] * bz);
            m.transformPosition(tmp);
            p[i][0] = tmp.x;
            p[i][1] = tmp.y;
            p[i][2] = tmp.z;
        }
        int[] order = {0, 1, 2, 2, 3, 0};
        for (int oi : order) {
            verts.add(p[oi][0]);
            verts.add(p[oi][1]);
            verts.add(p[oi][2]);
            verts.add(uv[oi][0]);
            verts.add(uv[oi][1]);
            verts.add(light);
        }
    }

    /**
     * Emits a textured cuboid in the matrix's local space (the caller's matrix carries the
     * model-pixel -> block scale, so corner coords here are in model pixels, 16 = 1 block).
     * Faces: top,bottom,N,S,W,E.
     */
    private void cuboid(Matrix4f m, float ox, float oy, float oz, float w, float h, float d,
                        float light, int... tiles) {
        float s = 1f / Tiles.ATLAS_TILES;
        for (int face = 0; face < 6; face++) {
            int tile = tiles[face];
            float u0 = (tile % Tiles.ATLAS_TILES) * s, v0 = (tile / Tiles.ATLAS_TILES) * s;
            float u1 = u0 + s, v1 = v0 + s;
            int[][] c = Face.CORNERS[face];
            float fl = light * Face.SHADE[face];
            float[][] p = new float[4][3];
            for (int i = 0; i < 4; i++) {
                tmp.set(ox + c[i][0] * w, oy + c[i][1] * h, oz + c[i][2] * d);
                m.transformPosition(tmp);
                p[i][0] = tmp.x;
                p[i][1] = tmp.y;
                p[i][2] = tmp.z;
            }
            float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
            int[] order = {0, 1, 2, 2, 3, 0};
            for (int oi : order) {
                verts.add(p[oi][0]);
                verts.add(p[oi][1]);
                verts.add(p[oi][2]);
                verts.add(uv[oi][0]);
                verts.add(uv[oi][1]);
                verts.add(fl);
            }
        }
    }

    private static float curve(float a) {
        return a / (4f - 3f * a);
    }

    private void flush() {
        if (verts.size() == 0) return;
        FloatBuffer fb = MemoryUtil.memAllocFloat(verts.size());
        fb.put(verts.raw(), 0, verts.size()).flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STREAM_DRAW);
        MemoryUtil.memFree(fb);
        glDrawArrays(GL_TRIANGLES, 0, verts.size() / 6);
    }
}
