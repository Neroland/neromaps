package za.co.neroland.neromaps.engine;

/**
 * Screen positions and texture coordinates of the minimap surface: one quad when square, a fan of
 * {@link #SEGMENTS} wedges when round (masking by geometry: no stencil, no shader, no pre-masked texture).
 * Rotation turns the texture coordinates, not the screen shape; the texture is a toroidal window sampled with
 * REPEAT, so coordinates outside 0..1 wrap. Allocation-free.
 */
public final class MinimapGeometry {

    public static final int SEGMENTS = 64;

    static final float[] COS = new float[SEGMENTS + 1];
    static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = Math.PI * 2 * i / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    public final float[] xy = new float[SEGMENTS * 8];
    public final float[] uv = new float[SEGMENTS * 8];
    public int quads;

    /**
     * @param uvPerPixel texture units per screen pixel (zoom)
     * @param sin        sine of the map rotation (0 when north-locked)
     * @param cos        cosine of the map rotation (1 when north-locked)
     */
    public void build(boolean round, float cx, float cy, float radius, float cu, float cv, float uvPerPixel,
            float sin, float cos) {
        int v = 0;
        if (!round) {
            v = vertex(v, cx, cy, -radius, -radius, cu, cv, uvPerPixel, sin, cos);
            v = vertex(v, cx, cy, -radius, radius, cu, cv, uvPerPixel, sin, cos);
            v = vertex(v, cx, cy, radius, radius, cu, cv, uvPerPixel, sin, cos);
            vertex(v, cx, cy, radius, -radius, cu, cv, uvPerPixel, sin, cos);
            quads = 1;
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float ax = COS[i] * radius;
            float ay = SIN[i] * radius;
            float bx = COS[i + 1] * radius;
            float by = SIN[i + 1] * radius;
            v = vertex(v, cx, cy, 0, 0, cu, cv, uvPerPixel, sin, cos);
            v = vertex(v, cx, cy, bx, by, cu, cv, uvPerPixel, sin, cos);
            v = vertex(v, cx, cy, ax, ay, cu, cv, uvPerPixel, sin, cos);
            v = vertex(v, cx, cy, ax, ay, cu, cv, uvPerPixel, sin, cos);
        }
        quads = SEGMENTS;
    }

    private int vertex(int v, float cx, float cy, float dx, float dy, float cu, float cv, float uvPerPixel,
            float sin, float cos) {
        int o = v * 2;
        xy[o] = cx + dx;
        xy[o + 1] = cy + dy;
        uv[o] = cu + (dx * cos - dy * sin) * uvPerPixel;
        uv[o + 1] = cv + (dx * sin + dy * cos) * uvPerPixel;
        return v + 1;
    }
}
