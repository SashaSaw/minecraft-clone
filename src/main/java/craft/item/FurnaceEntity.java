package craft.item;

import craft.world.Block;
import craft.world.World;

/** Furnace state at a block position. Smelts one item per 200 ticks while fueled. */
public class FurnaceEntity {
    public final int x, y, z;
    public ItemStack input, fuel, output;
    public int burnTime;       // remaining fuel ticks
    public int burnTotal;      // for the flame indicator
    public int cookTime;       // 0..200

    public FurnaceEntity(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static int smeltResult(int id) {
        return switch (id) {
            case Block.SAND -> Block.GLASS;
            case Item.RAW_IRON -> Item.IRON_INGOT;
            case Block.OAK_LOG -> Item.CHARCOAL;
            case Block.COBBLESTONE -> Block.STONE;
            case Item.PORKCHOP -> Item.COOKED_PORKCHOP;
            case Item.BEEF -> Item.COOKED_BEEF;
            case Item.CHICKEN -> Item.COOKED_CHICKEN;
            case Item.MUTTON -> Item.COOKED_MUTTON;
            default -> -1;
        };
    }

    public static int fuelValue(int id) {
        return switch (id) {
            case Item.COAL, Item.CHARCOAL -> 1600;
            case Block.OAK_PLANKS, Block.OAK_LOG, Block.CRAFTING_TABLE -> 300;
            case Item.STICK -> 100;
            case Item.WOOD_PICKAXE, Item.WOOD_AXE, Item.WOOD_SHOVEL, Item.WOOD_SWORD -> 200;
            default -> 0;
        };
    }

    private boolean canSmelt() {
        if (input == null) return false;
        int result = smeltResult(input.id);
        if (result < 0) return false;
        if (output == null) return true;
        return output.id == result && output.count < ItemStack.MAX;
    }

    public void tick(World world) {
        boolean wasBurning = burnTime > 0;
        if (burnTime > 0) burnTime--;

        if (burnTime > 0 && canSmelt()) {
            if (++cookTime >= 200) {
                cookTime = 0;
                int result = smeltResult(input.id);
                if (output == null) output = new ItemStack(result, 1);
                else output.count++;
                if (--input.count <= 0) input = null;
            }
        } else if (burnTime == 0 && canSmelt() && fuel != null) {
            int value = fuelValue(fuel.id);
            if (value > 0) {
                burnTime = burnTotal = value;
                if (--fuel.count <= 0) fuel = null;
            } else {
                cookTime = Math.max(0, cookTime - 2);
            }
        } else {
            cookTime = Math.max(0, cookTime - 2);
        }

        boolean burning = burnTime > 0;
        if (burning != wasBurning) {
            byte want = burning ? Block.FURNACE_LIT : Block.FURNACE;
            if (world.getBlock(x, y, z) != want
                    && (world.getBlock(x, y, z) == Block.FURNACE
                    || world.getBlock(x, y, z) == Block.FURNACE_LIT)) {
                world.setBlock(x, y, z, want);
            }
        }
    }
}
