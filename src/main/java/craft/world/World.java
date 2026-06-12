package craft.world;

import craft.gen.Decorator;
import craft.gen.TerrainGen;
import craft.light.LightEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

public class World {
    public static final int SEA_LEVEL = TerrainGen.SEA_LEVEL;

    public final long seed;
    public final TerrainGen terrainGen;
    private final Decorator decorator;
    public final LightEngine light;
    private final ExecutorService genPool;

    private final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final HashSet<Long> pendingTerrain = new HashSet<>();
    private final ConcurrentLinkedQueue<Chunk> completedTerrain = new ConcurrentLinkedQueue<>();

    /** Sections needing (re)mesh; drained by the renderer. Key = sectionKey. */
    public final HashSet<Long> dirtySections = new HashSet<>();
    /** Chunks removed this tick; renderer must free their meshes. */
    public final List<Chunk> unloadedChunks = new ArrayList<>();

    private int[][] sortedOffsets;   // {dx, dz} sorted by distance
    private int sortedRadius = -1;
    private int unloadCounter;

    public World(long seed, ExecutorService genPool) {
        this.seed = seed;
        this.genPool = genPool;
        this.terrainGen = new TerrainGen(seed);
        this.decorator = new Decorator(seed);
        this.light = new LightEngine(this);
    }

    public static long chunkKey(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    public static long sectionKey(int cx, int sy, int cz) {
        return ((long) (cx & 0x3FFFFFF) << 38) | ((long) (cz & 0x3FFFFFF) << 12) | (sy & 0xFFF);
    }

    public static int sectionKeyX(long key) {
        return (int) (key >> 38);
    }

    public static int sectionKeyZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    public static int sectionKeyY(long key) {
        return (int) (key & 0xFFF);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(chunkKey(cx, cz));
    }

    public byte getBlock(int x, int y, int z) {
        if (y < 0) return Block.BEDROCK;
        if (y >= Chunk.H) return Block.AIR;
        Chunk c = getChunk(x >> 4, z >> 4);
        if (c == null) return Block.AIR;
        return c.getBlock(x & 15, y, z & 15);
    }

    public Block blockAt(int x, int y, int z) {
        return Block.get(getBlock(x, y, z));
    }

    public int getSky(int x, int y, int z) {
        if (y >= Chunk.H) return 15;
        if (y < 0) return 0;
        Chunk c = getChunk(x >> 4, z >> 4);
        if (c == null) return 15;
        return c.getSky(x & 15, y, z & 15);
    }

    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y >= Chunk.H) return 0;
        Chunk c = getChunk(x >> 4, z >> 4);
        if (c == null) return 0;
        return c.getBlockLight(x & 15, y, z & 15);
    }

    public int opacity(int x, int y, int z) {
        return Block.get(getBlock(x, y, z)).opacity;
    }

    /** Player/game edit: updates light and marks affected sections for remesh. */
    public void setBlock(int x, int y, int z, byte id) {
        if (y < 0 || y >= Chunk.H) return;
        Chunk c = getChunk(x >> 4, z >> 4);
        if (c == null) return;
        byte old = c.getBlock(x & 15, y, z & 15);
        if (old == id) return;
        c.setBlock(x & 15, y, z & 15, id);
        c.modified = true;
        light.onBlockChanged(x, y, z, old, id);
        markDirtyAround(x, y, z);
    }

    /** Marks the section containing (x,y,z) dirty, plus neighbors when on a border. */
    public void markDirtyAround(int x, int y, int z) {
        markSectionDirty(x, y, z);
        if ((x & 15) == 0) markSectionDirty(x - 1, y, z);
        if ((x & 15) == 15) markSectionDirty(x + 1, y, z);
        if ((z & 15) == 0) markSectionDirty(x, y, z - 1);
        if ((z & 15) == 15) markSectionDirty(x, y, z + 1);
        if ((y & 15) == 0 && y > 0) markSectionDirty(x, y - 1, z);
        if ((y & 15) == 15 && y < Chunk.H - 1) markSectionDirty(x, y + 1, z);
    }

    public void markSectionDirty(int x, int y, int z) {
        if (y < 0 || y >= Chunk.H) return;
        dirtySections.add(sectionKey(x >> 4, y >> 4, z >> 4));
    }

    public boolean isMeshable(int cx, int cz) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = getChunk(cx + dx, cz + dz);
                if (c == null || c.state < Chunk.STATE_DECORATED) return false;
            }
        }
        return true;
    }

    public int surfaceY(int x, int z) {
        Chunk c = getChunk(x >> 4, z >> 4);
        return c == null ? -1 : c.surfaceY(x & 15, z & 15);
    }

    public int loadedChunkCount() {
        return chunks.size();
    }

    /**
     * Per-tick chunk pipeline: submit terrain jobs (workers), integrate finished chunks,
     * decorate + light a few per tick (main thread), unload distant chunks.
     */
    public void update(int pcx, int pcz, int renderDist) {
        int genRadius = renderDist + 1;
        ensureOffsets(genRadius);

        // Integrate finished terrain
        Chunk done;
        while ((done = completedTerrain.poll()) != null) {
            chunks.put(chunkKey(done.cx, done.cz), done);
            pendingTerrain.remove(chunkKey(done.cx, done.cz));
        }

        // Submit terrain jobs nearest-first
        int inFlight = pendingTerrain.size();
        for (int[] off : sortedOffsets) {
            if (inFlight >= 24) break;
            int cx = pcx + off[0], cz = pcz + off[1];
            long key = chunkKey(cx, cz);
            if (chunks.containsKey(key) || pendingTerrain.contains(key)) continue;
            pendingTerrain.add(key);
            inFlight++;
            genPool.submit(() -> {
                Chunk c = new Chunk(cx, cz);
                terrainGen.generate(c);
                completedTerrain.add(c);
            });
        }

        // Decorate + initial light, a few per tick, nearest-first
        int budget = 6;
        for (int[] off : sortedOffsets) {
            if (budget == 0) break;
            Chunk c = getChunk(pcx + off[0], pcz + off[1]);
            if (c != null && c.state == Chunk.STATE_TERRAIN) {
                decorator.decorate(this, c);
                light.initChunk(c);
                c.state = Chunk.STATE_DECORATED;
                for (int sy = 0; sy < Chunk.SECTIONS; sy++) {
                    dirtySections.add(sectionKey(c.cx, sy, c.cz));
                }
                budget--;
            }
        }

        // Unload far chunks occasionally
        if (++unloadCounter >= 40) {
            unloadCounter = 0;
            int limit = renderDist + 3;
            for (Chunk c : chunks.values()) {
                if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > limit) {
                    chunks.remove(chunkKey(c.cx, c.cz));
                    unloadedChunks.add(c);
                }
            }
        }
    }

    private void ensureOffsets(int radius) {
        if (sortedRadius == radius) return;
        sortedRadius = radius;
        List<int[]> offs = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                offs.add(new int[]{dx, dz});
            }
        }
        offs.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        sortedOffsets = offs.toArray(new int[0][]);
    }
}
