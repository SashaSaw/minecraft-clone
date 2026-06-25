package craft.item;

/** A chest block's contents: 27 storage slots at a fixed world position. */
public class ChestEntity {
    public static final int SIZE = 27;
    public final int x, y, z;
    public ItemStack[] contents = new ItemStack[SIZE];

    public ChestEntity(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
