package craft.tools;

import craft.item.Inventory;
import craft.item.Item;
import craft.item.ItemStack;
import craft.item.Recipes;
import craft.item.FurnaceEntity;
import craft.world.Block;

/** Headless sanity tests for crafting/smelting/inventory logic. */
public class LogicTest {
    static int failures = 0;

    public static void main(String[] args) {
        // 1x1 log -> planks anywhere in a 2x2 grid
        check("log->planks", recipe(new int[]{Block.OAK_LOG, -1, -1, -1}, 2, 2), Block.OAK_PLANKS, 4);
        check("log->planks offset", recipe(new int[]{-1, -1, -1, Block.OAK_LOG}, 2, 2), Block.OAK_PLANKS, 4);
        // sticks: planks stacked vertically
        check("sticks", recipe(new int[]{Block.OAK_PLANKS, -1, Block.OAK_PLANKS, -1}, 2, 2), Item.STICK, 4);
        // crafting table 2x2 planks
        check("table", recipe(new int[]{Block.OAK_PLANKS, Block.OAK_PLANKS, Block.OAK_PLANKS, Block.OAK_PLANKS}, 2, 2),
                Block.CRAFTING_TABLE, 1);
        // furnace ring in 3x3
        int C = Block.COBBLESTONE;
        check("furnace", recipe(new int[]{C, C, C, C, -1, C, C, C, C}, 3, 3), Block.FURNACE, 1);
        // wooden pickaxe
        int P = Block.OAK_PLANKS, S = Item.STICK;
        check("wood pick", recipe(new int[]{P, P, P, -1, S, -1, -1, S, -1}, 3, 3), Item.WOOD_PICKAXE, 1);
        // axe + mirrored axe
        check("axe", recipe(new int[]{P, P, -1, P, S, -1, -1, S, -1}, 3, 3), Item.WOOD_AXE, 1);
        check("axe mirrored", recipe(new int[]{-1, P, P, -1, S, P, -1, S, -1}, 3, 3), Item.WOOD_AXE, 1);
        // torch
        check("torch", recipe(new int[]{Item.COAL, -1, Item.STICK, -1}, 2, 2), Block.TORCH, 4);
        // no match
        Recipes.Recipe none = Recipes.match(new int[]{Block.DIRT, -1, -1, -1}, 2, 2);
        if (none != null) fail("dirt should not craft, got " + none.resultId());

        // smelting
        if (FurnaceEntity.smeltResult(Block.SAND) != Block.GLASS) fail("sand->glass");
        if (FurnaceEntity.smeltResult(Item.RAW_IRON) != Item.IRON_INGOT) fail("iron");
        if (FurnaceEntity.fuelValue(Item.COAL) != 1600) fail("coal fuel");

        // inventory merge
        Inventory inv = new Inventory();
        inv.add(new ItemStack(Block.DIRT, 60));
        inv.add(new ItemStack(Block.DIRT, 10));
        int total = 0;
        for (ItemStack s : inv.slots) if (s != null) total += s.count;
        if (total != 70) fail("inventory merge total=" + total);
        if (inv.slots[0].count != 64) fail("first stack should be 64, was " + inv.slots[0].count);

        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    static int[] recipe(int[] grid, int w, int h) {
        Recipes.Recipe r = Recipes.match(grid, w, h);
        return r == null ? null : new int[]{r.resultId(), r.resultCount()};
    }

    static void check(String name, int[] got, int wantId, int wantCount) {
        if (got == null) {
            fail(name + ": no match");
        } else if (got[0] != wantId || got[1] != wantCount) {
            fail(name + ": got " + got[0] + "x" + got[1] + " want " + wantId + "x" + wantCount);
        }
    }

    static void fail(String msg) {
        System.out.println("FAIL " + msg);
        failures++;
    }
}
