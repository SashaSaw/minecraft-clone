package craft.tools;

import craft.gen.TerrainGen;

/** Headless terrain calibration check: height histogram + land/ocean/beach ratios. */
public class GenStats {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345;
        TerrainGen gen = new TerrainGen(seed);
        int[] hist = new int[128];
        int n = 0, land = 0, ocean = 0, beach = 0;
        int min = 999, max = -1;
        for (int z = -1000; z < 1000; z += 4) {
            for (int x = -1000; x < 1000; x += 4) {
                int h = gen.height(x, z);
                hist[h]++;
                n++;
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (h >= 64) land++;
                else if (h >= 59) beach++;
                else ocean++;
            }
        }
        System.out.printf("samples=%d min=%d max=%d  land=%.1f%% beach=%.1f%% ocean=%.1f%%%n",
                n, min, max, 100.0 * land / n, 100.0 * beach / n, 100.0 * ocean / n);
        for (int h = min; h <= max; h++) {
            if (hist[h] > 0) {
                System.out.printf("%3d %6d %s%n", h, hist[h], "#".repeat(Math.min(80, hist[h] / 250)));
            }
        }
        // ASCII map 96x48 to eyeball continent shapes (~ 1 char = 16 blocks)
        for (int z = 0; z < 48; z++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 96; x++) {
                int h = gen.height((x - 48) * 16, (z - 24) * 16);
                sb.append(h >= 64 ? '#' : (h >= 59 ? '+' : (h >= 55 ? '~' : '.')));
            }
            System.out.println(sb);
        }
    }
}
