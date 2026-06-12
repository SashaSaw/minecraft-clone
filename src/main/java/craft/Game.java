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

    private enum State {TITLE, WORLDS, CREATE, RENAME, CONFIRM_DELETE, SETTINGS, PLAYING}

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
    private List<WorldInfo> worldsCache = new ArrayList<>();
    private WorldInfo selectedWorld;
    private final StringBuilder nameField = new StringBuilder();
    private final StringBuilder seedField = new StringBuilder();
    private int focusedField;
    private String activeSlider;
    private float guiScalePreview = 3;
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
        Settings.load();
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
            startWorld(new File("saves", "world-" + initialSeed), initialSeed);
        } else if ("worlds".equals(System.getProperty("craft.menu"))) {
            refreshWorlds();
            state = State.WORLDS;
        } else if ("settings".equals(System.getProperty("craft.menu"))) {
            openSettings();
        } else if ("create".equals(System.getProperty("craft.menu"))) {
            performAction("createScreen");
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

    private void startWorld(File dir, long seed) {
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
            world.save = new SaveManager(dir);
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

    private record WorldInfo(File dir, long seed, String name, long mtime) {
    }

    private record Slider(String id, float x, float y, float w) {
    }

    private void refreshWorlds() {
        List<WorldInfo> out = new ArrayList<>();
        File[] dirs = new File("saves").listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                File level = new File(d, "level.dat");
                if (!d.isDirectory() || !level.exists()) continue;
                long seed = SaveManager.readSeed(d, 0);
                String name = SaveManager.readName(d);
                out.add(new WorldInfo(d, seed, name != null ? name : "WORLD " + seed, level.lastModified()));
            }
        }
        out.sort((a, b) -> Long.compare(b.mtime, a.mtime));
        worldsCache = out.size() > 7 ? new ArrayList<>(out.subList(0, 7)) : out;
    }

    private List<MenuButton> currentButtons() {
        List<MenuButton> btns = new ArrayList<>();
        float cx = ui.screenW / 2f;
        float w = 220, h = 22;
        switch (state) {
            case TITLE -> {
                float y = ui.screenH / 2f - 10;
                btns.add(new MenuButton("play", "SINGLEPLAYER", cx - w / 2, y, w, h, true));
                btns.add(new MenuButton("settings", "SETTINGS", cx - w / 2, y + 30, w, h, true));
                btns.add(new MenuButton("quit", "QUIT GAME", cx - w / 2, y + 60, w, h, true));
            }
            case WORLDS -> {
                float y = 60;
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (int i = 0; i < worldsCache.size(); i++) {
                    WorldInfo e = worldsCache.get(i);
                    btns.add(new MenuButton("world:" + i,
                            e.name + "  (" + fmt.format(new Date(e.mtime)) + ")",
                            cx - 160, y, 240, h, true));
                    btns.add(new MenuButton("ren:" + i, "REN", cx + 84, y, 36, h, true));
                    btns.add(new MenuButton("del:" + i, "DEL", cx + 124, y, 36, h, true));
                    y += 27;
                }
                y += 12;
                btns.add(new MenuButton("createScreen", "CREATE NEW WORLD", cx - w / 2, y, w, h, true));
                btns.add(new MenuButton("back", "BACK", cx - w / 2, y + 30, w, h, true));
            }
            case CREATE -> {
                float y = ui.screenH / 2f + 30;
                btns.add(new MenuButton("docreate", "CREATE WORLD", cx - w / 2, y, w, h, true));
                btns.add(new MenuButton("worlds", "CANCEL", cx - w / 2, y + 30, w, h, true));
            }
            case RENAME -> {
                float y = ui.screenH / 2f + 10;
                btns.add(new MenuButton("dorename", "OK", cx - w / 2, y, w, h, true));
                btns.add(new MenuButton("worlds", "CANCEL", cx - w / 2, y + 30, w, h, true));
            }
            case CONFIRM_DELETE -> {
                float y = ui.screenH / 2f + 10;
                btns.add(new MenuButton("dodelete", "DELETE FOREVER", cx - w / 2, y, w, h, true));
                btns.add(new MenuButton("worlds", "CANCEL", cx - w / 2, y + 30, w, h, true));
            }
            case SETTINGS -> {
                btns.add(new MenuButton("settingsdone", "DONE", cx - w / 2, ui.screenH / 2f + 60, w, h, true));
            }
            case PLAYING -> {
                if (paused) {
                    float y = ui.screenH / 2f - 35;
                    btns.add(new MenuButton("resume", "BACK TO GAME", cx - w / 2, y, w, h, true));
                    btns.add(new MenuButton("settings", "SETTINGS", cx - w / 2, y + 30, w, h, true));
                    btns.add(new MenuButton("savequit", "SAVE AND QUIT TO TITLE", cx - w / 2, y + 60, w, h, true));
                }
            }
        }
        return btns;
    }

    private List<Slider> currentSliders() {
        if (state != State.SETTINGS) return List.of();
        float cx = ui.screenW / 2f, w = 220;
        return List.of(
                new Slider("scale", cx - w / 2, ui.screenH / 2f - 45, w),
                new Slider("bright", cx - w / 2, ui.screenH / 2f + 5, w));
    }

    private float sliderValue(String id) {
        return id.equals("scale") ? (guiScalePreview - 2) / 4f : Settings.brightness;
    }

    private void setSliderValue(String id, float v) {
        v = Settings.clamp01(v);
        if (id.equals("scale")) guiScalePreview = 2 + Math.round(v * 4);
        else Settings.brightness = v;
    }

    private String sliderLabel(String id) {
        return id.equals("scale")
                ? "GUI SCALE: " + (int) guiScalePreview
                : "BRIGHTNESS: " + (int) (Settings.brightness * 100) + "%";
    }

    private void performAction(String id) {
        switch (id) {
            case "play" -> {
                refreshWorlds();
                state = State.WORLDS;
            }
            case "quit" -> glfwSetWindowShouldClose(window.handle, true);
            case "back" -> state = State.TITLE;
            case "worlds" -> {
                refreshWorlds();
                state = State.WORLDS;
            }
            case "createScreen" -> {
                nameField.setLength(0);
                nameField.append("NEW WORLD");
                seedField.setLength(0);
                focusedField = 0;
                state = State.CREATE;
            }
            case "docreate" -> createWorld();
            case "dorename" -> {
                String n = nameField.toString().trim();
                if (selectedWorld != null && !n.isEmpty()) {
                    SaveManager.writeName(selectedWorld.dir, n);
                }
                refreshWorlds();
                state = State.WORLDS;
            }
            case "dodelete" -> {
                if (selectedWorld != null) SaveManager.deleteWorld(selectedWorld.dir);
                refreshWorlds();
                state = State.WORLDS;
            }
            case "settings" -> openSettings();
            case "settingsdone" -> closeSettings();
            case "resume" -> setPaused(false);
            case "savequit" -> quitToTitle();
            default -> {
                int i = Integer.parseInt(id.substring(id.indexOf(':') + 1));
                if (i >= worldsCache.size()) return;
                WorldInfo e = worldsCache.get(i);
                if (id.startsWith("world:")) {
                    startWorld(e.dir, e.seed);
                } else if (id.startsWith("ren:")) {
                    selectedWorld = e;
                    nameField.setLength(0);
                    nameField.append(e.name);
                    focusedField = 0;
                    state = State.RENAME;
                } else if (id.startsWith("del:")) {
                    selectedWorld = e;
                    state = State.CONFIRM_DELETE;
                }
            }
        }
    }

    private void createWorld() {
        String seedText = seedField.toString().trim();
        long seed;
        if (seedText.isEmpty()) {
            seed = new java.util.Random().nextLong();
        } else {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                seed = seedText.hashCode();
            }
        }
        File dir = new File("saves", "world-" + seed);
        int n = 2;
        while (dir.exists()) dir = new File("saves", "world-" + seed + "-" + n++);
        String name = nameField.toString().trim();
        SaveManager.writeName(dir, name.isEmpty() ? "WORLD " + seed : name);
        startWorld(dir, seed);
    }

    private void openSettings() {
        guiScalePreview = Settings.guiScale;
        state = State.SETTINGS;
    }

    private void closeSettings() {
        Settings.guiScale = Settings.clampScale((int) guiScalePreview);
        Settings.save();
        state = world != null ? State.PLAYING : State.TITLE;
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
            input.clearTyped();
            playingEvents();
            return;
        }

        // text input
        boolean typing = state == State.CREATE || state == State.RENAME;
        if (typing) {
            char ch;
            while ((ch = input.nextChar()) != 0) {
                StringBuilder field = focusedField == 0 || state == State.RENAME ? nameField : seedField;
                int max = field == nameField ? 22 : 19;
                if (field.length() < max) field.append(Character.toUpperCase(ch));
            }
        } else {
            input.clearTyped();
        }

        int key;
        while ((key = input.nextKeyPress()) != -1) {
            if (key == GLFW_KEY_ESCAPE) {
                switch (state) {
                    case WORLDS -> state = State.TITLE;
                    case CREATE, RENAME, CONFIRM_DELETE -> {
                        refreshWorlds();
                        state = State.WORLDS;
                    }
                    case SETTINGS -> closeSettings();
                    case PLAYING -> setPaused(false);
                    default -> {
                    }
                }
            } else if (typing && key == GLFW_KEY_BACKSPACE) {
                StringBuilder field = focusedField == 0 || state == State.RENAME ? nameField : seedField;
                if (field.length() > 0) field.setLength(field.length() - 1);
            } else if (typing && key == GLFW_KEY_TAB && state == State.CREATE) {
                focusedField = 1 - focusedField;
            } else if (typing && key == GLFW_KEY_ENTER) {
                performAction(state == State.CREATE ? "docreate" : "dorename");
            }
        }

        int btn;
        while ((btn = input.nextMousePress()) != -1) {
            if (btn != 0) continue;
            int mx = mouseUiX(), my = mouseUiY();
            // slider grab
            boolean grabbed = false;
            for (Slider s : currentSliders()) {
                if (mx >= s.x - 4 && mx < s.x + s.w + 4 && my >= s.y - 4 && my < s.y + 18) {
                    activeSlider = s.id;
                    setSliderValue(s.id, (mx - s.x) / s.w);
                    grabbed = true;
                }
            }
            // text field focus
            if (!grabbed && state == State.CREATE) {
                float[][] fields = fieldRects();
                for (int i = 0; i < fields.length; i++) {
                    float[] f = fields[i];
                    if (mx >= f[0] && mx < f[0] + f[2] && my >= f[1] && my < f[1] + 18) {
                        focusedField = i;
                        grabbed = true;
                    }
                }
            }
            if (!grabbed) menuClick();
        }

        // slider drag / release
        if (activeSlider != null) {
            if (input.isMouseDown(0)) {
                Slider s = null;
                for (Slider c : currentSliders()) if (c.id.equals(activeSlider)) s = c;
                if (s != null) setSliderValue(activeSlider, (mouseUiX() - s.x) / s.w);
            } else {
                if (activeSlider.equals("scale")) {
                    Settings.guiScale = Settings.clampScale((int) guiScalePreview);
                }
                Settings.save();
                activeSlider = null;
            }
        }

        input.consumeMouseDelta();
        input.consumeScroll();
    }

    /** Text field rectangles for the CREATE screen: {x, y, w} per field. */
    private float[][] fieldRects() {
        float cx = ui.screenW / 2f;
        if (state == State.RENAME) {
            return new float[][]{{cx - 110, ui.screenH / 2f - 30, 220}};
        }
        return new float[][]{
                {cx - 110, ui.screenH / 2f - 60, 220},
                {cx - 110, ui.screenH / 2f - 10, 220},
        };
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
        return (int) (input.cursorX() * window.fbWidth / Math.max(1, window.winWidth) / Settings.guiScale);
    }

    private int mouseUiY() {
        return (int) (input.cursorY() * window.fbHeight / Math.max(1, window.winHeight) / Settings.guiScale);
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
        if (world != null) {
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
        switch (state) {
            case TITLE -> {
                ui.textCenteredScaled("CRAFT", cx, ui.screenH * 0.20f, 6, 1f, 1f, 1f);
                ui.textCentered("A MINECRAFT-STYLE VOXEL WORLD", cx, ui.screenH * 0.20f + 54, 0.8f, 0.8f, 0.8f);
            }
            case WORLDS -> {
                ui.textCenteredScaled("SELECT WORLD", cx, 24, 2, 1f, 1f, 1f);
                if (worldsCache.isEmpty()) {
                    ui.textCentered("NO WORLDS YET - CREATE ONE!", cx, 70, 0.75f, 0.75f, 0.75f);
                }
            }
            case CREATE -> {
                ui.textCenteredScaled("CREATE NEW WORLD", cx, ui.screenH / 2f - 110, 2, 1f, 1f, 1f);
                float[][] f = fieldRects();
                ui.text("WORLD NAME", f[0][0], f[0][1] - 12, 0.8f, 0.8f, 0.8f);
                drawField(f[0], nameField.toString(), focusedField == 0);
                ui.text("SEED (LEAVE BLANK FOR RANDOM)", f[1][0], f[1][1] - 12, 0.8f, 0.8f, 0.8f);
                drawField(f[1], seedField.toString(), focusedField == 1);
            }
            case RENAME -> {
                ui.textCenteredScaled("RENAME WORLD", cx, ui.screenH / 2f - 80, 2, 1f, 1f, 1f);
                drawField(fieldRects()[0], nameField.toString(), true);
            }
            case CONFIRM_DELETE -> {
                ui.textCenteredScaled("DELETE WORLD?", cx, ui.screenH / 2f - 80, 2, 1f, 0.45f, 0.4f);
                if (selectedWorld != null) {
                    ui.textCentered("'" + selectedWorld.name + "' WILL BE LOST FOREVER!", cx,
                            ui.screenH / 2f - 40, 0.9f, 0.9f, 0.9f);
                }
            }
            case SETTINGS -> drawSettingsPanel();
            default -> {
            }
        }
        drawMenuButtons(currentButtons(), mouseUiX(), mouseUiY());
        ui.end();
    }

    private void drawField(float[] rect, String text, boolean focused) {
        ui.rect(rect[0] - 1, rect[1] - 1, rect[2] + 2, 18, 0.05f, 0.05f, 0.05f, 1f);
        ui.rect(rect[0], rect[1], rect[2], 16, focused ? 0.10f : 0.18f, focused ? 0.10f : 0.18f, focused ? 0.12f : 0.18f, 1f);
        String t = text;
        if (focused && (System.currentTimeMillis() / 400) % 2 == 0) t += "_";
        ui.text(t, rect[0] + 4, rect[1] + 4, 1, 1, 1);
    }

    private void drawSettingsPanel() {
        ui.textCenteredScaled("SETTINGS", ui.screenW / 2f, ui.screenH / 2f - 95, 2, 1, 1, 1);
        for (Slider s : currentSliders()) {
            ui.textCentered(sliderLabel(s.id), s.x + s.w / 2, s.y - 13, 0.9f, 0.9f, 0.9f);
            ui.rect(s.x - 1, s.y - 1, s.w + 2, 14, 0.05f, 0.05f, 0.05f, 0.9f);
            ui.rect(s.x, s.y, s.w, 12, 0.25f, 0.25f, 0.25f, 1f);
            float hx = s.x + sliderValue(s.id) * (s.w - 8);
            ui.rect(hx, s.y - 2, 8, 16, 0.85f, 0.85f, 0.85f, 1f);
        }
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
            if (state == State.SETTINGS) {
                drawSettingsPanel();
            } else {
                ui.textCenteredScaled("GAME PAUSED", ui.screenW / 2f, ui.screenH / 2f - 70, 2, 1, 1, 1);
            }
            drawMenuButtons(currentButtons(), mouseUiX(), mouseUiY());
        }
        ui.end();
    }
}
