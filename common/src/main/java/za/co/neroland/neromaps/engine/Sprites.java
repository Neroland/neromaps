package za.co.neroland.neromaps.engine;

/**
 * The minimap's small procedural sprite sheet (32 x 32): a white patch for solid frame geometry, a marker dot and
 * the player arrow. Drawn in code, so the minimap never depends on an art file being present; Fable's frames and
 * icon atlas replace these when they land. Pixels are ARGB.
 */
public final class Sprites {

    public static final int SIZE = 32;
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
