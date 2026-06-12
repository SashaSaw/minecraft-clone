package craft.world;

import craft.item.FurnaceEntity;
import craft.item.ItemStack;
import craft.player.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * World persistence: saves/<seed>/level.dat (time, player, spawn, furnaces) and
 * c.<cx>.<cz>.bin per player-modified chunk (run-length-encoded blocks, gzipped).
 * Unmodified chunks regenerate from the seed. Entities are not persisted.
 */
public class SaveManager {
    private static final int LEVEL_VERSION = 1;
    private final File dir;

    public SaveManager(long seed) {
        dir = new File("saves", "world-" + seed);
        dir.mkdirs();
    }

    private File chunkFile(int cx, int cz) {
        return new File(dir, "c." + cx + "." + cz + ".bin");
    }

    public boolean hasChunk(int cx, int cz) {
        return chunkFile(cx, cz).exists();
    }

    public Chunk loadChunk(int cx, int cz) {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new FileInputStream(chunkFile(cx, cz))))) {
            Chunk c = new Chunk(cx, cz);
            int i = 0;
            while (i < c.blocks.length) {
                int run = in.readUnsignedShort();
                byte id = in.readByte();
                for (int j = 0; j < run; j++) c.blocks[i++] = id;
            }
            c.state = Chunk.STATE_TERRAIN;
            c.diskLoaded = true;
            c.modified = true;   // keep saving it on unload
            return c;
        } catch (IOException e) {
            System.err.println("Failed to load chunk " + cx + "," + cz + ": " + e);
            return null;
        }
    }

    public void saveChunk(Chunk c) {
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new FileOutputStream(chunkFile(c.cx, c.cz))))) {
            int i = 0;
            while (i < c.blocks.length) {
                byte id = c.blocks[i];
                int run = 1;
                while (i + run < c.blocks.length && c.blocks[i + run] == id && run < 65535) run++;
                out.writeShort(run);
                out.writeByte(id);
                i += run;
            }
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + c.cx + "," + c.cz + ": " + e);
        }
    }

    public boolean hasLevel() {
        return new File(dir, "level.dat").exists();
    }

    public void saveLevel(World world, Player player, int spawnX, int spawnY, int spawnZ) {
        try (DataOutputStream out = new DataOutputStream(
                new FileOutputStream(new File(dir, "level.dat")))) {
            out.writeInt(LEVEL_VERSION);
            out.writeLong(world.time);
            out.writeInt(spawnX);
            out.writeInt(spawnY);
            out.writeInt(spawnZ);
            out.writeDouble(player.x);
            out.writeDouble(player.y);
            out.writeDouble(player.z);
            out.writeFloat(player.yaw);
            out.writeFloat(player.pitch);
            out.writeInt(player.hp);
            out.writeInt(player.hunger);
            out.writeFloat(player.saturation);
            out.writeInt(player.air);
            for (ItemStack s : player.inventory.slots) {
                out.writeInt(s == null ? -1 : s.id);
                out.writeInt(s == null ? 0 : s.count);
            }
            out.writeInt(world.furnaces.size());
            for (FurnaceEntity f : world.furnaces.values()) {
                out.writeInt(f.x);
                out.writeInt(f.y);
                out.writeInt(f.z);
                writeStack(out, f.input);
                writeStack(out, f.fuel);
                writeStack(out, f.output);
                out.writeInt(f.burnTime);
                out.writeInt(f.burnTotal);
                out.writeInt(f.cookTime);
            }
        } catch (IOException e) {
            System.err.println("Failed to save level: " + e);
        }
    }

    /** Returns {spawnX, spawnY, spawnZ} or null if no save. Mutates world/player state. */
    public int[] loadLevel(World world, Player player) {
        File f = new File(dir, "level.dat");
        if (!f.exists()) return null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            if (in.readInt() != LEVEL_VERSION) return null;
            world.time = in.readLong();
            int[] spawn = {in.readInt(), in.readInt(), in.readInt()};
            player.x = in.readDouble();
            player.y = in.readDouble();
            player.z = in.readDouble();
            player.prevX = player.x;
            player.prevY = player.y;
            player.prevZ = player.z;
            player.yaw = in.readFloat();
            player.pitch = in.readFloat();
            player.hp = in.readInt();
            player.hunger = in.readInt();
            player.saturation = in.readFloat();
            player.air = in.readInt();
            for (int i = 0; i < player.inventory.slots.length; i++) {
                int id = in.readInt();
                int count = in.readInt();
                player.inventory.slots[i] = id < 0 ? null : new ItemStack(id, count);
            }
            int nf = in.readInt();
            for (int i = 0; i < nf; i++) {
                FurnaceEntity fe = new FurnaceEntity(in.readInt(), in.readInt(), in.readInt());
                fe.input = readStack(in);
                fe.fuel = readStack(in);
                fe.output = readStack(in);
                fe.burnTime = in.readInt();
                fe.burnTotal = in.readInt();
                fe.cookTime = in.readInt();
                world.furnaces.put(World.posKey(fe.x, fe.y, fe.z), fe);
            }
            return spawn;
        } catch (IOException e) {
            System.err.println("Failed to load level: " + e);
            return null;
        }
    }

    private static void writeStack(DataOutputStream out, ItemStack s) throws IOException {
        out.writeInt(s == null ? -1 : s.id);
        out.writeInt(s == null ? 0 : s.count);
    }

    private static ItemStack readStack(DataInputStream in) throws IOException {
        int id = in.readInt();
        int count = in.readInt();
        return id < 0 ? null : new ItemStack(id, count);
    }
}
