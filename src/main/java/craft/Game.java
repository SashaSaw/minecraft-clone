package craft;

import craft.entity.Entity;
import craft.entity.ItemEntity;
import craft.entity.MobSpawner;
import craft.player.Player;
import craft.render.MobRenderer;
import craft.render.OverlayRenderer;
import craft.render.Sky;
import craft.render.TextureGen;
import craft.render.Tiles;
import craft.render.WorldRenderer;
import craft.ui.Hud;
import craft.ui.Screen;
import craft.ui.UI;
import craft.world.Block;
import craft.world.Chunk;
import craft.world.SaveManager;
import craft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private static final double TICK = 1.0 / 20.0;
    private static final float BASE_FOV = 70f;

    private enum State {TITLE, WORLDS, PLAYING}

    private final Long initialSeed;
    private Window window;
    private Input input;
    private ExecutorService pool;
    private int atlasTex;

    // world-independent
    private Sky sky;
    private OverlayRenderer overlays;
    private MobRenderer mobRenderer;
    private UI ui;
    private Hud hud;

    // per-world (null outside PLAYING)
    private World world;
    private WorldRenderer renderer;
    private Player player;
    private Interaction interaction;
    private MobSpawner spawner;
    private Screen screen;

    private State state = State.TITLE;
    private boolean paused;
    private int renderDist = 8;
    private float fovBoost;
    private boolean showDebug;
    private boolean wasDead;
    private int spawnX, spawnY, spawnZ;
    private int fps;
    private int demoTicks;

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f viewRot = new Matrix4f();
    private final Vector3f camPos = new Vector3f();

    private final boolean autopilot = System.getProperty("craft.shot") != null;

    public Game(Long initialSeed) {
        this.initialSeed = initialSeed;
    }

    public void run() {
        window = new Window(1280, 760, "Craft");
        input = new Input(window.handle);

        TextureGen textures = new TextureGen();
        textures.buildAll();
        atlasTex = textures.atlasTex;

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "gen-worker");
            t.setDaemon(true);
            return t;
        });

        sky = new Sky(textures.sunTex, textures.moonTex);
        overlays = new OverlayRenderer(atlasTex);
        mobRenderer = new MobRenderer(atlasTex);
        ui = new UI(atlasTex);
        hud = new Hud();

        if (initialSeed != null) {
            startWorld(initialSeed);
        } else if ("worlds".equals(System.getProperty("craft.menu"))) {
            state = State.WORLDS;
        }

        double prev = glfwGetTime();
        double acc = 0;
        int frames = 0;
        double fpsTimer = prev;
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

            handleEvents();

            if (state == State.PLAYING && !paused) {
                while (acc >= TICK) {
                    tickWorld();
                    acc -= TICK;
                }
            } else {
                acc = 0;
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
                window.setTitle("Craft | " + fps + " fps");
                if (autopilot && world != null) {
                    System.out.println("fps=" + fps + " chunks=" + world.loadedChunkCount()
                            + " sections=" + renderer.sectionCount() + " entities=" + world.entities.size());
                }
            }
        }

        saveWorld();
        pool.shutdownNow();
        window.destroy();
    }

    // ------------------------------------------------------------------ world lifecycle

    private void startWorld(long seed) {
        world = new World(seed, pool);
        world.time = Long.getLong("craft.time", 1000);
        renderer = new WorldRenderer(world, pool, atlasTex);
        interaction = new Interaction();
        spawner = new MobSpawner();
        screen = null;
        paused = false;
        wasDead = false;
        demoTicks = 0;

        int[] spawn = findSpawn();
        String posProp = System.getProperty("craft.pos");
        if (posProp != null) {
            String[] p = posProp.split(",");
            spawn = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        }
        spawnX = spawn[0];
        spawnY = spawn[1];
        spawnZ = spawn[2];
        player = world.player = new Player(spawnX + 0.5, spawnY + 1, spawnZ + 0.5);
        player.yaw = (float) Math.toRadians(Double.parseDouble(System.getProperty("craft.yaw", "0")));
        player.pitch = (float) Math.toRadians(Double.parseDouble(System.getProperty("craft.pitch", "0")));

        if (!autopilot) {
            world.save = new SaveManager(seed);
            int[] savedSpawn = world.save.loadLevel(world, player);
            if (savedSpawn != null) {
                spawnX = savedSpawn[0];
                spawnY = savedSpawn[1];
                spawnZ = savedSpawn[2];
                System.out.println("Loaded saved world");
            }
            input.captureCursor(true);
        }
        System.out.println("Seed: " + seed + "  Spawn: " + spawnX + "," + spawnY + "," + spawnZ);
        state = State.PLAYING;
    }

    private void saveWorld() {
        if (world != null && world.save != null) {
            world.saveModifiedChunks();
            world.save.saveLevel(world, player, spawnX, spawnY, spawnZ);
            System.out.println("World saved");
        }
    }

    private void quitToTitle() {
        closeScreen();
        saveWorld();
        renderer.dispose();
        world = null;
        renderer = null;
        player = null;
        interaction = null;
        spawner = null;
        paused = false;
        state = State.TITLE;
        input.captureCursor(false);
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

    // ------------------------------------------------------------------ menus

    private record MenuButton(String id, String label, float x, float y, float w, float h, boolean enabled) {
    }

    private record WorldEntry(long seed, long mtime) {
    }

    private List<WorldEntry> listWorlds() {
        List<WorldEntry> out = new ArrayList<>();
        File[] dirs = new File("saves").listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.getName().startsWith("world-")) continue;
                File level = new File(d, "level.dat");
                if (!level.exists()) continue;
                try {
                    out.add(new WorldEntry(Long.parseLong(d.getName().substring(6)), level.lastModified()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.mtime, a.mtime));
        return out.size() > 7 ? out.subList(0, 7) : out;
    }

    private List<MenuButton> currentButtons() {
        List<MenuButton> btns = new ArrayList<>();
        float cx = ui.screenW / 2f;
        float w = 220, h = 22;
        if (state == State.TITLE) {
            float y = ui.screenH / 2f - 10;
            btns.add(new MenuButton("play", "SINGLEPLAYER", cx - w / 2, y, w, h, true));
            btns.add(new MenuButton("quit", "QUIT GAME", cx - w / 2, y + 30, w, h, true));
        } else if (state == State.WORLDS) {
            float y = 60;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (WorldEntry e : listWorlds()) {
                btns.add(new MenuButton("world:" + e.seed,
                        "WORLD " + e.seed + "  (" + fmt.format(new Date(e.mtime)) + ")",
                        cx - w / 2 - 40, y, w + 80, h, true));
                y += 27;
            }
            y += 12;
            btns.add(new MenuButton("new", "CREATE NEW WORLD", cx - w / 2, y, w, h, true));
            btns.add(new MenuButton("back", "BACK", cx - w / 2, y + 30, w, h, true));
        } else if (state == State.PLAYING && paused) {
            float y = ui.screenH / 2f - 35;
            btns.add(new MenuButton("resume", "BACK TO GAME", cx - w / 2, y, w, h, true));
            btns.add(new MenuButton("settings", "SETTINGS (COMING SOON)", cx - w / 2, y + 30, w, h, false));
            btns.add(new MenuButton("savequit", "SAVE AND QUIT TO TITLE", cx - w / 2, y + 60, w, h, true));
        }
        return btns;
    }

    private void performAction(String id) {
        switch (id) {
            case "play" -> state = State.WORLDS;
            case "quit" -> glfwSetWindowShouldClose(window.handle, true);
            case "back" -> state = State.TITLE;
            case "new" -> startWorld(System.nanoTime() & 0xFFFFFFFFL);
            case "resume" -> setPaused(false);
            case "savequit" -> quitToTitle();
            default -> {
                if (id.startsWith("world:")) startWorld(Long.parseLong(id.substring(6)));
            }
        }
    }

    private void setPaused(boolean p) {
        paused = p;
        if (!autopilot) input.captureCursor(!p);
    }

    private void drawMenuButtons(List<MenuButton> btns, int mx, int my) {
        for (MenuButton b : btns) {
            boolean hover = b.enabled && mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
            ui.rect(b.x - 1, b.y - 1, b.w + 2, b.h + 2, 0.05f, 0.05f, 0.05f, 0.9f);
            if (hover) ui.rect(b.x, b.y, b.w, b.h, 0.45f, 0.45f, 0.6f, 0.95f);
            else ui.rect(b.x, b.y, b.w, b.h, 0.28f, 0.28f, 0.28f, 0.95f);
            float c = b.enabled ? 1f : 0.55f;
            ui.textCentered(b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, c, c, hover ? 0.7f : c);
        }
    }

    private void menuClick() {
        int mx = mouseUiX(), my = mouseUiY();
        for (MenuButton b : currentButtons()) {
            if (b.enabled && mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                performAction(b.id);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ events

    private void handleEvents() {
        if (state == State.PLAYING && !paused) {
            playingEvents();
            return;
        }
        // menus and pause screen
        int key;
        while ((key = input.nextKeyPress()) != -1) {
            if (key == GLFW_KEY_ESCAPE) {
                if (state == State.WORLDS) state = State.TITLE;
                else if (state == State.PLAYING && paused) setPaused(false);
            }
        }
        int btn;
        while ((btn = input.nextMousePress()) != -1) {
            if (btn == 0) menuClick();
        }
        input.consumeMouseDelta();
        input.consumeScroll();
    }

    private void playingEvents() {
        int key;
        while ((key = input.nextKeyPress()) != -1) {
            if (key == GLFW_KEY_ESCAPE) {
                if (screen != null) closeScreen();
                else setPaused(true);
            } else if (key == GLFW_KEY_E) {
                if (screen != null) closeScreen();
                else if (!player.dead) openScreen(new Screen.InventoryScreen(player.inventory));
            } else if (key == GLFW_KEY_W) {
                player.onKeyPress(key);
            } else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                player.inventory.selected = key - GLFW_KEY_1;
            } else if (key == GLFW_KEY_F3) {
                showDebug = !showDebug;
            } else if (key == GLFW_KEY_F2) {
                craft.util.Screenshot.capture(window.fbWidth, window.fbHeight,
                        "screenshot-" + System.currentTimeMillis() + ".png");
            } else if (key == GLFW_KEY_Q && screen == null && !player.dead) {
                dropHeldItem();
            }
        }

        double scroll = input.consumeScroll();
        if (scroll != 0 && screen == null) {
            int sel = player.inventory.selected - (int) Math.signum(scroll);
            player.inventory.selected = ((sel % 9) + 9) % 9;
        }

        int btn;
        while ((btn = input.nextMousePress()) != -1) {
            if (player.dead) {
                player.respawn(spawnX, spawnY, spawnZ);
                wasDead = false;
            } else if (screen == null && btn == 0 && (input.isCursorCaptured() || autopilot)) {
                interaction.queueAttack();
            } else if (screen != null) {
                boolean shift = input.isDown(GLFW_KEY_LEFT_SHIFT) || input.isDown(GLFW_KEY_RIGHT_SHIFT);
                screen.click(ui, mouseUiX(), mouseUiY(), btn, shift);
            }
        }
    }

    // ------------------------------------------------------------------ tick

    private void tickWorld() {
        int pcx = (int) Math.floor(player.x) >> 4;
        int pcz = (int) Math.floor(player.z) >> 4;
        world.update(pcx, pcz, renderDist);
        world.tickBlockEntities();
        for (Chunk fc : world.freshlyDecorated) spawner.spawnHerd(world, fc, world.seed);
        world.freshlyDecorated.clear();
        spawner.tickHostile(world);

        Chunk c = world.getChunk(pcx, pcz);
        boolean ready = c != null && c.state >= Chunk.STATE_DECORATED;
        if (ready && System.getProperty("craft.demo") != null) demoTick();
        if (ready) {
            boolean controls = (input.isCursorCaptured() || autopilot) && screen == null && !player.dead;
            player.tick(input, world, controls);
            if (controls) {
                interaction.tick(world, player, input);
                if (interaction.openContainer == Block.CRAFTING_TABLE) {
                    openScreen(new Screen.CraftingScreen(player.inventory));
                } else if (interaction.openContainer == Block.FURNACE) {
                    openScreen(new Screen.FurnaceScreen(player.inventory,
                            world.furnaceAt(interaction.containerX, interaction.containerY, interaction.containerZ)));
                }
            }
            if (!wasDead && player.dead) {
                dropAllInventory();
            }
            wasDead = player.dead;
            tickEntities();
        }

        // autosave every 45 s
        if (world.save != null && world.time % 900 == 0) {
            world.saveModifiedChunks();
            world.save.saveLevel(world, player, spawnX, spawnY, spawnZ);
        }

        world.time++;
    }

    /** Scripted actions for screenshot verification (-Dcraft.demo=world|screen|mobs). */
    private void demoTick() {
        demoTicks++;
        if (demoTicks != 40) return;
        var inv = player.inventory;
        inv.add(new craft.item.ItemStack(Block.COBBLESTONE, 32));
        inv.add(new craft.item.ItemStack(Block.GLASS, 16));
        inv.add(new craft.item.ItemStack(Block.TORCH, 16));
        inv.add(new craft.item.ItemStack(Block.OAK_PLANKS, 32));
        inv.add(new craft.item.ItemStack(craft.item.Item.IRON_PICKAXE, 1));
        inv.add(new craft.item.ItemStack(craft.item.Item.COOKED_BEEF, 5));

        int bx = (int) Math.floor(player.x), bz = (int) Math.floor(player.z);
        int ground = world.surfaceY(bx, bz + 3);
        if ("pause".equals(System.getProperty("craft.demo"))) {
            setPaused(true);
        } else if ("mobs".equals(System.getProperty("craft.demo"))) {
            double my = world.surfaceY(bx, bz + 6) + 1;
            world.entities.add(new craft.entity.Zombie(bx - 6 + 0.5,
                    world.surfaceY(bx - 6, bz + 13) + 1, bz + 13 + 0.5));
            world.entities.add(new craft.entity.Animals.Cow(bx - 2 + 0.5, my, bz + 6 + 0.5));
            world.entities.add(new craft.entity.Animals.Sheep(bx + 0.5, my, bz + 6 + 0.5));
            world.entities.add(new craft.entity.Animals.Pig(bx + 2 + 0.5, my, bz + 6 + 0.5));
            world.entities.add(new craft.entity.Animals.Chicken(bx + 4 + 0.5, my, bz + 6 + 0.5));
        } else if ("world".equals(System.getProperty("craft.demo"))) {
            for (int dx = -1; dx <= 1; dx++) {
                interaction.breakBlock(world, player, bx + dx, ground, bz + 3);
            }
            for (int dx = -2; dx <= 2; dx++) {
                world.setBlock(bx + dx, ground + 1, bz + 6, Block.COBBLESTONE);
                world.setBlock(bx + dx, ground + 2, bz + 6, Block.GLASS);
            }
            world.setBlock(bx - 2, ground + 3, bz + 6, Block.TORCH);
            world.setBlock(bx + 2, ground + 3, bz + 6, Block.TORCH);
            world.setBlock(bx, ground + 1, bz + 4, Block.TORCH);
            world.setBlock(bx + 3, ground + 1, bz + 3, Block.CRAFTING_TABLE);
            world.setBlock(bx - 3, ground + 1, bz + 3, Block.FURNACE_LIT);
        } else {
            openScreen(new Screen.InventoryScreen(inv));
        }
    }

    private int mouseUiX() {
        return (int) (input.cursorX() * window.fbWidth / Math.max(1, window.winWidth) / UI.SCALE);
    }

    private int mouseUiY() {
        return (int) (input.cursorY() * window.fbHeight / Math.max(1, window.winHeight) / UI.SCALE);
    }

    private void openScreen(Screen s) {
        screen = s;
        input.captureCursor(false);
    }

    private void closeScreen() {
        if (screen == null) return;
        for (craft.item.ItemStack st : screen.close()) {
            interaction.spawnDrop(world, (int) Math.floor(player.x), (int) Math.floor(player.y + 1),
                    (int) Math.floor(player.z), st);
        }
        screen = null;
        if (!autopilot) input.captureCursor(true);
    }

    private void dropHeldItem() {
        craft.item.ItemStack held = player.inventory.held();
        if (held == null) return;
        craft.item.ItemStack one = new craft.item.ItemStack(held.id, 1);
        player.inventory.consumeHeld();
        double dx = Math.sin(player.yaw), dz = -Math.cos(player.yaw);
        ItemEntity e = new ItemEntity(player.x + dx * 0.5, player.y + 1.3, player.z + dz * 0.5,
                one, dx * 0.25, 0.05, dz * 0.25);
        e.pickupDelay = 30;
        world.entities.add(e);
    }

    private void dropAllInventory() {
        for (int i = 0; i < player.inventory.slots.length; i++) {
            craft.item.ItemStack st = player.inventory.slots[i];
            if (st != null) {
                interaction.spawnDrop(world, (int) Math.floor(player.x),
                        (int) Math.floor(player.y + 0.5), (int) Math.floor(player.z), st);
                player.inventory.slots[i] = null;
            }
        }
        closeScreen();
    }

    private void tickEntities() {
        Iterator<Entity> it = world.entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            Chunk ec = world.getChunk((int) Math.floor(e.x) >> 4, (int) Math.floor(e.z) >> 4);
            if (ec == null || ec.state < Chunk.STATE_DECORATED) continue;
            e.tick(world);
            if (!e.removed && e instanceof ItemEntity item && item.pickupDelay == 0 && !player.dead) {
                if (e.distSq(player.x, player.y + 0.8, player.z) < 1.6 * 1.6) {
                    item.stack.count = player.inventory.add(item.stack) == 0 ? 0 : item.stack.count;
                    if (item.stack.count == 0) e.removed = true;
                }
            }
            if (e.removed) it.remove();
        }
    }

    // ------------------------------------------------------------------ render

    private void render(float partial) {
        if (window.resized) {
            glViewport(0, 0, window.fbWidth, window.fbHeight);
            window.resized = false;
        }
        if (state == State.PLAYING) {
            renderWorld(partial);
        } else {
            renderMenu();
        }
    }

    private void renderMenu() {
        glClearColor(0.08f, 0.07f, 0.07f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        ui.begin(window.fbWidth, window.fbHeight);

        // tiled, darkened dirt backdrop
        for (int y = 0; y < ui.screenH + 32; y += 32) {
            for (int x = 0; x < ui.screenW + 32; x += 32) {
                ui.tile(Tiles.DIRT, x, y, 32, 32, 0.35f, 0.35f, 0.35f, 1f);
            }
        }

        float cx = ui.screenW / 2f;
        if (state == State.TITLE) {
            ui.textCenteredScaled("CRAFT", cx, ui.screenH * 0.22f, 6, 1f, 1f, 1f);
            ui.textCentered("A MINECRAFT-STYLE VOXEL WORLD", cx, ui.screenH * 0.22f + 54, 0.8f, 0.8f, 0.8f);
        } else if (state == State.WORLDS) {
            ui.textCenteredScaled("SELECT WORLD", cx, 24, 2, 1f, 1f, 1f);
            if (listWorlds().isEmpty()) {
                ui.textCentered("NO WORLDS YET - CREATE ONE!", cx, 70, 0.75f, 0.75f, 0.75f);
            }
        }
        drawMenuButtons(currentButtons(), mouseUiX(), mouseUiY());
        ui.end();
    }

    private void renderWorld(float partial) {
        if (input.isCursorCaptured() && screen == null && !paused) {
            double[] d = input.consumeMouseDelta();
            player.turn(d[0], d[1], 0.0025);
        } else {
            input.consumeMouseDelta();
        }

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

        boolean underwater = player.eyeInWater(world);
        float[] fogColor;
        float fogStart, fogEnd;
        if (underwater) {
            fogColor = new float[]{0.08f, 0.15f, 0.45f};
            fogStart = 2;
            fogEnd = 24;
        } else {
            fogColor = Sky.horizonColor(world.time);
            fogEnd = renderDist * 16;
            fogStart = fogEnd * 0.7f;
        }
        glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (!underwater) {
            sky.render(proj, viewRot, world.time);
        }

        int pcx = (int) Math.floor(player.x) >> 4;
        int pcz = (int) Math.floor(player.z) >> 4;
        renderer.update(pcx, pcz, renderDist);
        float dayLight = Sky.dayLight(world.time);
        renderer.renderSolid(proj, view, camPos, dayLight, fogColor, fogStart, fogEnd);

        overlays.renderItems(renderer.pv(), world, world.entities, dayLight, partial, camPos);
        mobRenderer.render(renderer.pv(), world, world.entities, dayLight, partial, camPos);
        if (screen == null && !paused && !player.dead && interaction.target != null) {
            overlays.renderSelection(renderer.pv(), interaction.target.x, interaction.target.y, interaction.target.z);
            if (interaction.breakProgress > 0) {
                overlays.renderCrack(renderer.pv(), interaction.target.x, interaction.target.y,
                        interaction.target.z, interaction.breakProgress);
            }
        }

        renderer.renderWater(camPos);

        // 2D overlay
        ui.begin(window.fbWidth, window.fbHeight);
        String debug = showDebug ? String.format("XYZ %.1f / %.1f / %.1f  FPS %d  T %d  C %d",
                player.x, player.y, player.z, fps, world.time % Sky.DAY_TICKS, world.loadedChunkCount()) : null;
        hud.render(ui, player, debug);
        if (screen != null) {
            if (screen instanceof Screen.FurnaceScreen fs) fs.sync();
            ui.rect(0, 0, ui.screenW, ui.screenH, 0, 0, 0, 0.45f);
            screen.render(ui, mouseUiX(), mouseUiY());
        }
        if (paused) {
            ui.rect(0, 0, ui.screenW, ui.screenH, 0, 0, 0, 0.55f);
            ui.textCenteredScaled("GAME PAUSED", ui.screenW / 2f, ui.screenH / 2f - 70, 2, 1, 1, 1);
            drawMenuButtons(currentButtons(), mouseUiX(), mouseUiY());
        }
        ui.end();
    }
}
