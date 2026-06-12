package craft.item;

/** Mutable item stack. Max stack 64; tools don't stack. */
public class ItemStack {
    public static final int MAX = 64;

    public int id;
    public int count;

    public ItemStack(int id, int count) {
        this.id = id;
        this.count = count;
    }

    public Item item() {
        return Item.get(id);
    }

    public boolean stackable() {
        return item().tool == Item.Tool.NONE;
    }

    public int maxStack() {
        return stackable() ? MAX : 1;
    }

    public ItemStack copy() {
        return new ItemStack(id, count);
    }

    public boolean canMerge(ItemStack other) {
        return other != null && other.id == id && stackable() && other.stackable();
    }
}
