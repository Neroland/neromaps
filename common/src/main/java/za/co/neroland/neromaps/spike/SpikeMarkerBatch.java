package za.co.neroland.neromaps.spike;

/**
 * SPIKE. Projects up to {@link #CAPACITY} world markers into minimap screen space in one pass: offset from the
 * player, rotate with the map, cull against the square or circle, and write one small quad per visible marker
 * into flat arrays the emitter streams to the GPU as one batch. Allocation-free after construction.
 */
public final class SpikeMarkerBatch {

    /** Markers this batch can hold (the spike benchmarks 200). */
    public static final int CAPACITY = 256;

    private final double[] worldX = new double[CAPACITY];
    private final double[] worldZ = new double[CAPACITY];
    private final int[] colour = new int[CAPACITY];
    private int size;

    /** x, y per vertex, four vertices per visible marker. */
    public final float[] xy = new float[CAPACITY * 4 * 2];
    /** ARGB per visible marker. */
    public final int[] quadColour = new int[CAPACITY];
    /** Visible markers written by the last {@link #build}. */
    public int quads;

    /** Adds a marker; ignored once full. */
    public void add(double x, double z, int argb) {
        if (size < CAPACITY) {
            worldX[size] = x;
            worldZ[size] = z;
            colour[size] = argb;
            size++;
        }
    }

    /** Removes every marker. */
    public void clear() {
        size = 0;
        quads = 0;
    }

    /** Markers held. */
    public int size() {
        return size;
    }

    /**
     * @param px          player x
     * @param pz          player z
     * @param sin         sine of the map rotation
     * @param cos         cosine of the map rotation
     * @param round       circle (true) or square cull
     * @param cx          screen centre x
     * @param cy          screen centre y
     * @param radius      half the minimap size, screen pixels
     * @param pxPerBlock  zoom
     * @param half        half the marker size, screen pixels
     */
    public void build(double px, double pz, float sin, float cos, boolean round, float cx, float cy, float radius,
            float pxPerBlock, float half) {
        float limit = radius - half;
        float limitSq = limit * limit;
        int q = 0;
        for (int i = 0; i < size; i++) {
            float dx = (float) (worldX[i] - px) * pxPerBlock;
            float dz = (float) (worldZ[i] - pz) * pxPerBlock;
            // inverse of the texture rotation, so markers stay glued to the terrain under them
            float sx = dx * cos + dz * sin;
            float sy = -dx * sin + dz * cos;
            if (round ? sx * sx + sy * sy > limitSq : Math.abs(sx) > limit || Math.abs(sy) > limit) {
                continue;
            }
            float x0 = cx + sx - half;
            float y0 = cy + sy - half;
            float x1 = x0 + half * 2;
            float y1 = y0 + half * 2;
            int o = q * 8;
            xy[o] = x0;
            xy[o + 1] = y0;
            xy[o + 2] = x0;
            xy[o + 3] = y1;
            xy[o + 4] = x1;
            xy[o + 5] = y1;
            xy[o + 6] = x1;
            xy[o + 7] = y0;
            quadColour[q] = colour[i];
            q++;
        }
        quads = q;
    }
}
