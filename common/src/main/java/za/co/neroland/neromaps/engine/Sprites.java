package za.co.neroland.neromaps.engine;

/**
 * The minimap's sprite sheet (512 x 512, one texture, so the overlay stays one batch): the procedural 32 x 32 corner
 * (a white patch for solid geometry, a marker dot, the player arrow) drawn in code so the minimap never depends on an
 * art file, plus Fable's frame art for the current shape, size and border composited in at load (Stage 7). Pixels
 * are ARGB.
 */
public final class Sprites {

    public static final int SIZE = 512;
    /** Fable's frame art is composited into the sheet at these texel origins (see {@code MinimapHud}). */
    public static final int SQUARE_X = 32;
    public static final int SQUARE_Y = 0;
    public static final int NORTH_X = 64;
    public static final int NORTH_Y = 0;
    public static final int BEZEL_X = 0;
    public static final int BEZEL_Y = 32;
    public static final int TICKS_X = 176;
    public static final int TICKS_Y = 32;
    /** Solid white texel centre (frame geometry is tinted by vertex colour). */
    public static final float WHITE_U0 = 1f / SIZE;
    public static final float WHITE_V0 = 1f / SIZE;
    public static final float WHITE_U1 = 2f / SIZE;
    public static final float WHITE_V1 = 2f / SIZE;
    public static final float DOT_U0 = 8f / SIZE;
    public static final float DOT_U1 = 16f / SIZE;
    public static final float DOT_V0 = 0f;
    public static final float DOT_V1 = 8f / SIZE;
    public static final float ARROW_U0 = 16f / SIZE;
    public static final float ARROW_U1 = 25f / SIZE;
    public static final float ARROW_V0 = 0f;
    public static final float ARROW_V1 = 9f / SIZE;

    private Sprites() {
    }

    /** The sheet, row-major ARGB. */
    public static int[] pixels() {
        int[] p = new int[SIZE * SIZE];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                p[y * SIZE + x] = 0xFFFFFFFF;
            }
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                float dx = x + 0.5f - 4f;
                float dy = y + 0.5f - 4f;
                float d = dx * dx + dy * dy;
                p[y * SIZE + 8 + x] = d > 16f ? 0 : d > 9f ? 0xFF1A1D22 : 0xFFFFFFFF;
            }
        }
        // 9 x 9 arrowhead pointing up: periwinkle-bright body, white core, dark outline
        for (int y = 0; y < 9; y++) {
            int half = y * 4 / 8; // widens toward the base
            for (int x = 0; x < 9; x++) {
                int dx = Math.abs(x - 4);
                boolean inside = dx <= half && y < 8 && !(y >= 6 && dx <= 1 && y > 6);
                boolean edge = inside && (dx == half || y == 7);
                int c = 0;
                if (inside) {
                    c = edge ? 0xFF1A1D22 : (dx == 0 && y >= 2 && y <= 5) ? 0xFFFFFFFF : 0xFFB2BEFF;
                }
                p[y * SIZE + 16 + x] = c;
            }
        }
        return p;
    }
}
