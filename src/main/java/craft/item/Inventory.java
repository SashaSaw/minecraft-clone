package craft.item;

/** 36 slots: 0-8 hotbar, 9-35 main grid. */
public class Inventory {
    public final ItemStack[] slots = new ItemStack[36];
    public int selected;

    public ItemStack held() {
        return slots[selected];
    }

    /** Adds a stack, merging then filling empty slots (hotbar first). Returns leftover count. */
    public int add(ItemStack stack) {
        if (stack == null || stack.count <= 0) return 0;
        for (ItemStack s : slots) {
            if (s != null && s.canMerge(stack)) {
                int room = s.maxStack() - s.count;
                int take = Math.min(room, stack.count);
                s.count += take;
                stack.count -= take;
                if (stack.count == 0) return 0;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = stack.copy();
                stack.count = 0;
                return 0;
            }
        }
        return stack.count;
    }

    /** Removes one item from the selected slot. */
    public void consumeHeld() {
        ItemStack h = held();
        if (h == null) return;
        if (--h.count <= 0) slots[selected] = null;
    }
}
