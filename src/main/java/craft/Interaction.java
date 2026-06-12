package craft;

import craft.entity.ItemEntity;
import craft.item.BlockDrops;
import craft.item.Item;
import craft.item.ItemStack;
import craft.player.Player;
import craft.util.Raycast;
import craft.world.Block;
import craft.world.Face;
import craft.world.World;

import java.util.Random;

/** Block targeting, hold-to-break, placing, eating, container opening. */
public class Interaction {
    public static final double REACH = 4.5;

    public Raycast.Hit target;
    public float breakProgress;
    private int breakX = Integer.MIN_VALUE, breakY, breakZ;
    private int useCooldown;
    private final Random rng = new Random();

    /** Set when the player right-clicked a container this tick. */
    public byte openContainer;
    public int containerX, containerY, containerZ;

    public void tick(World world, Player player, Input input) {
        openContainer = 0;
        double cosP = Math.cos(player.pitch), sinP = Math.sin(player.pitch);
        double dx = Math.sin(player.yaw) * cosP;
        double dy = -sinP;
        double dz = -Math.cos(player.yaw) * cosP;
        target = Raycast.cast(world, player.x, player.y + Player.EYE, player.z, dx, dy, dz, REACH);

        if (useCooldown > 0) useCooldown--;

        // breaking
        if (input.isMouseDown(0) && target != null) {
            if (target.x != breakX || target.y != breakY || target.z != breakZ) {
                breakProgress = 0;
                breakX = target.x;
                breakY = target.y;
                breakZ = target.z;
            }
            Block b = Block.get(world.getBlock(target.x, target.y, target.z));
            ItemStack held = player.inventory.held();
            float dmg = BlockDrops.damagePerTick(b, held == null ? null : held.item());
            if (dmg > 0) {
                breakProgress += dmg;
                player.exhaustion += 0.0005f;
                if (breakProgress >= 1f) {
                    breakBlock(world, player, target.x, target.y, target.z);
                    breakProgress = 0;
                    breakX = Integer.MIN_VALUE;
                }
            }
        } else {
            breakProgress = 0;
            breakX = Integer.MIN_VALUE;
        }

        // using / placing / eating
        if (input.isMouseDown(1) && useCooldown == 0) {
            useCooldown = 4;
            use(world, player);
        }
    }

    private void use(World world, Player player) {
        // open containers (unless sneaking)
        if (target != null && !player.sneaking) {
            byte tb = world.getBlock(target.x, target.y, target.z);
            if (tb == Block.CRAFTING_TABLE || tb == Block.FURNACE || tb == Block.FURNACE_LIT) {
                openContainer = tb == Block.CRAFTING_TABLE ? Block.CRAFTING_TABLE : Block.FURNACE;
                containerX = target.x;
                containerY = target.y;
                containerZ = target.z;
                return;
            }
        }

        ItemStack held = player.inventory.held();
        if (held == null) return;
        Item item = held.item();

        // eat
        if (item.foodValue > 0) {
            if (player.eat(item.foodValue)) {
                player.inventory.consumeHeld();
                useCooldown = 24;
            }
            return;
        }

        // place block
        if (item.isBlock() && target != null) {
            byte id = (byte) item.id;
            Block b = Block.get(id);
            int px = target.x + Face.NORMAL[target.face][0];
            int py = target.y + Face.NORMAL[target.face][1];
            int pz = target.z + Face.NORMAL[target.face][2];
            if (py < 1 || py >= craft.world.Chunk.H) return;
            if (!world.blockAt(px, py, pz).replaceable()) return;
            // cross plants need solid ground below
            if (b.layer == Block.Layer.CROSS
                    && !Block.get(world.getBlock(px, py - 1, pz)).opaqueCube) {
                return;
            }
            // can't place a solid block intersecting the player
            if (b.solid && intersectsPlayer(player, px, py, pz)) return;
            world.setBlock(px, py, pz, id);
            player.inventory.consumeHeld();
        }
    }

    private boolean intersectsPlayer(Player p, int bx, int by, int bz) {
        return p.x + p.width / 2 > bx && p.x - p.width / 2 < bx + 1
                && p.y + p.height > by && p.y < by + 1
                && p.z + p.width / 2 > bz && p.z - p.width / 2 < bz + 1;
    }

    public void breakBlock(World world, Player player, int x, int y, int z) {
        Block b = Block.get(world.getBlock(x, y, z));
        if (b.id == Block.AIR || b.hardness < 0) return;
        ItemStack held = player.inventory.held();
        ItemStack drop = BlockDrops.dropFor(b, held == null ? null : held.item(), rng);
        // furnace keeps its contents
        if (b.id == Block.FURNACE || b.id == Block.FURNACE_LIT) {
            craft.item.FurnaceEntity f = world.furnaces.remove(World.posKey(x, y, z));
            if (f != null) {
                spawnDrop(world, x, y, z, f.input);
                spawnDrop(world, x, y, z, f.fuel);
                spawnDrop(world, x, y, z, f.output);
            }
        }
        world.setBlock(x, y, z, Block.AIR);
        spawnDrop(world, x, y, z, drop);
        player.exhaustion += 0.005f;
        // collapse unsupported plants above (tall grass, flowers, cane columns, torches)
        int yy = y + 1;
        while (yy < craft.world.Chunk.H) {
            Block above = Block.get(world.getBlock(x, yy, z));
            if (above.layer != Block.Layer.CROSS) break;
            ItemStack d = BlockDrops.dropFor(above, null, rng);
            world.setBlock(x, yy, z, Block.AIR);
            spawnDrop(world, x, yy, z, d);
            yy++;
        }
    }

    public void spawnDrop(World world, int x, int y, int z, ItemStack stack) {
        if (stack == null || stack.count <= 0) return;
        world.entities.add(new ItemEntity(
                x + 0.5 + (rng.nextDouble() - 0.5) * 0.3,
                y + 0.3,
                z + 0.5 + (rng.nextDouble() - 0.5) * 0.3,
                stack,
                (rng.nextDouble() - 0.5) * 0.1, 0.18, (rng.nextDouble() - 0.5) * 0.1));
    }
}
