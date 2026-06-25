package craft.item;

/**
 * Armour rules: which of the 4 equipment slots a piece fits, its protection points, and the
 * resulting damage reduction. Kept as a small static table (like FurnaceEntity.smeltResult) so
 * armour pieces stay plain Items. Each armour point = 4% reduction, capped at 80%.
 */
public final class Armour {
    public static final int SLOTS = 4;          // helmet, chestplate, leggings, boots
    public static final int HELMET = 0, CHESTPLATE = 1, LEGGINGS = 2, BOOTS = 3;

    private Armour() {
    }

    /** Equipment slot an item belongs in (0..3), or -1 if it isn't armour. */
    public static int slot(int id) {
        return switch (id) {
            case Item.LEATHER_HELMET, Item.IRON_HELMET -> HELMET;
            case Item.LEATHER_CHESTPLATE, Item.IRON_CHESTPLATE -> CHESTPLATE;
            case Item.LEATHER_LEGGINGS, Item.IRON_LEGGINGS -> LEGGINGS;
            case Item.LEATHER_BOOTS, Item.IRON_BOOTS -> BOOTS;
            default -> -1;
        };
    }

    public static boolean isArmour(int id) {
        return slot(id) >= 0;
    }

    /** Protection points for a piece (vanilla-ish leather/iron values). */
    public static int points(int id) {
        return switch (id) {
            case Item.LEATHER_HELMET -> 1;
            case Item.LEATHER_CHESTPLATE -> 3;
            case Item.LEATHER_LEGGINGS -> 2;
            case Item.LEATHER_BOOTS -> 1;
            case Item.IRON_HELMET -> 2;
            case Item.IRON_CHESTPLATE -> 6;
            case Item.IRON_LEGGINGS -> 5;
            case Item.IRON_BOOTS -> 2;
            default -> 0;
        };
    }

    /** Sum of protection points across the equipped armour slots. */
    public static int totalPoints(ItemStack[] armour) {
        int p = 0;
        for (ItemStack s : armour) {
            if (s != null) p += points(s.id);
        }
        return p;
    }

    /** Reduces incoming damage by the equipped armour (4% per point, capped at 80%). */
    public static int reduce(int damage, ItemStack[] armour) {
        float r = Math.min(0.8f, totalPoints(armour) * 0.04f);
        return Math.max(0, Math.round(damage * (1 - r)));
    }
}
