package craft.world;

import craft.render.Tiles;

/**
 * Static block registry. Block ids are bytes stored directly in chunk arrays.
 * Tiles reference indices in the procedurally generated texture atlas (see TextureGen).
 */
public final class Block {
    public enum Layer {NONE, SOLID, CUTOUT, CROSS, WATER}

    public enum Tool {NONE, PICKAXE, AXE, SHOVEL}

    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte COBBLESTONE = 4;
    public static final byte SAND = 5;
    public static final byte GRAVEL = 6;
    public static final byte BEDROCK = 7;
    public static final byte OAK_LOG = 8;
    public static final byte OAK_LEAVES = 9;
    public static final byte OAK_PLANKS = 10;
    public static final byte WATER = 11;
    public static final byte GLASS = 12;
    public static final byte COAL_ORE = 13;
    public static final byte IRON_ORE = 14;
    public static final byte TALL_GRASS = 15;
    public static final byte DANDELION = 16;
    public static final byte POPPY = 17;
    public static final byte SUGAR_CANE = 18;
    public static final byte TORCH = 19;
    public static final byte CRAFTING_TABLE = 20;
    public static final byte FURNACE = 21;
    public static final byte FURNACE_LIT = 22;

    public static final Block[] REGISTRY = new Block[32];

    public final byte id;
    public final String name;
    public final Layer layer;
    public final boolean solid;        // has collision
    public final boolean opaqueCube;   // culls neighbor faces entirely
    public final int opacity;          // light attenuation 0..15
    public final int emission;         // emitted block light 0..15
    public final float hardness;       // break-time scale; -1 unbreakable, 0 instant
    public final boolean cullSame;     // cull faces against same block id (water, glass)
    public final Tool bestTool;
    public final boolean requiresTool; // no drops without the right tool
    public final int tileTop, tileSide, tileBottom;

    private Block(byte id, String name, Layer layer, boolean solid, boolean opaqueCube,
                  int opacity, int emission, float hardness, boolean cullSame,
                  Tool bestTool, boolean requiresTool,
                  int tileTop, int tileSide, int tileBottom) {
        this.id = id;
        this.name = name;
        this.layer = layer;
        this.solid = solid;
        this.opaqueCube = opaqueCube;
        this.opacity = opacity;
        this.emission = emission;
        this.hardness = hardness;
        this.cullSame = cullSame;
        this.bestTool = bestTool;
        this.requiresTool = requiresTool;
        this.tileTop = tileTop;
        this.tileSide = tileSide;
        this.tileBottom = tileBottom;
    }

    private static void reg(byte id, String name, Layer layer, boolean solid, boolean opaqueCube,
                            int opacity, int emission, float hardness, boolean cullSame,
                            Tool bestTool, boolean requiresTool,
                            int tileTop, int tileSide, int tileBottom) {
        REGISTRY[id] = new Block(id, name, layer, solid, opaqueCube, opacity, emission,
                hardness, cullSame, bestTool, requiresTool, tileTop, tileSide, tileBottom);
    }

    public static Block get(byte id) {
        Block b = REGISTRY[id & 0xFF];
        return b != null ? b : REGISTRY[AIR];
    }

    static {
        reg(AIR, "air", Layer.NONE, false, false, 0, 0, 0, false, Tool.NONE, false, 0, 0, 0);
        reg(GRASS, "grass_block", Layer.SOLID, true, true, 15, 0, 0.6f, false, Tool.SHOVEL, false,
                Tiles.GRASS_TOP, Tiles.GRASS_SIDE, Tiles.DIRT);
        reg(DIRT, "dirt", Layer.SOLID, true, true, 15, 0, 0.5f, false, Tool.SHOVEL, false,
                Tiles.DIRT, Tiles.DIRT, Tiles.DIRT);
        reg(STONE, "stone", Layer.SOLID, true, true, 15, 0, 1.5f, false, Tool.PICKAXE, true,
                Tiles.STONE, Tiles.STONE, Tiles.STONE);
        reg(COBBLESTONE, "cobblestone", Layer.SOLID, true, true, 15, 0, 2.0f, false, Tool.PICKAXE, true,
                Tiles.COBBLE, Tiles.COBBLE, Tiles.COBBLE);
        reg(SAND, "sand", Layer.SOLID, true, true, 15, 0, 0.5f, false, Tool.SHOVEL, false,
                Tiles.SAND, Tiles.SAND, Tiles.SAND);
        reg(GRAVEL, "gravel", Layer.SOLID, true, true, 15, 0, 0.6f, false, Tool.SHOVEL, false,
                Tiles.GRAVEL, Tiles.GRAVEL, Tiles.GRAVEL);
        reg(BEDROCK, "bedrock", Layer.SOLID, true, true, 15, 0, -1f, false, Tool.NONE, false,
                Tiles.BEDROCK, Tiles.BEDROCK, Tiles.BEDROCK);
        reg(OAK_LOG, "oak_log", Layer.SOLID, true, true, 15, 0, 2.0f, false, Tool.AXE, false,
                Tiles.LOG_TOP, Tiles.LOG_SIDE, Tiles.LOG_TOP);
        reg(OAK_LEAVES, "oak_leaves", Layer.CUTOUT, true, false, 1, 0, 0.2f, false, Tool.NONE, false,
                Tiles.LEAVES, Tiles.LEAVES, Tiles.LEAVES);
        reg(OAK_PLANKS, "oak_planks", Layer.SOLID, true, true, 15, 0, 2.0f, false, Tool.AXE, false,
                Tiles.PLANKS, Tiles.PLANKS, Tiles.PLANKS);
        reg(WATER, "water", Layer.WATER, false, false, 2, 0, -1f, true, Tool.NONE, false,
                Tiles.WATER, Tiles.WATER, Tiles.WATER);
        reg(GLASS, "glass", Layer.CUTOUT, true, false, 0, 0, 0.3f, true, Tool.NONE, false,
                Tiles.GLASS, Tiles.GLASS, Tiles.GLASS);
        reg(COAL_ORE, "coal_ore", Layer.SOLID, true, true, 15, 0, 3.0f, false, Tool.PICKAXE, true,
                Tiles.COAL_ORE, Tiles.COAL_ORE, Tiles.COAL_ORE);
        reg(IRON_ORE, "iron_ore", Layer.SOLID, true, true, 15, 0, 3.0f, false, Tool.PICKAXE, true,
                Tiles.IRON_ORE, Tiles.IRON_ORE, Tiles.IRON_ORE);
        reg(TALL_GRASS, "tall_grass", Layer.CROSS, false, false, 0, 0, 0f, false, Tool.NONE, false,
                Tiles.TALL_GRASS, Tiles.TALL_GRASS, Tiles.TALL_GRASS);
        reg(DANDELION, "dandelion", Layer.CROSS, false, false, 0, 0, 0f, false, Tool.NONE, false,
                Tiles.DANDELION, Tiles.DANDELION, Tiles.DANDELION);
        reg(POPPY, "poppy", Layer.CROSS, false, false, 0, 0, 0f, false, Tool.NONE, false,
                Tiles.POPPY, Tiles.POPPY, Tiles.POPPY);
        reg(SUGAR_CANE, "sugar_cane", Layer.CROSS, false, false, 0, 0, 0f, false, Tool.NONE, false,
                Tiles.SUGAR_CANE, Tiles.SUGAR_CANE, Tiles.SUGAR_CANE);
        reg(TORCH, "torch", Layer.CROSS, false, false, 0, 14, 0f, false, Tool.NONE, false,
                Tiles.TORCH, Tiles.TORCH, Tiles.TORCH);
        reg(CRAFTING_TABLE, "crafting_table", Layer.SOLID, true, true, 15, 0, 2.5f, false, Tool.AXE, false,
                Tiles.CRAFTING_TOP, Tiles.CRAFTING_SIDE, Tiles.PLANKS);
        reg(FURNACE, "furnace", Layer.SOLID, true, true, 15, 0, 3.5f, false, Tool.PICKAXE, true,
                Tiles.FURNACE_TOP, Tiles.FURNACE_FRONT, Tiles.FURNACE_TOP);
        reg(FURNACE_LIT, "lit_furnace", Layer.SOLID, true, true, 15, 13, 3.5f, false, Tool.PICKAXE, true,
                Tiles.FURNACE_TOP, Tiles.FURNACE_FRONT_LIT, Tiles.FURNACE_TOP);
    }

    /** True if a plant/water can replace this block when placing/decorating. */
    public boolean replaceable() {
        return id == AIR || id == WATER || (layer == Layer.CROSS && id != TORCH);
    }

    /** Tile index for face: 0=top, 1..4=sides, 5=bottom. */
    public int tileFor(int face) {
        if (face == Face.TOP) return tileTop;
        if (face == Face.BOTTOM) return tileBottom;
        return tileSide;
    }
}
