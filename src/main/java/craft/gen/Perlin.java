package craft.gen;

import java.util.Random;

/** Classic improved Perlin gradient noise, 2D, with seeded permutation table. */
public class Perlin {
    private final int[] p = new int[512];

    public Perlin(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 256; i++) p[i] = p[i + 256] = perm[i];
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }

    /** Single octave, roughly [-1, 1]. */
    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf), v = fade(yf);
        int aa = p[p[xi] + yi];
        int ab = p[p[xi] + yi + 1];
        int ba = p[p[xi + 1] + yi];
        int bb = p[p[xi + 1] + yi + 1];
        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v) * 1.41;   // stretch toward [-1,1]
    }

    /** Fractal Brownian motion: octaves with lacunarity 2.0, persistence 0.5. Normalized ~[-1,1]. */
    public double fbm(double x, double y, int octaves, double baseFreq) {
        double sum = 0, amp = 1, freq = baseFreq, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x * freq + o * 1259.7, y * freq + o * 3137.3);
            norm += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return sum / norm;
    }
}
