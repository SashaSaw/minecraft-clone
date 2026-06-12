package craft.item;

import craft.world.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Shaped crafting recipes. Pattern rows use key chars, ' ' = empty.
 * Matching normalizes the placed items to their bounding box and also tries
 * the horizontally mirrored pattern.
 */
public final class Recipes {
    public record Recipe(String[] pattern, String keys, int[] keyIds, int resultId, int resultCount) {
    }

    public static final List<Recipe> ALL = new ArrayList<>();

    private static void reg(int resultId, int count, String keys, int[] ids, String... pattern) {
        ALL.add(new Recipe(pattern, keys, ids, resultId, count));
    }

    static {
        int P = Block.OAK_PLANKS, C = Block.COBBLESTONE, L = Block.OAK_LOG;
        int S = Item.STICK, I = Item.IRON_INGOT, K = Item.COAL, H = Item.CHARCOAL;

        reg(P, 4, "L", new int[]{L}, "L");
        reg(S, 4, "P", new int[]{P}, "P", "P");
        reg(Block.CRAFTING_TABLE, 1, "P", new int[]{P}, "PP", "PP");
        reg(Block.FURNACE, 1, "C", new int[]{C}, "CCC", "C C", "CCC");
        reg(Block.TORCH, 4, "KS", new int[]{K, S}, "K", "S");
        reg(Block.TORCH, 4, "KS", new int[]{H, S}, "K", "S");

        tool(Item.WOOD_PICKAXE, P, S, "MMM", " S ", " S ");
        tool(Item.STONE_PICKAXE, C, S, "MMM", " S ", " S ");
        tool(Item.IRON_PICKAXE, I, S, "MMM", " S ", " S ");
        tool(Item.WOOD_AXE, P, S, "MM", "MS", " S");
        tool(Item.STONE_AXE, C, S, "MM", "MS", " S");
        tool(Item.IRON_AXE, I, S, "MM", "MS", " S");
        tool(Item.WOOD_SHOVEL, P, S, "M", "S", "S");
        tool(Item.STONE_SHOVEL, C, S, "M", "S", "S");
        tool(Item.IRON_SHOVEL, I, S, "M", "S", "S");
        tool(Item.WOOD_SWORD, P, S, "M", "M", "S");
        tool(Item.STONE_SWORD, C, S, "M", "M", "S");
        tool(Item.IRON_SWORD, I, S, "M", "M", "S");
    }

    private static void tool(int result, int material, int stick, String... pattern) {
        reg(result, 1, "MS", new int[]{material, stick}, pattern);
    }

    /**
     * Matches the crafting grid (row-major, w x h, entries are item ids or -1).
     * Returns the recipe or null.
     */
    public static Recipe match(int[] grid, int w, int h) {
        // bounding box of placed items
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (grid[yy * w + xx] >= 0) {
                    minX = Math.min(minX, xx);
                    maxX = Math.max(maxX, xx);
                    minY = Math.min(minY, yy);
                    maxY = Math.max(maxY, yy);
                }
            }
        }
        if (maxX < 0) return null;
        int bw = maxX - minX + 1, bh = maxY - minY + 1;

        for (Recipe r : ALL) {
            if (r.pattern.length != bh) continue;
            int pw = r.pattern[0].length();
            if (pw != bw) continue;
            if (matches(r, grid, w, minX, minY, bw, bh, false)
                    || matches(r, grid, w, minX, minY, bw, bh, true)) {
                return r;
            }
        }
        return null;
    }

    private static boolean matches(Recipe r, int[] grid, int gridW,
                                   int ox, int oy, int bw, int bh, boolean mirror) {
        for (int yy = 0; yy < bh; yy++) {
            for (int xx = 0; xx < bw; xx++) {
                char pc = r.pattern[yy].charAt(mirror ? bw - 1 - xx : xx);
                int want = pc == ' ' ? -1 : r.keyIds[r.keys.indexOf(pc)];
                int got = grid[(oy + yy) * gridW + (ox + xx)];
                if (want != got) return false;
            }
        }
        return true;
    }

    private Recipes() {
    }
}
