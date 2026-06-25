package craft.item;

import craft.render.Tiles;
import craft.world.Block;

/**
 * Item registry. Ids 0..31 are the block ids (block items); 32+ are pure items.
 * Tools have no durability (deliberate simplification).
 */
public class Item {
    public enum Tool {NONE, PICKAXE, AXE, SHOVEL, SWORD}

    public static final int STICK = 32;
    public static final int COAL = 33;
    public static final int IRON_INGOT = 34;
    public static final int RAW_IRON = 35;
    public static final int WOOD_PICKAXE = 36;
    public static final int WOOD_AXE = 37;
    public static final int WOOD_SHOVEL = 38;
    public static final int WOOD_SWORD = 39;
    public static final int STONE_PICKAXE = 40;
    public static final int STONE_AXE = 41;
    public static final int STONE_SHOVEL = 42;
    public static final int STONE_SWORD = 43;
    public static final int IRON_PICKAXE = 44;
    public static final int IRON_AXE = 45;
    public static final int IRON_SHOVEL = 46;
    public static final int IRON_SWORD = 47;
    public static final int APPLE = 48;
    public static final int CHARCOAL = 49;
    // food items from mobs (phase 3 spawns the mobs; items exist now)
    public static final int PORKCHOP = 50;
    public static final int COOKED_PORKCHOP = 51;
    public static final int BEEF = 52;
    public static final int COOKED_BEEF = 53;
    public static final int CHICKEN = 54;
    public static final int COOKED_CHICKEN = 55;
    public static final int MUTTON = 56;
    public static final int COOKED_MUTTON = 57;
    public static final int ROTTEN_FLESH = 58;
    public static final int FEATHER = 59;
    public static final int LEATHER = 60;
    public static final int WOOL = 61;   // placeable? kept as item for simplicity
    public static final int LEATHER_HELMET = 62;
    public static final int LEATHER_CHESTPLATE = 63;
    public static final int LEATHER_LEGGINGS = 64;
    public static final int LEATHER_BOOTS = 65;
    public static final int IRON_HELMET = 66;
    public static final int IRON_CHESTPLATE = 67;
    public static final int IRON_LEGGINGS = 68;
    public static final int IRON_BOOTS = 69;

    public static final Item[] REGISTRY = new Item[128];

    public final int id;
    public final String name;
    public final int icon;          // atlas tile for UI; -1 for block items (drawn from block tiles)
    public final Tool tool;
    public final int toolSpeed;     // 2 wood, 4 stone, 6 iron
    public final int foodValue;     // hunger points restored; 0 = not food
    public final int attackDamage;  // melee damage bonus

    private Item(int id, String name, int icon, Tool tool, int toolSpeed, int foodValue, int attackDamage) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.tool = tool;
        this.toolSpeed = toolSpeed;
        this.foodValue = foodValue;
        this.attackDamage = attackDamage;
    }

    public boolean isBlock() {
        return id < 32;
    }

    public Block block() {
        return Block.get((byte) id);
    }

    public static Item get(int id) {
        return REGISTRY[id];
    }

    private static void reg(int id, String name, int icon, Tool tool, int speed, int food, int dmg) {
        REGISTRY[id] = new Item(id, name, icon, tool, speed, food, dmg);
    }

    static {
        // block items
        for (int i = 0; i < 32; i++) {
            Block b = Block.REGISTRY[i];
            if (b != null) reg(i, b.name, -1, Tool.NONE, 0, 0, 0);
        }
        reg(STICK, "stick", Tiles.ICON_STICK, Tool.NONE, 0, 0, 0);
        reg(COAL, "coal", Tiles.ICON_COAL, Tool.NONE, 0, 0, 0);
        reg(IRON_INGOT, "iron_ingot", Tiles.ICON_IRON_INGOT, Tool.NONE, 0, 0, 0);
        reg(RAW_IRON, "raw_iron", Tiles.ICON_RAW_IRON, Tool.NONE, 0, 0, 0);
        reg(CHARCOAL, "charcoal", Tiles.ICON_CHARCOAL, Tool.NONE, 0, 0, 0);
        reg(APPLE, "apple", Tiles.ICON_APPLE, Tool.NONE, 0, 4, 0);

        reg(WOOD_PICKAXE, "wooden_pickaxe", Tiles.ICON_WOOD_PICK, Tool.PICKAXE, 2, 0, 1);
        reg(WOOD_AXE, "wooden_axe", Tiles.ICON_WOOD_AXE, Tool.AXE, 2, 0, 2);
        reg(WOOD_SHOVEL, "wooden_shovel", Tiles.ICON_WOOD_SHOVEL, Tool.SHOVEL, 2, 0, 1);
        reg(WOOD_SWORD, "wooden_sword", Tiles.ICON_WOOD_SWORD, Tool.SWORD, 1, 0, 3);
        reg(STONE_PICKAXE, "stone_pickaxe", Tiles.ICON_STONE_PICK, Tool.PICKAXE, 4, 0, 2);
        reg(STONE_AXE, "stone_axe", Tiles.ICON_STONE_AXE, Tool.AXE, 4, 0, 3);
        reg(STONE_SHOVEL, "stone_shovel", Tiles.ICON_STONE_SHOVEL, Tool.SHOVEL, 4, 0, 2);
        reg(STONE_SWORD, "stone_sword", Tiles.ICON_STONE_SWORD, Tool.SWORD, 1, 0, 4);
        reg(IRON_PICKAXE, "iron_pickaxe", Tiles.ICON_IRON_PICK, Tool.PICKAXE, 6, 0, 3);
        reg(IRON_AXE, "iron_axe", Tiles.ICON_IRON_AXE, Tool.AXE, 6, 0, 4);
        reg(IRON_SHOVEL, "iron_shovel", Tiles.ICON_IRON_SHOVEL, Tool.SHOVEL, 6, 0, 3);
        reg(IRON_SWORD, "iron_sword", Tiles.ICON_IRON_SWORD, Tool.SWORD, 1, 0, 6);

        reg(PORKCHOP, "raw_porkchop", Tiles.ICON_PORKCHOP, Tool.NONE, 0, 3, 0);
        reg(COOKED_PORKCHOP, "cooked_porkchop", Tiles.ICON_COOKED_PORKCHOP, Tool.NONE, 0, 8, 0);
        reg(BEEF, "raw_beef", Tiles.ICON_BEEF, Tool.NONE, 0, 3, 0);
        reg(COOKED_BEEF, "steak", Tiles.ICON_COOKED_BEEF, Tool.NONE, 0, 8, 0);
        reg(CHICKEN, "raw_chicken", Tiles.ICON_CHICKEN, Tool.NONE, 0, 2, 0);
        reg(COOKED_CHICKEN, "cooked_chicken", Tiles.ICON_COOKED_CHICKEN, Tool.NONE, 0, 6, 0);
        reg(MUTTON, "raw_mutton", Tiles.ICON_MUTTON, Tool.NONE, 0, 2, 0);
        reg(COOKED_MUTTON, "cooked_mutton", Tiles.ICON_COOKED_MUTTON, Tool.NONE, 0, 6, 0);
        reg(ROTTEN_FLESH, "rotten_flesh", Tiles.ICON_ROTTEN_FLESH, Tool.NONE, 0, 2, 0);
        reg(FEATHER, "feather", Tiles.ICON_FEATHER, Tool.NONE, 0, 0, 0);
        reg(LEATHER, "leather", Tiles.ICON_LEATHER, Tool.NONE, 0, 0, 0);
        reg(WOOL, "wool", Tiles.ICON_WOOL, Tool.NONE, 0, 0, 0);

        reg(LEATHER_HELMET, "leather_helmet", Tiles.ICON_LEATHER_HELMET, Tool.NONE, 0, 0, 0);
        reg(LEATHER_CHESTPLATE, "leather_chestplate", Tiles.ICON_LEATHER_CHESTPLATE, Tool.NONE, 0, 0, 0);
        reg(LEATHER_LEGGINGS, "leather_leggings", Tiles.ICON_LEATHER_LEGGINGS, Tool.NONE, 0, 0, 0);
        reg(LEATHER_BOOTS, "leather_boots", Tiles.ICON_LEATHER_BOOTS, Tool.NONE, 0, 0, 0);
        reg(IRON_HELMET, "iron_helmet", Tiles.ICON_IRON_HELMET, Tool.NONE, 0, 0, 0);
        reg(IRON_CHESTPLATE, "iron_chestplate", Tiles.ICON_IRON_CHESTPLATE, Tool.NONE, 0, 0, 0);
        reg(IRON_LEGGINGS, "iron_leggings", Tiles.ICON_IRON_LEGGINGS, Tool.NONE, 0, 0, 0);
        reg(IRON_BOOTS, "iron_boots", Tiles.ICON_IRON_BOOTS, Tool.NONE, 0, 0, 0);
    }
}
