package craft.item;

import craft.world.Block;

import java.util.Random;

/** What breaking a block yields, given the held item. */
public final class BlockDrops {
    public static ItemStack dropFor(Block b, Item held, Random rng) {
        boolean correctTool = toolMatches(b, held);
        if (b.requiresTool && !correctTool) return null;
        return switch (b.id) {
            case Block.GRASS -> new ItemStack(Block.DIRT, 1);
            case Block.STONE -> new ItemStack(Block.COBBLESTONE, 1);
            case Block.OAK_LEAVES -> rng.nextInt(200) == 0 ? new ItemStack(Item.APPLE, 1) : null;
            case Block.GLASS, Block.TALL_GRASS, Block.DANDELION, Block.POPPY -> nullOrSelf(b, rng);
            case Block.COAL_ORE -> new ItemStack(Item.COAL, 1);
            case Block.IRON_ORE -> (held != null && held.tool == Item.Tool.PICKAXE && held.toolSpeed >= 4)
                    ? new ItemStack(Item.RAW_IRON, 1) : null;
            case Block.FURNACE_LIT -> new ItemStack(Block.FURNACE, 1);
            case Block.WATER, Block.BEDROCK, Block.AIR -> null;
            default -> new ItemStack(b.id, 1);
        };
    }

    private static ItemStack nullOrSelf(Block b, Random rng) {
        // glass drops nothing; flowers drop themselves; tall grass drops nothing
        if (b.id == Block.DANDELION || b.id == Block.POPPY) return new ItemStack(b.id, 1);
        return null;
    }

    public static boolean toolMatches(Block b, Item held) {
        if (held == null) return false;
        return switch (b.bestTool) {
            case PICKAXE -> held.tool == Item.Tool.PICKAXE;
            case AXE -> held.tool == Item.Tool.AXE;
            case SHOVEL -> held.tool == Item.Tool.SHOVEL;
            case NONE -> false;
        };
    }

    /** Break damage accumulated per tick (block breaks at 1.0). */
    public static float damagePerTick(Block b, Item held) {
        if (b.hardness < 0) return 0;
        if (b.hardness == 0) return 2;   // instant
        boolean correct = toolMatches(b, held);
        if (b.requiresTool && !correct) {
            return 1f / (b.hardness * 100f);
        }
        float speed = correct ? held.toolSpeed : 1f;
        return speed / (b.hardness * 30f);
    }

    private BlockDrops() {
    }
}
