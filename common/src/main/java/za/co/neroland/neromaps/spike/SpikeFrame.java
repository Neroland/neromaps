package za.co.neroland.neromaps.spike;

/**
 * SPIKE. The whole per-frame CPU path of the minimap: layout, rotation, surface geometry and the marker batch.
 * This is exactly what the allocation test runs 10 000 times; the loader HUD layer then only streams these arrays
 * into vertices. No lambdas, boxing, strings or collections.
 */
public final class SpikeFrame {

    /** Texels per side of the toroidal minimap texture. */
    public static final int TEXTURE = SpikeColumns.SIZE;

    public final SpikeMinimapGeometry surface = new SpikeMinimapGeometry();
    public final SpikeMarkerBatch markers = new SpikeMarkerBatch();

    /** Settings (Stage 2 reads these from the client config and presets). */
    public boolean round = true;
    public boolean rotate = true;
    public int sizePx = 128;
    public int marginPx = 4;
    public float pxPerBlock = 1f;
    public float markerHalf = 3f;

    /** Layout written by {@link #update}. */
    public int left;
    public int top;
    public int right;
    public int bottom;

    public void update(double px, double pz, float yawDegrees, int screenWidth) {
        float radius = sizePx * 0.5f;
        right = screenWidth - marginPx;
        left = right - sizePx;
        top = marginPx;
        bottom = top + sizePx;
        float cx = left + radius;
        float cy = top + radius;
        float sin = 0f;
        float cos = 1f;
        if (rotate) {
            // player's facing up the screen: rotate the world by -(yaw + 180)
            double a = Math.toRadians(yawDegrees + 180.0);
            sin = (float) Math.sin(a);
            cos = (float) Math.cos(a);
        }
        float cu = (float) (wrap(px) / TEXTURE);
        float cv = (float) (wrap(pz) / TEXTURE);
        surface.build(round, cx, cy, radius, cu, cv, 1f / (pxPerBlock * TEXTURE), sin, cos);
        markers.build(px, pz, sin, cos, round, cx, cy, radius, pxPerBlock, markerHalf);
    }

    private static double wrap(double v) {
        double m = v % TEXTURE;
        return m < 0 ? m + TEXTURE : m;
    }
}
