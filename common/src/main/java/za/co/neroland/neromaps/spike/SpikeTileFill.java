package za.co.neroland.neromaps.spike;

/**
 * SPIKE. Column-scan colour fill: turns {@link SpikeColumns} into ARGB texels with vanilla-map-style height shading
 * (compare with the column to the north) and depth-tinted water. Allocation-free; tables are built once.
 */
public final class SpikeTileFill {

    private static final int SIZE = SpikeColumns.SIZE;
    /** Shade multipliers out of 256: lower than the north neighbour, level, higher. */
    private static final int SHADE_LOW = 180;
    private static final int SHADE_LEVEL = 220;
    private static final int SHADE_HIGH = 255;
    /** Pre-blended water colours by depth bucket (0..15). */
    private static final int[] WATER = new int[16];

    static {
        int shallow = 0xFF3F76E4;
        int deep = 0xFF0E2A6B;
        for (int d = 0; d < WATER.length; d++) {
            WATER[d] = lerp(shallow, deep, d / 15f);
        }
    }

    private SpikeTileFill() {
    }

    /** Fills the whole tile. {@code out} is {@code SIZE * SIZE} ARGB texels. */
    public static void fill(SpikeColumns c, int[] palette, int[] out) {
        fillRect(c, palette, out, 0, 0, SIZE, SIZE);
    }

    /** Fills one rectangle of the tile (a dirty chunk is 16 x 16). */
    public static void fillRect(SpikeColumns c, int[] palette, int[] out, int x0, int z0, int w, int h) {
        short[] height = c.height;
        byte[] block = c.block;
        byte[] water = c.water;
        int paletteMask = palette.length - 1; // palette length is a power of two
        for (int z = z0; z < z0 + h; z++) {
            int row = z * SIZE;
            int north = (z == 0 ? z : z - 1) * SIZE;
            for (int x = x0; x < x0 + w; x++) {
                int i = row + x;
                int depth = water[i];
                int argb;
                if (depth > 0) {
                    argb = WATER[Math.min(15, depth >> 1)];
                    // vanilla-style dither on odd depths so the sea floor reads
                    if (((x + z) & 1) == 0 && (depth & 1) == 1) {
                        argb = shade(argb, SHADE_LEVEL);
                    }
                } else {
                    int hs = height[i];
                    int hn = height[north + x];
                    int s = hs > hn ? SHADE_HIGH : hs == hn ? SHADE_LEVEL : SHADE_LOW;
                    argb = shade(palette[block[i] & paletteMask], s);
                }
                out[i] = argb;
            }
        }
    }

    private static int shade(int argb, int s) {
        int r = ((argb >>> 16) & 0xFF) * s >>> 8;
        int g = ((argb >>> 8) & 0xFF) * s >>> 8;
        int b = (argb & 0xFF) * s >>> 8;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >>> 16) & 0xFF) + ((((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t));
        int g = (int) (((a >>> 8) & 0xFF) + ((((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** A small fake palette (power-of-two length) matching {@link SpikeColumns#fake}. */
    public static int[] fakePalette() {
        int[] p = new int[8];
        p[0] = 0xFF000000;
        p[1] = 0xFF7FB238; // grass
        p[2] = 0xFF707070; // stone
        p[3] = 0xFFF7E9A3; // sand
        p[4] = 0xFFFF7F7F; // flowers
        p[5] = 0xFF8F7748;
        p[6] = 0xFFFFFFFF;
        p[7] = 0xFF4040FF;
        return p;
    }
}
