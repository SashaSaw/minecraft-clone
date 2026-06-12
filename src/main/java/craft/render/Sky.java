package craft.render;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

/**
 * Vanilla-style sky: gradient dome + sunset horizon glow, sun/moon billboards rotating
 * east->west, point stars at night. 24000-tick day; tick 0 = sunrise, 6000 = noon.
 */
public class Sky {
    public static final int DAY_TICKS = 24000;

    // Day colors (noon): zenith #78A7FF, horizon #C0D8FF
    private static final float[] ZENITH = {0.47f, 0.65f, 1.0f};
    private static final float[] HORIZON = {0.75f, 0.84f, 1.0f};

    private static final String DOME_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            uniform mat4 uPV;
            out vec3 vDir;
            void main() {
                vDir = aPos;
                vec4 p = uPV * vec4(aPos, 1.0);
                gl_Position = p.xyww;
            }
            """;

    private static final String DOME_FS = """
            #version 330 core
            in vec3 vDir;
            out vec4 FragColor;
            uniform vec3 uZenith;
            uniform vec3 uHorizon;
            uniform vec4 uGlowColor;   // rgb + alpha
            uniform vec2 uGlowDir;     // horizontal direction toward the sun
            void main() {
                vec3 dir = normalize(vDir);
                vec3 col;
                if (dir.y >= 0.0) {
                    col = mix(uHorizon, uZenith, pow(clamp(dir.y * 1.7, 0.0, 1.0), 0.75));
                } else {
                    col = mix(uHorizon, uHorizon * 0.45, clamp(-dir.y * 3.5, 0.0, 1.0));
                }
                float horiz = exp(-abs(dir.y) * 4.5);
                vec2 xz = dir.xz / max(length(dir.xz), 1e-4);
                float facing = max(dot(xz, uGlowDir), 0.0);
                col += uGlowColor.rgb * (uGlowColor.a * horiz * pow(facing, 3.0));
                FragColor = vec4(col, 1.0);
            }
            """;

    private static final String QUAD_VS = """
            #version 330 core
            layout(location=0) in vec2 aPos;
            uniform mat4 uM;
            out vec2 vUV;
            void main() {
                vUV = aPos * 0.5 + 0.5;
                vec4 p = uM * vec4(aPos, 0.0, 1.0);
                gl_Position = p.xyww;
            }
            """;

    private static final String QUAD_FS = """
            #version 330 core
            in vec2 vUV;
            out vec4 FragColor;
            uniform sampler2D uTex;
            uniform float uAlpha;
            void main() {
                vec4 c = texture(uTex, vUV);
                FragColor = vec4(c.rgb, c.a * uAlpha);
            }
            """;

    private static final String STAR_VS = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            uniform mat4 uPV;
            void main() {
                vec4 p = uPV * vec4(aPos, 1.0);
                gl_Position = p.xyww;
                gl_PointSize = 2.0;
            }
            """;

    private static final String STAR_FS = """
            #version 330 core
            out vec4 FragColor;
            uniform float uAlpha;
            void main() {
                FragColor = vec4(0.85, 0.88, 1.0, uAlpha);
            }
            """;

    private final Shader domeShader = new Shader(DOME_VS, DOME_FS);
    private final Shader quadShader = new Shader(QUAD_VS, QUAD_FS);
    private final Shader starShader = new Shader(STAR_VS, STAR_FS);
    private final int domeVao, domeCount;
    private final int quadVao;
    private final int starVao, starCount;
    private final int sunTex, moonTex;
    private final Matrix4f tmp = new Matrix4f();
    private final Matrix4f model = new Matrix4f();

    public Sky(int sunTex, int moonTex) {
        this.sunTex = sunTex;
        this.moonTex = moonTex;

        // dome: UV sphere
        int stacks = 10, slices = 18;
        float[] sphere = buildSphere(stacks, slices);
        domeCount = sphere.length / 3;
        domeVao = uploadVao(sphere, 3);

        quadVao = uploadVao(new float[]{-1, -1, 1, -1, 1, 1, 1, 1, -1, 1, -1, -1}, 2);

        Random r = new Random(10842);
        float[] stars = new float[1200 * 3];
        for (int i = 0; i < 1200; i++) {
            // random point on unit sphere
            double t = r.nextDouble() * Math.PI * 2;
            double u = r.nextDouble() * 2 - 1;
            double s = Math.sqrt(1 - u * u);
            stars[i * 3] = (float) (s * Math.cos(t));
            stars[i * 3 + 1] = (float) (s * Math.sin(t));
            stars[i * 3 + 2] = (float) u;
        }
        starCount = 1200;
        starVao = uploadVao(stars, 3);
    }

    private static float[] buildSphere(int stacks, int slices) {
        float[][] grid = new float[(stacks + 1) * (slices + 1)][3];
        for (int i = 0; i <= stacks; i++) {
            double phi = Math.PI * i / stacks - Math.PI / 2;   // -90..90
            for (int j = 0; j <= slices; j++) {
                double theta = 2 * Math.PI * j / slices;
                grid[i * (slices + 1) + j] = new float[]{
                        (float) (Math.cos(phi) * Math.cos(theta)),
                        (float) Math.sin(phi),
                        (float) (Math.cos(phi) * Math.sin(theta))
                };
            }
        }
        float[] out = new float[stacks * slices * 6 * 3];
        int k = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                float[][] q = {
                        grid[i * (slices + 1) + j],
                        grid[(i + 1) * (slices + 1) + j],
                        grid[(i + 1) * (slices + 1) + j + 1],
                        grid[i * (slices + 1) + j + 1]
                };
                int[] order = {0, 1, 2, 2, 3, 0};
                for (int oi : order) {
                    out[k++] = q[oi][0];
                    out[k++] = q[oi][1];
                    out[k++] = q[oi][2];
                }
            }
        }
        return out;
    }

    private static int uploadVao(float[] data, int comps) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        fb.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);
        glVertexAttribPointer(0, comps, GL_FLOAT, false, comps * 4, 0);
        glEnableVertexAttribArray(0);
        return vao;
    }

    // ---------- day/night math ----------

    /** 0 at noon... wraps; drives sun rotation. */
    public static float celestialAngle(long time) {
        float frac = (time % DAY_TICKS) / (float) DAY_TICKS - 0.25f;
        if (frac < 0) frac += 1;
        return frac;
    }

    /** 1 at noon, 0 at night. */
    public static float dayFactor(long time) {
        float a = celestialAngle(time);
        float d = (float) (Math.cos(a * Math.PI * 2) * 2.0 + 0.5);
        return Math.max(0, Math.min(1, d));
    }

    /** Sky-light multiplier for the shader: brightness of (15 - skyDarken). */
    public static float dayLight(long time) {
        float d = dayFactor(time);
        float level = (4 + d * 11) / 15f;
        return level / (4f - 3f * level);
    }

    /** Sun direction: rises east (+X), sets west. */
    public static float[] sunDir(long time) {
        double a = celestialAngle(time) * Math.PI * 2;
        return new float[]{(float) -Math.sin(a), (float) Math.cos(a), 0};
    }

    public static float[] skyColor(long time, float[] base) {
        float d = dayFactor(time);
        float scale = d * 0.94f + 0.06f;
        return new float[]{base[0] * scale, base[1] * scale, base[2] * scale};
    }

    public static float[] zenithColor(long time) {
        return skyColor(time, ZENITH);
    }

    public static float[] horizonColor(long time) {
        return skyColor(time, HORIZON);
    }

    /** Sunset/sunrise glow color+alpha; alpha 0 when sun isn't near the horizon. */
    public static float[] glow(long time) {
        double a = celestialAngle(time) * Math.PI * 2;
        double cos = Math.cos(a);
        if (Math.abs(cos) > 0.4) return new float[]{0, 0, 0, 0};
        float f = (float) (cos / 0.4 * 0.5 + 0.5);
        float alpha = (float) (1 - (1 - Math.sin(f * Math.PI)) * 0.99);
        return new float[]{0.7f + f * 0.3f, 0.2f + f * f * 0.7f, 0.2f, alpha * alpha * 0.6f};
    }

    // ---------- rendering ----------

    public void render(Matrix4f proj, Matrix4f viewRot, long time) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        proj.mul(viewRot, tmp);

        float[] zen = zenithColor(time);
        float[] hor = horizonColor(time);
        float[] glow = glow(time);
        float[] sun = sunDir(time);

        domeShader.bind();
        domeShader.setMat4("uPV", tmp);
        domeShader.setVec3("uZenith", zen[0], zen[1], zen[2]);
        domeShader.setVec3("uHorizon", hor[0], hor[1], hor[2]);
        domeShader.setVec4("uGlowColor", glow[0], glow[1], glow[2], glow[3]);
        // glow toward the sun's horizon azimuth (+X at sunrise, -X at sunset)
        float gx = sun[0] >= 0 ? 1 : -1;
        domeShader.setVec2("uGlowDir", gx, 0);
        glBindVertexArray(domeVao);
        glDrawArrays(GL_TRIANGLES, 0, domeCount);

        // stars
        float night = 1 - Math.min(1, dayFactor(time) * 2);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        if (night > 0.01f) {
            double a = celestialAngle(time) * Math.PI * 2;
            starShader.bind();
            Matrix4f starPv = new Matrix4f(tmp).rotateZ((float) -a);
            starShader.setMat4("uPV", starPv);
            starShader.setFloat("uAlpha", night * 0.7f);
            glEnable(GL_PROGRAM_POINT_SIZE);
            glBindVertexArray(starVao);
            glDrawArrays(GL_POINTS, 0, starCount);
        }

        // sun & moon
        drawCelestial(sun, 0.10f, sunTex, 1.0f);
        drawCelestial(new float[]{-sun[0], -sun[1], -sun[2]}, 0.07f, moonTex, 0.9f);

        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void drawCelestial(float[] dir, float size, int tex, float alpha) {
        // orthonormal basis around dir (orbit is in the XY plane, so Z is one tangent)
        float[] t1 = {-dir[1], dir[0], 0};
        float[] t2 = {0, 0, 1};
        model.set(
                t1[0] * size, t1[1] * size, t1[2] * size, 0,
                t2[0] * size, t2[1] * size, t2[2] * size, 0,
                0, 0, 1, 0,
                dir[0], dir[1], dir[2], 1);
        quadShader.bind();
        Matrix4f m = new Matrix4f(tmp).mul(model);
        quadShader.setMat4("uM", m);
        quadShader.setFloat("uAlpha", alpha);
        quadShader.setInt("uTex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
}
