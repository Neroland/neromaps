package za.co.neroland.neromaps.spike;

/**
 * SPIKE. A fake 256 x 256 block of map columns in the shape the locked design describes (surface height, surface
 * block as a palette index, water depth). Pure Java, so the fill can be benchmarked without a game.
 */
public final class SpikeColumns {

    /** Columns per side of one tile. */
    public static final int SIZE = 256;

    /** Surface height per column, row-major (z * SIZE + x). */
    public final short[] height = new short[SIZE * SIZE];
    /** Surface block as an index into the tile palette. */
    public final byte[] block = new byte[SIZE * SIZE];
    /** Water depth above the surface block, 0 for dry land, saturating at 127. */
    public final byte[] water = new byte[SIZE * SIZE];

    /** A deterministic rolling landscape with a sea, so every shading branch is exercised. */
    public static SpikeColumns fake(int seed) {
        SpikeColumns c = new SpikeColumns();
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                double hills = Math.sin((x + seed) * 0.05) * 12 + Math.cos((z - seed) * 0.07) * 9
                        + Math.sin((x + z) * 0.13) * 3;
                int h = 64 + (int) hills;
                int i = z * SIZE + x;
                if (h < 60) {
                    c.height[i] = (short) (h - 2);
                    c.block[i] = 3; // sand under water
                    c.water[i] = (byte) Math.min(127, 62 - h + 2);
                } else {
                    c.height[i] = (short) h;
                    c.block[i] = (byte) (h > 72 ? 2 : (x ^ z) % 7 == 0 ? 4 : 1); // stone / flowers / grass
                    c.water[i] = 0;
                }
            }
        }
        return c;
    }
}
