package za.co.neroland.neromaps.engine;

/**
 * A preallocated batch of textured, coloured quads (x, y and u, v per vertex, four vertices per quad, the winding
 * vanilla's blit uses). The minimap's second pass (frame, markers, player arrow) is one of these. Allocation-free.
 */
public final class QuadBatch {

    public final int capacity;
    public final float[] xy;
    public final float[] uv;
    public final int[] colour;
    public int quads;

    public QuadBatch(int capacity) {
        this.capacity = capacity;
        this.xy = new float[capacity * 8];
        this.uv = new float[capacity * 8];
        this.colour = new int[capacity];
    }

    public void clear() {
        quads = 0;
    }

    /** An axis-aligned quad. Returns false when the batch is full. */
    public boolean rect(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        return quad(x0, y0, x0, y1, x1, y1, x1, y0, u0, v0, u0, v1, u1, v1, u1, v0, argb);
    }

    /** A quad centred on {@code (cx, cy)}, {@code w x h}, rotated by {@code (sin, cos)} (screen space). */
    public boolean rotated(float cx, float cy, float w, float h, float sin, float cos, float u0, float v0, float u1,
            float v1, int argb) {
        float hw = w * 0.5f;
        float hh = h * 0.5f;
        float ax = -hw * cos + hh * sin;
        float ay = -hw * sin - hh * cos;
        float bx = -hw * cos - hh * sin;
        float by = -hw * sin + hh * cos;
        return quad(cx + ax, cy + ay, cx + bx, cy + by, cx - ax, cy - ay, cx - bx, cy - by, u0, v0, u0, v1, u1, v1,
                u1, v0, argb);
    }

    /** A general quad, vertices in drawing order. */
    public boolean quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float u0,
            float v0, float u1, float v1, float u2, float v2, float u3, float v3, int argb) {
        if (quads >= capacity) {
            return false;
        }
        int o = quads * 8;
        xy[o] = x0;
        xy[o + 1] = y0;
        xy[o + 2] = x1;
        xy[o + 3] = y1;
        xy[o + 4] = x2;
        xy[o + 5] = y2;
        xy[o + 6] = x3;
        xy[o + 7] = y3;
        uv[o] = u0;
        uv[o + 1] = v0;
        uv[o + 2] = u1;
        uv[o + 3] = v1;
        uv[o + 4] = u2;
        uv[o + 5] = v2;
        uv[o + 6] = u3;
        uv[o + 7] = v3;
        colour[quads] = argb;
        quads++;
        return true;
    }
}
