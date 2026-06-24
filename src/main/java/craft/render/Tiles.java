package craft.render;

/** Atlas tile indices. The atlas is a 16x16 grid of 16px tiles (256x256). */
public final class Tiles {
    public static final int GRASS_TOP = 0;
    public static final int GRASS_SIDE = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int COBBLE = 4;
    public static final int SAND = 5;
    public static final int GRAVEL = 6;
    public static final int BEDROCK = 7;
    public static final int LOG_SIDE = 8;
    public static final int LOG_TOP = 9;
    public static final int PLANKS = 10;
    public static final int LEAVES = 11;
    public static final int WATER = 12;
    public static final int GLASS = 13;
    public static final int COAL_ORE = 14;
    public static final int IRON_ORE = 15;
    public static final int TALL_GRASS = 16;
    public static final int DANDELION = 17;
    public static final int POPPY = 18;
    public static final int SUGAR_CANE = 19;
    public static final int TORCH = 20;
    public static final int CRAFTING_TOP = 21;
    public static final int CRAFTING_SIDE = 22;
    public static final int FURNACE_TOP = 23;
    public static final int FURNACE_FRONT = 24;
    public static final int FURNACE_FRONT_LIT = 25;
    public static final int WOOL_WHITE = 26;

    public static final int CRACK_0 = 30;   // ..CRACK_9 = 39

    public static final int ICON_STICK = 40;
    public static final int ICON_COAL = 41;
    public static final int ICON_IRON_INGOT = 42;
    public static final int ICON_RAW_IRON = 43;
    public static final int ICON_CHARCOAL = 44;
    public static final int ICON_APPLE = 45;
    public static final int ICON_WOOD_PICK = 46;
    public static final int ICON_WOOD_AXE = 47;
    public static final int ICON_WOOD_SHOVEL = 48;
    public static final int ICON_WOOD_SWORD = 49;
    public static final int ICON_STONE_PICK = 50;
    public static final int ICON_STONE_AXE = 51;
    public static final int ICON_STONE_SHOVEL = 52;
    public static final int ICON_STONE_SWORD = 53;
    public static final int ICON_IRON_PICK = 54;
    public static final int ICON_IRON_AXE = 55;
    public static final int ICON_IRON_SHOVEL = 56;
    public static final int ICON_IRON_SWORD = 57;
    public static final int ICON_PORKCHOP = 58;
    public static final int ICON_COOKED_PORKCHOP = 59;
    public static final int ICON_BEEF = 60;
    public static final int ICON_COOKED_BEEF = 61;
    public static final int ICON_CHICKEN = 62;
    public static final int ICON_COOKED_CHICKEN = 63;
    public static final int ICON_MUTTON = 64;
    public static final int ICON_COOKED_MUTTON = 65;
    public static final int ICON_ROTTEN_FLESH = 66;
    public static final int ICON_FEATHER = 67;
    public static final int ICON_LEATHER = 68;
    public static final int ICON_WOOL = 69;
    public static final int WHITE = 70;
    public static final int ICON_HEART = 71;
    public static final int ICON_HEART_HALF = 72;
    public static final int ICON_HEART_EMPTY = 73;
    public static final int ICON_HUNGER = 74;
    public static final int ICON_HUNGER_HALF = 75;
    public static final int ICON_HUNGER_EMPTY = 76;
    public static final int ICON_BUBBLE = 77;

    // first-person viewmodel
    public static final int ARM_SKIN = 78;   // player forearm (skin + sleeve cuff)

    // mob skins
    public static final int ZOMBIE_FACE = 80;
    public static final int ZOMBIE_SKIN = 81;
    public static final int ZOMBIE_SHIRT = 82;
    public static final int ZOMBIE_PANTS = 83;
    public static final int PIG_SKIN = 84;
    public static final int PIG_FACE = 85;
    public static final int COW_BODY = 86;
    public static final int COW_FACE = 87;
    public static final int COW_LEG = 88;
    public static final int SHEEP_WOOL = 89;
    public static final int SHEEP_FACE = 90;
    public static final int SHEEP_LEG = 91;
    public static final int CHICKEN_BODY = 92;
    public static final int CHICKEN_FACE = 93;
    public static final int YELLOW = 94;
    public static final int RED = 95;

    // player skin (third-person body model)
    public static final int PLAYER_FACE = 96;
    public static final int PLAYER_SKIN = 97;
    public static final int PLAYER_SHIRT = 98;
    public static final int PLAYER_PANTS = 99;

    public static final int ATLAS_TILES = 16;     // tiles per row
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = ATLAS_TILES * TILE_PX;

    private Tiles() {
    }
}
