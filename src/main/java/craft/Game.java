package craft;

import craft.player.Player;
import craft.render.Sky;
import craft.render.TextureGen;
import craft.render.WorldRenderer;
import craft.world.Chunk;
import craft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private static final double TICK = 1.0 / 20.0;
    private static final float BASE_FOV = 70f;

    private final long seed;
    private Window window;
    private Input input;
    private World world;
    private WorldRenderer renderer;
    private Sky sky;
    private Player player;
    private ExecutorService pool;

    private int renderDist = 8;
    private long worldTime = Long.getLong("craft.time", 1000);
    private float fovBoost;            // sprint FOV lerp

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f viewRot = new Matrix4f();
    private final Vector3f camPos = new Vector3f();

    public Game(long seed) {
        this.seed = seed;
    }

    private final boolean autopilot = System.getProperty("craft.shot") != null;

    public void run() {
        window = new Window(1280, 760, "Craft");
        input = new Input(window.handle);
        if (!autopilot) input.captureCursor(true);

        TextureGen textures = new TextureGen();
        textures.buildAll();

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "gen-worker");
            t.setDaemon(true);
            return t;
        });

        world = new World(seed, pool);
        renderer = new WorldRenderer(world, pool, textures.atlasTex);
        sky = new Sky(textures.sunTex, textures.moonTex);

        int[] spawn = findSpawn();
        String posProp = System.getProperty("craft.pos");
        if (posProp != null) {
            String[] p = posProp.split(",");
            spawn = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        }
        player = new Player(spawn[0] + 0.5, spawn[1] + 1, spawn[2] + 0.5);
        player.yaw = (float) Math.toRadians(Double.parseDouble(System.getProperty("craft.yaw", "0")));
        player.pitch = (float) Math.toRadians(Double.parseDouble(System.getProperty("craft.pitch", "0")));
        System.out.println("Seed: " + seed + "  Spawn: " + spawn[0] + "," + spawn[1] + "," + spawn[2]);

        glClearColor(0.6f, 0.7f, 1.0f, 1.0f);

        double prev = glfwGetTime();
        double acc = 0;
        int frames = 0;
        double fpsTimer = prev;
        int fps = 0;
        double start = prev;
        String shotPath = System.getProperty("craft.shot");
        double shotDelay = Double.parseDouble(System.getProperty("craft.shotDelay", "12"));
        double exitAfter = Double.parseDouble(System.getProperty("craft.exitAfter", "0"));
        boolean shotTaken = false;

        while (!window.shouldClose()) {
            double now = glfwGetTime();
            acc += Math.min(now - prev, 0.25);
            prev = now;
            if (exitAfter > 0 && now - start >= exitAfter) break;

            while (acc >= TICK) {
                tick();
                acc -= TICK;
            }
            render((float) (acc / TICK));

            if (shotPath != null && !shotTaken && now - start >= shotDelay) {
                shotTaken = true;
                craft.util.Screenshot.capture(window.fbWidth, window.fbHeight, shotPath);
            }
            window.swap();
            glfwPollEvents();

            frames++;
            if (now - fpsTimer >= 1.0) {
                fps = frames;
                frames = 0;
                fpsTimer = now;
                window.setTitle(String.format(
                        "Craft | %d fps | xyz %.1f / %.1f / %.1f | chunks %d | meshes %d (%d pending) | time %d",
                        fps, player.x, player.y, player.z,
                        world.loadedChunkCount(), renderer.sectionCount(), renderer.pendingMeshes(),
                        worldTime % Sky.DAY_TICKS));
            }
        }

        pool.shutdownNow();
        window.destroy();
    }

    /** Nearest land column to the origin (pure noise query, no chunks needed). */
    private int[] findSpawn() {
        for (int r = 0; r < 2000; r += 8) {
            for (int a = 0; a < Math.max(1, r); a += 8) {
                int[][] candidates = r == 0 ? new int[][]{{0, 0}} : new int[][]{
                        {r, a - r}, {-r, r - a}, {a - r, -r}, {r - a, r}
                };
                for (int[] c : candidates) {
                    int h = world.terrainGen.height(c[0], c[1]);
                    if (h >= World.SEA_LEVEL + 2) {
                        return new int[]{c[0], h + 1, c[1]};
                    }
                }
            }
        }
        return new int[]{0, 80, 0};
    }

    private void tick() {
        int key;
        while ((key = input.nextKeyPress()) != -1) {
            if (key == GLFW_KEY_ESCAPE) input.captureCursor(!input.isCursorCaptured());
            if (key == GLFW_KEY_W) player.onKeyPress(key);
            if (key == GLFW_KEY_F2) {
                craft.util.Screenshot.capture(window.fbWidth, window.fbHeight,
                        "screenshot-" + System.currentTimeMillis() + ".png");
            }
        }

        int pcx = (int) Math.floor(player.x) >> 4;
        int pcz = (int) Math.floor(player.z) >> 4;
        world.update(pcx, pcz, renderDist);

        // only simulate once the ground under the player exists
        Chunk c = world.getChunk(pcx, pcz);
        if (c != null && c.state >= Chunk.STATE_DECORATED
                && (input.isCursorCaptured() || autopilot)) {
            player.tick(input, world);
        }

        worldTime++;
    }

    private void render(float partial) {
        if (window.resized) {
            glViewport(0, 0, window.fbWidth, window.fbHeight);
            window.resized = false;
        }

        // mouse look per frame
        if (input.isCursorCaptured()) {
            double[] d = input.consumeMouseDelta();
            player.turn(d[0], d[1], 0.0025);
        }

        // camera (interpolated)
        double ix = player.prevX + (player.x - player.prevX) * partial;
        double iy = player.prevY + (player.y - player.prevY) * partial;
        double iz = player.prevZ + (player.z - player.prevZ) * partial;
        camPos.set((float) ix, (float) (iy + Player.EYE), (float) iz);

        float targetBoost = player.sprinting ? 1f : 0f;
        fovBoost += (targetBoost - fovBoost) * 0.15f;
        float fov = BASE_FOV * (1 + fovBoost * 0.10f);

        float aspect = (float) window.fbWidth / Math.max(1, window.fbHeight);
        float far = Math.max(256, renderDist * 16 + 64);
        proj.identity().perspective((float) Math.toRadians(fov), aspect, 0.05f, far);

        float cosP = (float) Math.cos(player.pitch), sinP = (float) Math.sin(player.pitch);
        float sinY = (float) Math.sin(player.yaw), cosY = (float) Math.cos(player.yaw);
        Vector3f forward = new Vector3f(sinY * cosP, -sinP, -cosY * cosP);
        view.identity().lookAt(camPos, new Vector3f(camPos).add(forward), new Vector3f(0, 1, 0));
        viewRot.set(view).setTranslation(0, 0, 0);

        // fog & clear
        boolean underwater = player.eyeInWater(world);
        float[] fogColor;
        float fogStart, fogEnd;
        if (underwater) {
            fogColor = new float[]{0.08f, 0.15f, 0.45f};
            fogStart = 2;
            fogEnd = 24;
        } else {
            fogColor = Sky.horizonColor(worldTime);
            fogEnd = renderDist * 16;
            fogStart = fogEnd * 0.7f;
        }
        glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (!underwater) {
            sky.render(proj, viewRot, worldTime);
        }

        int pcx = (int) Math.floor(player.x) >> 4;
        int pcz = (int) Math.floor(player.z) >> 4;
        renderer.update(pcx, pcz, renderDist);
        renderer.render(proj, view, camPos, Sky.dayLight(worldTime), fogColor, fogStart, fogEnd);
    }
}
