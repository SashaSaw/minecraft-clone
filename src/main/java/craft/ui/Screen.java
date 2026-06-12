package craft.ui;

import craft.item.FurnaceEntity;
import craft.item.Inventory;
import craft.item.ItemStack;
import craft.item.Recipes;
import craft.render.Tiles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Container screens (inventory / crafting table / furnace) with vanilla-ish click rules:
 * left = pick up / place / swap, right = half / place one, shift = quick move.
 */
public abstract class Screen {
    public static class Slot {
        public final int x, y;
        final ItemStack[] arr;
        final int idx;
        public boolean resultOnly;
        public IntPredicate accepts = id -> true;

        Slot(ItemStack[] arr, int idx, int x, int y) {
            this.arr = arr;
            this.idx = idx;
            this.x = x;
            this.y = y;
        }

        public ItemStack get() {
            return arr[idx];
        }

        public void set(ItemStack s) {
            arr[idx] = s;
        }
    }

    public final List<Slot> slots = new ArrayList<>();
    public ItemStack cursor;
    protected final Inventory inv;
    protected int panelW = 176, panelH = 166;

    protected Screen(Inventory inv) {
        this.inv = inv;
    }

    /** Adds the standard 27-slot main grid + 9-slot hotbar. */
    protected void addPlayerSlots() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slots.add(new Slot(inv.slots, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            slots.add(new Slot(inv.slots, c, 8 + c * 18, 142));
        }
    }

    public int panelX(UI ui) {
        return (ui.screenW - panelW) / 2;
    }

    public int panelY(UI ui) {
        return (ui.screenH - panelH) / 2;
    }

    public void render(UI ui, int mouseX, int mouseY) {
        int px = panelX(ui), py = panelY(ui);
        ui.rect(px - 3, py - 3, panelW + 6, panelH + 6, 0.13f, 0.13f, 0.13f, 0.95f);
        ui.rect(px, py, panelW, panelH, 0.78f, 0.78f, 0.78f, 1f);
        renderBg(ui, px, py);
        for (Slot s : slots) {
            ui.slot(px + s.x, py + s.y);
            ui.itemStack(s.get(), px + s.x, py + s.y);
            if (hovered(s, px, py, mouseX, mouseY)) {
                ui.rect(px + s.x, py + s.y, 16, 16, 1, 1, 1, 0.35f);
            }
        }
        if (cursor != null) {
            ui.itemStack(cursor, mouseX - 8, mouseY - 8);
        }
    }

    protected abstract void renderBg(UI ui, int px, int py);

    private boolean hovered(Slot s, int px, int py, int mx, int my) {
        return mx >= px + s.x && mx < px + s.x + 18 && my >= py + s.y && my < py + s.y + 18;
    }

    public void click(UI ui, int mouseX, int mouseY, int button, boolean shift) {
        int px = panelX(ui), py = panelY(ui);
        for (Slot s : slots) {
            if (hovered(s, px, py, mouseX, mouseY)) {
                onSlotClick(s, button, shift);
                onContentsChanged();
                return;
            }
        }
    }

    protected void onSlotClick(Slot s, int button, boolean shift) {
        ItemStack st = s.get();
        if (s.resultOnly) {
            takeResult(s, shift);
            return;
        }
        if (button == 0) {
            if (shift && st != null && cursor == null) {
                quickMove(s);
            } else if (cursor == null) {
                s.set(null);
                cursor = st;
            } else if (st == null) {
                if (s.accepts.test(cursor.id)) {
                    s.set(cursor);
                    cursor = null;
                }
            } else if (st.canMerge(cursor)) {
                int take = Math.min(st.maxStack() - st.count, cursor.count);
                st.count += take;
                cursor.count -= take;
                if (cursor.count == 0) cursor = null;
            } else if (s.accepts.test(cursor.id)) {
                s.set(cursor);
                cursor = st;
            }
        } else if (button == 1) {
            if (cursor == null && st != null) {
                int half = (st.count + 1) / 2;
                cursor = new ItemStack(st.id, half);
                st.count -= half;
                if (st.count == 0) s.set(null);
            } else if (cursor != null && s.accepts.test(cursor.id)) {
                if (st == null) {
                    s.set(new ItemStack(cursor.id, 1));
                    if (--cursor.count == 0) cursor = null;
                } else if (st.canMerge(cursor) && st.count < st.maxStack()) {
                    st.count++;
                    if (--cursor.count == 0) cursor = null;
                }
            }
        }
    }

    /** Default quick-move: container slot -> inventory; inventory <-> hotbar. */
    protected void quickMove(Slot s) {
        ItemStack st = s.get();
        if (st == null) return;
        boolean isPlayerSlot = s.arr == inv.slots;
        if (!isPlayerSlot) {
            if (inv.add(st) == 0) s.set(null);
        } else {
            // between hotbar and main grid
            int from = s.idx;
            int lo = from < 9 ? 9 : 0, hi = from < 9 ? 36 : 9;
            for (int i = lo; i < hi && st.count > 0; i++) {
                ItemStack t = inv.slots[i];
                if (t != null && t.canMerge(st)) {
                    int take = Math.min(t.maxStack() - t.count, st.count);
                    t.count += take;
                    st.count -= take;
                }
            }
            for (int i = lo; i < hi && st.count > 0; i++) {
                if (inv.slots[i] == null) {
                    inv.slots[i] = st.copy();
                    st.count = 0;
                }
            }
            if (st.count == 0) s.set(null);
        }
    }

    protected void takeResult(Slot s, boolean shift) {
    }

    protected void onContentsChanged() {
    }

    /** Returns stacks that must be dropped into the world on close (cursor, craft grid). */
    public List<ItemStack> close() {
        List<ItemStack> out = new ArrayList<>();
        if (cursor != null) {
            out.add(cursor);
            cursor = null;
        }
        return out;
    }

    // ------------------------------------------------------------------

    public static class InventoryScreen extends Screen {
        final ItemStack[] craft = new ItemStack[4];
        final ItemStack[] result = new ItemStack[1];
        Recipes.Recipe current;

        public InventoryScreen(Inventory inv) {
            super(inv);
            addPlayerSlots();
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 2; c++) {
                    slots.add(new Slot(craft, r * 2 + c, 88 + c * 18, 26 + r * 18));
                }
            }
            Slot res = new Slot(result, 0, 148, 35);
            res.resultOnly = true;
            slots.add(res);
            onContentsChanged();
        }

        @Override
        protected void renderBg(UI ui, int px, int py) {
            ui.text("CRAFT", px + 97, py + 10, 0.25f, 0.25f, 0.25f);
            ui.text(">", px + 132, py + 39, 0.25f, 0.25f, 0.25f);
        }

        @Override
        protected void onContentsChanged() {
            current = matchGrid(craft, 2, 2);
            result[0] = current == null ? null : new ItemStack(current.resultId(), current.resultCount());
        }

        @Override
        protected void takeResult(Slot s, boolean shift) {
            craftResult(this, craft, result, () -> current);
        }

        @Override
        public List<ItemStack> close() {
            List<ItemStack> out = super.close();
            for (int i = 0; i < craft.length; i++) {
                if (craft[i] != null) {
                    if (inv.add(craft[i]) > 0) out.add(craft[i]);
                    craft[i] = null;
                }
            }
            result[0] = null;
            return out;
        }
    }

    public static class CraftingScreen extends Screen {
        final ItemStack[] craft = new ItemStack[9];
        final ItemStack[] result = new ItemStack[1];
        Recipes.Recipe current;

        public CraftingScreen(Inventory inv) {
            super(inv);
            addPlayerSlots();
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    slots.add(new Slot(craft, r * 3 + c, 30 + c * 18, 17 + r * 18));
                }
            }
            Slot res = new Slot(result, 0, 124, 35);
            res.resultOnly = true;
            slots.add(res);
        }

        @Override
        protected void renderBg(UI ui, int px, int py) {
            ui.text("CRAFTING", px + 30, py + 6, 0.25f, 0.25f, 0.25f);
            ui.text(">", px + 102, py + 39, 0.25f, 0.25f, 0.25f);
        }

        @Override
        protected void onContentsChanged() {
            current = matchGrid(craft, 3, 3);
            result[0] = current == null ? null : new ItemStack(current.resultId(), current.resultCount());
        }

        @Override
        protected void takeResult(Slot s, boolean shift) {
            craftResult(this, craft, result, () -> current);
        }

        @Override
        public List<ItemStack> close() {
            List<ItemStack> out = super.close();
            for (int i = 0; i < craft.length; i++) {
                if (craft[i] != null) {
                    if (inv.add(craft[i]) > 0) out.add(craft[i]);
                    craft[i] = null;
                }
            }
            result[0] = null;
            return out;
        }
    }

    private static Recipes.Recipe matchGrid(ItemStack[] craft, int w, int h) {
        int[] grid = new int[w * h];
        for (int i = 0; i < craft.length; i++) {
            grid[i] = craft[i] == null ? -1 : craft[i].id;
        }
        return Recipes.match(grid, w, h);
    }

    private static void craftResult(Screen screen, ItemStack[] craft, ItemStack[] result,
                                    java.util.function.Supplier<Recipes.Recipe> recipe) {
        Recipes.Recipe r = recipe.get();
        if (r == null) return;
        ItemStack res = new ItemStack(r.resultId(), r.resultCount());
        if (screen.cursor == null) {
            screen.cursor = res;
        } else if (screen.cursor.canMerge(res)
                && screen.cursor.count + res.count <= screen.cursor.maxStack()) {
            screen.cursor.count += res.count;
        } else {
            return;   // can't take
        }
        for (int i = 0; i < craft.length; i++) {
            if (craft[i] != null && --craft[i].count <= 0) craft[i] = null;
        }
        screen.onContentsChanged();
    }

    // ------------------------------------------------------------------

    public static class FurnaceScreen extends Screen {
        public final FurnaceEntity furnace;
        final ItemStack[] io = new ItemStack[3];   // mirrors furnace fields via sync

        public FurnaceScreen(Inventory inv, FurnaceEntity furnace) {
            super(inv);
            this.furnace = furnace;
            addPlayerSlots();
            Slot in = new Slot(io, 0, 56, 17);
            Slot fuel = new Slot(io, 1, 56, 53);
            fuel.accepts = id -> FurnaceEntity.fuelValue(id) > 0;
            Slot out = new Slot(io, 2, 116, 35);
            out.resultOnly = true;
            slots.add(in);
            slots.add(fuel);
            slots.add(out);
        }

        /** Pull furnace state into the slot array (called each frame before render/click). */
        public void sync() {
            io[0] = furnace.input;
            io[1] = furnace.fuel;
            io[2] = furnace.output;
        }

        private void push() {
            furnace.input = io[0];
            furnace.fuel = io[1];
            furnace.output = io[2];
        }

        @Override
        protected void onSlotClick(Slot s, int button, boolean shift) {
            sync();
            super.onSlotClick(s, button, shift);
            push();
        }

        @Override
        protected void takeResult(Slot s, boolean shift) {
            if (io[2] == null) return;
            if (shift) {
                if (inv.add(io[2]) == 0) io[2] = null;
            } else if (cursor == null) {
                cursor = io[2];
                io[2] = null;
            } else if (cursor.canMerge(io[2])
                    && cursor.count + io[2].count <= cursor.maxStack()) {
                cursor.count += io[2].count;
                io[2] = null;
            }
            push();
        }

        @Override
        protected void renderBg(UI ui, int px, int py) {
            ui.text("FURNACE", px + 60, py + 6, 0.25f, 0.25f, 0.25f);
            // flame indicator
            if (furnace.burnTime > 0 && furnace.burnTotal > 0) {
                float f = furnace.burnTime / (float) furnace.burnTotal;
                int h = Math.max(1, (int) (12 * f));
                ui.rect(px + 57, py + 37 + (12 - h), 12, h, 1f, 0.55f, 0.1f, 1f);
            }
            // progress arrow
            float p = furnace.cookTime / 200f;
            ui.rect(px + 79, py + 38, 24, 6, 0.45f, 0.45f, 0.45f, 1f);
            if (p > 0) ui.rect(px + 79, py + 38, 24 * p, 6, 1f, 1f, 1f, 1f);
        }

        @Override
        public List<ItemStack> close() {
            push();
            return super.close();
        }
    }
}
