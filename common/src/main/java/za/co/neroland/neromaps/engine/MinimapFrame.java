package za.co.neroland.neromaps.engine;

import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.settings.MinimapSettings.Border;

/**
 * The minimap's whole per-frame CPU path: the surface geometry (one quad or a 64-wedge fan) and the overlay batch
 * (frame, north mark, markers, player arrow). Preallocated; {@link #update} allocates nothing. Markers are
 * world positions added by layer code (none in Stage 2 beyond the player); they are projected, rotated with the
 * map and culled to the shape.
 */
public final class MinimapFrame {

    public static final int MARKER_CAPACITY = 256;
    public static final int OVERLAY_CAPACITY = 1024;

    public final MinimapGeometry surface = new MinimapGeometry();
    public final QuadBatch overlay = new QuadBatch(OVERLAY_CAPACITY);

    private final double[] markerX = new double[MARKER_CAPACITY];
    private final double[] markerZ = new double[MARKER_CAPACITY];
    private final int[] markerColour = new int[MARKER_CAPACITY];
    private int markers;

    public float markerHalf = 3f;

    public void clearMarkers() {
        markers = 0;
    }

    public void addMarker(double x, double z, int argb) {
        if (markers < MARKER_CAPACITY) {
            markerX[markers] = x;
            markerZ[markers] = z;
            markerColour[markers] = argb;
            markers++;
        }
    }

    public int markerCount() {
        return markers;
    }

    /** Frame thickness for a border style. */
    public static int frameWidth(Border border, boolean highContrast) {
        if (highContrast) {
            return 2;
        }
        return border == Border.NERO ? 3 : border == Border.MINIMAL ? 2 : 0;
    }

    /**
     * @param texture   clipmap size N (texels)
     * @param zoom      GUI pixels per block
     * @param yawDeg    player yaw (Minecraft: 0 = south)
     * @param px        player x, {@code pz} player z (blocks)
     */
    public void update(MinimapLayout l, boolean round, boolean rotate, float yawDeg, double px, double pz,
            float zoom, int texture, Border border, boolean highContrast) {
        float radius = l.size * 0.5f;
        float cx = l.left + radius;
        float cy = l.top + radius;
        float sin = 0f;
        float cos = 1f;
        if (rotate) {
            double a = Math.toRadians(yawDeg + 180.0);
            sin = (float) Math.sin(a);
            cos = (float) Math.cos(a);
        }
        float cu = (float) (wrap(px, texture) / texture);
        float cv = (float) (wrap(pz, texture) / texture);
        surface.build(round, cx, cy, radius, cu, cv, 1f / (zoom * texture), sin, cos);

        QuadBatch b = overlay;
        b.clear();
        frame(b, round, cx, cy, radius, l.frame, border, highContrast, sin, cos);
        // markers
        float limit = radius - markerHalf;
        float limitSq = limit * limit;
        for (int i = 0; i < markers; i++) {
            float dx = (float) (markerX[i] - px) * zoom;
            float dz = (float) (markerZ[i] - pz) * zoom;
            float sx = dx * cos + dz * sin;
            float sy = -dx * sin + dz * cos;
            if (round ? sx * sx + sy * sy > limitSq : Math.abs(sx) > limit || Math.abs(sy) > limit) {
                continue;
            }
            b.rect(cx + sx - markerHalf, cy + sy - markerHalf, cx + sx + markerHalf, cy + sy + markerHalf,
                    Sprites.DOT_U0, Sprites.DOT_V0, Sprites.DOT_U1, Sprites.DOT_V1, markerColour[i]);
        }
        // the player: on a rotating map the arrow points up, on a north-locked map it turns
        float arrowSin = 0f;
        float arrowCos = 1f;
        if (!rotate) {
            double a = Math.toRadians(yawDeg + 180.0);
            arrowSin = (float) Math.sin(a);
            arrowCos = (float) Math.cos(a);
        }
        b.rotated(cx, cy, 9f, 9f, arrowSin, arrowCos, Sprites.ARROW_U0, Sprites.ARROW_V0, Sprites.ARROW_U1,
                Sprites.ARROW_V1, 0xFFFFFFFF);
    }

    private static void frame(QuadBatch b, boolean round, float cx, float cy, float r, int fw, Border border,
            boolean hc, float sin, float cos) {
        // north on screen: the world's -Z after the map rotation
        float nx = -sin;
        float ny = -cos;
        if (fw == 0) {
            north(b, round, cx, cy, r, nx, ny, hc);
            return;
        }
        if (round) {
            if (hc) {
                ring(b, cx, cy, r, r + 2, 0xFFFFFFFF);
            } else if (border == Border.NERO) {
                ring(b, cx, cy, r, r + 1, MapPalette.GLASS);
                ring(b, cx, cy, r + 1, r + 2, MapPalette.STEEL);
                ring(b, cx, cy, r + 2, r + 3, MapPalette.G_DARK);
                // minor ticks every 45 degrees, rotating with the map
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * k / 4;
                    float tx = (float) (nx * Math.cos(a) - ny * Math.sin(a));
                    float ty = (float) (nx * Math.sin(a) + ny * Math.cos(a));
                    float d = r + 1.5f;
                    float half = k == 0 ? 1.5f : 1f;
                    b.rect(cx + tx * d - half, cy + ty * d - half, cx + tx * d + half, cy + ty * d + half,
                            Sprites.WHITE_U0, Sprites.WHITE_V0, Sprites.WHITE_U1, Sprites.WHITE_V1,
                            k == 0 ? MapPalette.MAP_ACCENT_BRIGHT : MapPalette.MAP_ACCENT);
                }
            } else {
                ring(b, cx, cy, r, r + 1, MapPalette.TRIM);
                ring(b, cx, cy, r + 1, r + 2, MapPalette.G_DARK);
            }
        } else {
            float x0 = cx - r;
            float y0 = cy - r;
            float x1 = cx + r;
            float y1 = cy + r;
            if (hc) {
                box(b, x0, y0, x1, y1, 2, 0xFFFFFFFF);
            } else if (border == Border.NERO) {
                box(b, x0, y0, x1, y1, 1, MapPalette.GLASS);
                box(b, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 1, MapPalette.STEEL);
                box(b, x0 - 2, y0 - 2, x1 + 2, y1 + 2, 1, MapPalette.G_DARK);
                // periwinkle L-brackets, 5 px arms
                bracket(b, x0 - 1, y0 - 1, 1, 1);
                bracket(b, x1 + 1, y0 - 1, -1, 1);
                bracket(b, x0 - 1, y1 + 1, 1, -1);
                bracket(b, x1 + 1, y1 + 1, -1, -1);
            } else {
                box(b, x0, y0, x1, y1, 1, MapPalette.TRIM);
                box(b, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 1, MapPalette.G_DARK);
            }
        }
        north(b, round, cx, cy, r + fw, nx, ny, hc);
    }

    /** The north mark: a small accent square on the rim in the north direction. */
    private static void north(QuadBatch b, boolean round, float cx, float cy, float r, float nx, float ny,
            boolean hc) {
        float d = r;
        if (!round) {
            float m = Math.max(Math.abs(nx), Math.abs(ny));
            d = r / Math.max(1e-3f, m);
        }
        float x = cx + nx * d;
        float y = cy + ny * d;
        b.rect(x - 2f, y - 2f, x + 2f, y + 2f, Sprites.WHITE_U0, Sprites.WHITE_V0, Sprites.WHITE_U1,
                Sprites.WHITE_V1, hc ? 0xFFFFFFFF : MapPalette.MAP_ACCENT_BRIGHT);
    }

    private static void ring(QuadBatch b, float cx, float cy, float r0, float r1, int argb) {
        float[] c = MinimapGeometry.COS;
        float[] s = MinimapGeometry.SIN;
        for (int i = 0; i < MinimapGeometry.SEGMENTS; i++) {
            b.quad(cx + c[i] * r0, cy + s[i] * r0, cx + c[i] * r1, cy + s[i] * r1, cx + c[i + 1] * r1,
                    cy + s[i + 1] * r1, cx + c[i + 1] * r0, cy + s[i + 1] * r0, Sprites.WHITE_U0, Sprites.WHITE_V0,
                    Sprites.WHITE_U0, Sprites.WHITE_V1, Sprites.WHITE_U1, Sprites.WHITE_V1, Sprites.WHITE_U1,
                    Sprites.WHITE_V0, argb);
        }
    }

    /** A {@code w}-wide outline just outside {@code (x0,y0)-(x1,y1)}. */
    private static void box(QuadBatch b, float x0, float y0, float x1, float y1, float w, int argb) {
        solid(b, x0 - w, y0 - w, x1 + w, y0, argb);
        solid(b, x0 - w, y1, x1 + w, y1 + w, argb);
        solid(b, x0 - w, y0, x0, y1, argb);
        solid(b, x1, y0, x1 + w, y1, argb);
    }

    /** An L-bracket with its corner at (x, y), 5 px arms pointing along +sx and +sy. */
    private static void bracket(QuadBatch b, float x, float y, int sx, int sy) {
        float hx0 = Math.min(x, x + sx * 5);
        float hx1 = Math.max(x, x + sx * 5);
        float vy0 = Math.min(y, y + sy * 5);
        float vy1 = Math.max(y, y + sy * 5);
        float hy0 = sy > 0 ? y - 1 : y;
        float vx0 = sx > 0 ? x - 1 : x;
        solid(b, hx0, hy0, hx1, hy0 + 1, MapPalette.MAP_ACCENT);
        solid(b, vx0, vy0, vx0 + 1, vy1, MapPalette.MAP_ACCENT);
    }

    private static void solid(QuadBatch b, float x0, float y0, float x1, float y1, int argb) {
        b.rect(x0, y0, x1, y1, Sprites.WHITE_U0, Sprites.WHITE_V0, Sprites.WHITE_U1, Sprites.WHITE_V1, argb);
    }

    static double wrap(double v, int n) {
        double m = v % n;
        return m < 0 ? m + n : m;
    }
}
