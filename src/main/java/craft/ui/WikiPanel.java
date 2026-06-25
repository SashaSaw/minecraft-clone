package craft.ui;

import craft.item.FurnaceEntity;
import craft.item.Item;
import craft.item.Recipes;
import craft.world.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the in-game Wiki: every registered resource (block/item), every crafting recipe,
 * and every smelting recipe + fuel. Everything is enumerated straight from the registries
 * ({@link Item#REGISTRY}, {@link Recipes#ALL}, {@link FurnaceEntity}), so anything newly added
 * to the game shows up here automatically with no manual upkeep.
 */
public final class WikiPanel {
    private WikiPanel() {
    }

    /**
     * Draws the scrollable content. {@code top} is the viewport's top edge; {@code scroll} is how
     * far it's scrolled down. The caller should clip to the viewport. Returns total content height
     * (so the caller can clamp scrolling).
     */
    public static float render(UI ui, float left, float top, float width, float scroll) {
        float y0 = top - scroll;   // screen y of content row 0
        float y = 0;               // local content cursor

        // ---- Resources: every item/block in the registry, two columns ----
        y = header(ui, left, y0, y, "RESOURCES");
        List<Item> items = new ArrayList<>();
        for (Item it : Item.REGISTRY) {
            if (it != null && it.id != Block.AIR) items.add(it);
        }
        float colW = width / 2f;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float rx = left + (i % 2) * colW;
            float ry = y0 + y + (i / 2) * 20;
            ui.tile(displayTile(it), rx, ry, 16, 16);
            ui.text(pretty(it.name), rx + 20, ry + 5, 0.92f, 0.92f, 0.92f);
        }
        y += (items.size() + 1) / 2 * 20 + 10;

        // ---- Crafting recipes ----
        y = header(ui, left, y0, y, "CRAFTING RECIPES");
        for (Recipes.Recipe r : Recipes.ALL) {
            y = recipeRow(ui, left, y0, y, r);
        }
        y += 6;

        // ---- Smelting ----
        y = header(ui, left, y0, y, "SMELTING");
        for (int id = 0; id < Item.REGISTRY.length; id++) {
            int out = FurnaceEntity.smeltResult(id);
            if (out < 0 || Item.get(id) == null || Item.get(out) == null) continue;
            float ry = y0 + y;
            ui.tile(displayTile(Item.get(id)), left, ry, 16, 16);
            ui.text(">", left + 20, ry + 5, 0.8f, 0.8f, 0.8f);
            ui.tile(displayTile(Item.get(out)), left + 34, ry, 16, 16);
            ui.text(pretty(Item.get(out).name), left + 54, ry + 5, 0.92f, 0.92f, 0.92f);
            y += 20;
        }
        y += 6;

        // ---- Fuels (with how many items each smelts) ----
        y = header(ui, left, y0, y, "FUELS");
        for (int id = 0; id < Item.REGISTRY.length; id++) {
            int fuel = FurnaceEntity.fuelValue(id);
            if (fuel <= 0 || Item.get(id) == null) continue;
            float ry = y0 + y;
            Item it = Item.get(id);
            ui.tile(displayTile(it), left, ry, 16, 16);
            float n = fuel / 200f;
            String smelts = (n == Math.floor(n)) ? String.valueOf((int) n) : String.valueOf(n);
            ui.text(pretty(it.name) + "  (SMELTS " + smelts + ")", left + 20, ry + 5, 0.92f, 0.92f, 0.92f);
            y += 20;
        }
        y += 12;
        return y;
    }

    private static float header(UI ui, float left, float y0, float y, String label) {
        y += 8;
        ui.textScaled(label, left, y0 + y, 1.3f, 1f, 0.86f, 0.42f);
        return y + 20;
    }

    /** One crafting recipe: input grid -> output icon + "N x NAME". Returns the new y cursor. */
    private static float recipeRow(UI ui, float left, float y0, float y, Recipes.Recipe r) {
        String[] pat = r.pattern();
        int cols = 0;
        for (String row : pat) cols = Math.max(cols, row.length());
        int rows = pat.length;
        float cell = 13, gx = left, gy = y0 + y;
        for (int ri = 0; ri < rows; ri++) {
            for (int ci = 0; ci < cols; ci++) {
                float x = gx + ci * cell, yy = gy + ri * cell;
                ui.rect(x, yy, cell - 1, cell - 1, 0.16f, 0.16f, 0.16f, 1f);
                char c = ci < pat[ri].length() ? pat[ri].charAt(ci) : ' ';
                if (c != ' ') {
                    int id = r.keyIds()[r.keys().indexOf(c)];
                    ui.tile(displayTile(Item.get(id)), x, yy, cell - 1, cell - 1);
                }
            }
        }
        float midY = gy + rows * cell / 2f - 4;
        float ax = gx + cols * cell + 4;
        ui.text(">", ax, midY, 0.85f, 0.85f, 0.85f);
        Item out = Item.get(r.resultId());
        float ox = ax + 14;
        ui.tile(displayTile(out), ox, midY - 4, 16, 16);
        ui.text(r.resultCount() + " X " + pretty(out.name), ox + 20, midY, 1f, 1f, 1f);
        return y + Math.max(rows * cell, 16) + 9;
    }

    /** Atlas tile to show for an item: block items use their side face, others their icon. */
    private static int displayTile(Item it) {
        return it.isBlock() ? it.block().tileSide : it.icon;
    }

    private static String pretty(String name) {
        return name.replace('_', ' ');   // Font renders uppercase
    }
}
