package craft.entity;

import craft.item.ItemStack;
import craft.world.World;

/** A dropped item: small billboard with gravity; magnets to the player; despawns after 5 min. */
public class ItemEntity extends Entity {
    public final ItemStack stack;
    public int age;
    public int pickupDelay = 10;

    public ItemEntity(double x, double y, double z, ItemStack stack,
                      double vx, double vy, double vz) {
        super(0.25, 0.25, x, y, z);
        this.stack = stack;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    /** Spawns a drop with a small random toss. */
    public static void scatter(World world, double x, double y, double z,
                               ItemStack stack, java.util.Random rng) {
        if (stack == null || stack.count <= 0) return;
        world.entities.add(new ItemEntity(
                x + (rng.nextDouble() - 0.5) * 0.3, y, z + (rng.nextDouble() - 0.5) * 0.3,
                stack,
                (rng.nextDouble() - 0.5) * 0.1, 0.18, (rng.nextDouble() - 0.5) * 0.1));
    }

    @Override
    public void tick(World world) {
        rememberPosition();
        age++;
        if (pickupDelay > 0) pickupDelay--;
        if (age > 6000) {
            removed = true;
            return;
        }
        if (isInWater(world)) {
            vy = Math.min(vy + 0.04, 0.06);   // float up
            vx *= 0.9;
            vz *= 0.9;
        } else {
            vy -= 0.04;
        }
        move(world, vx, vy, vz);
        double friction = onGround ? 0.6 : 0.98;
        vx *= friction;
        vz *= friction;
        vy *= 0.98;
        if (y < -10) removed = true;
    }
}
