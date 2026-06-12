package craft.world;

/** Cube face constants and geometry tables. Coordinate system: +X east, +Y up, +Z south. */
public final class Face {
    public static final int TOP = 0, BOTTOM = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    public static final int[][] NORMAL = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    /**
     * 4 corner positions per face, CCW seen from outside the cube
     * (so (v1-v0)x(v2-v1) = outward normal).
     */
    public static final int[][][] CORNERS = {
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},  // TOP
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},  // BOTTOM
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},  // NORTH (-Z)
            {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},  // SOUTH (+Z)
            {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},  // WEST (-X)
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},  // EAST (+X)
    };

    /** Vanilla face brightness multipliers. */
    public static final float[] SHADE = {1.0f, 0.5f, 0.8f, 0.8f, 0.6f, 0.6f};

    private Face() {
    }
}
