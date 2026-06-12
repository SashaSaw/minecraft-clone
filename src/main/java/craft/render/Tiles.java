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

    public static final int ATLAS_TILES = 16;     // tiles per row
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = ATLAS_TILES * TILE_PX;

    private Tiles() {
    }
}
