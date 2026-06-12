package craft.entity;

import craft.item.Item;
import craft.item.ItemStack;
import craft.world.Block;
import craft.world.World;

/** The four passive mobs. */
public final class Animals {

    public static class Pig extends Mob {
        public Pig(double x, double y, double z) {
            super(0.9, 0.9, 10, 2.5, x, y, z);
        }

        @Override
        protected ItemStack[] deathDrops() {
            return new ItemStack[]{new ItemStack(Item.PORKCHOP, 1 + rng.nextInt(3))};
        }
    }

    public static class Cow extends Mob {
        public Cow(double x, double y, double z) {
            super(0.9, 1.4, 10, 2.0, x, y, z);
        }

        @Override
        protected ItemStack[] deathDrops() {
            return new ItemStack[]{
                    new ItemStack(Item.BEEF, 1 + rng.nextInt(3)),
                    rng.nextInt(3) > 0 ? new ItemStack(Item.LEATHER, 1 + rng.nextInt(2)) : null,
            };
        }
    }

    public static class Sheep extends Mob {
        public int eatTimer;   // head-down grazing animation

        public Sheep(double x, double y, double z) {
            super(0.9, 1.3, 8, 2.3, x, y, z);
        }

        @Override
        protected void aiStep(World world) {
            if (eatTimer > 0) {
                eatTimer--;
                if (eatTimer == 4 && onGrass(world)) {
                    int bx = (int) Math.floor(x), by = (int) Math.floor(y) - 1, bz = (int) Math.floor(z);
                    world.setBlock(bx, by, bz, Block.DIRT);
                }
                return;   // stand still while grazing
            }
            if (panicTicks == 0 && onGround && rng.nextInt(600) == 0 && onGrass(world)) {
                eatTimer = 40;
                return;
            }
            super.aiStep(world);
        }

        @Override
        protected ItemStack[] deathDrops() {
            return new ItemStack[]{
                    new ItemStack(Item.WOOL, 1),
                    new ItemStack(Item.MUTTON, 1 + rng.nextInt(2)),
            };
        }
    }

    public static class Chicken extends Mob {
        public float wingFlap;

        public Chicken(double x, double y, double z) {
            super(0.4, 0.7, 4, 2.5, x, y, z);
        }

        @Override
        protected void applyGravity() {
            vy -= 0.08;
            if (vy < 0) vy *= 0.6;   // wing-brake: slow fall
            vy *= 0.98;
        }

        @Override
        public void tick(World world) {
            super.tick(world);
            if (!onGround && vy < 0) wingFlap += 0.9f;
            fallDistance = 0;        // chickens take no fall damage
        }

        @Override
        protected ItemStack[] deathDrops() {
            return new ItemStack[]{
                    new ItemStack(Item.CHICKEN, 1),
                    rng.nextInt(3) > 0 ? new ItemStack(Item.FEATHER, 1 + rng.nextInt(2)) : null,
            };
        }
    }

    private Animals() {
    }
}
