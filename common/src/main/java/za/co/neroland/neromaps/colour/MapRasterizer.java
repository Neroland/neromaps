package za.co.neroland.neromaps.colour;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.tile.Tile;

/**
 * Turns tile columns into ARGB pixels. One chunk (16 x 16 columns) per call, so the
 * render thread can drain the clipmap and world-map textures a chunk at a time. Allocates nothing after the
 * tile's colour cache is warm.
 */
public final class MapRasterizer {

    private static final int[] SHADE = {200, 228, 256, 276, 297}; // 0.78, 0.89, 1.00, 1.08, 1.16 (x256)
    private static final String WATER_ID = "minecraft:water";

    private MapRasterizer() {
    }

    /** Per-tile colour cache: palette index to packed table colour and biome colours. */
    public static final class TileColours {
        int version = -1;
        int blocks;
        int biomes;
        int[] block = new int[16];
        int[] grass = new int[16];
        int[] foliage = new int[16];
        int[] water = new int[16];
        int[] style = new int[16];
        /** The water texture's colour (the biome water colour is multiplied by it, as the game does). */
        int waterTexture = 0xFFFFFF;
    }

    static TileColours colours(Tile t, ColourTable table) {
        TileColours c = t.colours instanceof TileColours tc ? tc : null;
        if (c == null) {
            c = new TileColours();
            t.colours = c;
        }
        if (c.version != table.version()) {
            c.version = table.version();
            c.blocks = 0;
            c.biomes = 0;
            c.waterTexture = ColourTable.rgb(table.block(WATER_ID));
        }
        int nb = t.blockPaletteSize();
        if (c.blocks < nb) {
            if (c.block.length < nb) {
                c.block = java.util.Arrays.copyOf(c.block, Math.max(nb, c.block.length * 2));
            }
            for (int i = c.blocks; i < nb; i++) {
                c.block[i] = table.block(t.blockId(i));
            }
            c.blocks = nb;
        }
        int nbi = t.biomePaletteSize();
        if (c.biomes < nbi) {
            if (c.grass.length < nbi) {
                int n = Math.max(nbi, c.grass.length * 2);
                c.grass = java.util.Arrays.copyOf(c.grass, n);
                c.foliage = java.util.Arrays.copyOf(c.foliage, n);
                c.water = java.util.Arrays.copyOf(c.water, n);
                c.style = java.util.Arrays.copyOf(c.style, n);
            }
            for (int i = c.biomes; i < nbi; i++) {
                int[] b = table.biome(t.biomeId(i));
                c.grass[i] = b[0];
                c.foliage[i] = b[1];
                c.water[i] = b[2];
                c.style[i] = table.biomeStyle(t.biomeId(i));
            }
            c.biomes = nbi;
        }
        return c;
    }

    /**
     * Rasterises tile-local chunk {@code (cx, cz)} of {@code t} (at tile coords {@code tx, tz}) into {@code dst}
     * starting at {@code dstOff} with row stride {@code dstStride}. A null tile draws fog.
     */
    public static void chunk(@Nullable Tile t, int tx, int tz, int cx, int cz, TileLookup neighbours,
            ColourTable table, RasterOptions o, int[] dst, int dstOff, int dstStride) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        int worldX0 = (tx << 8) + x0;
        int worldZ0 = (tz << 8) + z0;
        if (t == null) {
            for (int z = 0; z < 16; z++) {
                int row = dstOff + z * dstStride;
                for (int x = 0; x < 16; x++) {
                    dst[row + x] = fog(worldX0 + x, worldZ0 + z, o.style);
                }
            }
            return;
        }
        TileColours c = colours(t, table);
        Tile north = z0 == 0 ? neighbours.get(t.lod, tx, tz - 1) : t;
        Tile west = x0 == 0 ? neighbours.get(t.lod, tx - 1, tz) : t;
        int step = 1 << t.lod;
        // fog edge: which neighbouring chunks are unsurveyed. Fog is chunk-aligned, so one
        // probe per side tells the whole edge; the explored columns next to it are dithered toward the void.
        boolean fogW = absent(t, neighbours, tx, tz, x0 - 1, z0 + 8);
        boolean fogE = absent(t, neighbours, tx, tz, x0 + 16, z0 + 8);
        boolean fogN = absent(t, neighbours, tx, tz, x0 + 8, z0 - 1);
        boolean fogS = absent(t, neighbours, tx, tz, x0 + 8, z0 + 16);
        boolean edges = fogW || fogE || fogN || fogS;
        for (int z = z0; z < z0 + 16; z++) {
            int row = dstOff + (z - z0) * dstStride;
            for (int x = x0; x < x0 + 16; x++) {
                int i = Tile.index(x, z);
                int wx = worldX0 + (x - x0);
                int wz = worldZ0 + (z - z0);
                int argb;
                if (!t.present(i)) {
                    argb = fog(wx, wz, o.style);
                } else {
                    int h = t.height[i];
                    int hn = z > 0 ? height(t, Tile.index(x, z - 1), h) : north != null ? height(north, Tile.index(x, 255), h) : h;
                    int hw = x > 0 ? height(t, i - 1, h) : west != null ? height(west, Tile.index(255, z), h) : h;
                    argb = column(t, c, i, x, z, h, hn, hw, o);
                    if (o.chunkLines && (((wx * step) & 15) == 0 || ((wz * step) & 15) == 0)) {
                        argb = scale(argb, 216);
                    }
                    if (o.gridLines && (((wx * step) & 127) == 0 || ((wz * step) & 127) == 0)) {
                        argb = mix(argb, MapPalette.MAP_ACCENT, 90);
                    }
                    if (edges) {
                        int lx = x - x0;
                        int lz = z - z0;
                        int d = 99;
                        if (fogW) {
                            d = Math.min(d, lx + 1);
                        }
                        if (fogE) {
                            d = Math.min(d, 16 - lx);
                        }
                        if (fogN) {
                            d = Math.min(d, lz + 1);
                        }
                        if (fogS) {
                            d = Math.min(d, 16 - lz);
                        }
                        argb = fogEdge(argb, d, wx, wz, o.style);
                    }
                }
                dst[row + (x - x0)] = argb;
            }
        }
    }

    /** Whether tile-local column {@code (x, z)} (possibly in a neighbouring tile) is unsurveyed. */
    private static boolean absent(Tile t, TileLookup neighbours, int tx, int tz, int x, int z) {
        if (x >= 0 && x < Tile.SIZE && z >= 0 && z < Tile.SIZE) {
            return !t.present(Tile.index(x, z));
        }
        int ntx = tx + Math.floorDiv(x, Tile.SIZE);
        int ntz = tz + Math.floorDiv(z, Tile.SIZE);
        Tile n = neighbours.get(t.lod, ntx, ntz);
        return n == null || !n.present(Tile.index(Math.floorMod(x, Tile.SIZE), Math.floorMod(z, Tile.SIZE)));
    }

    /**
     * The explored side of a fog boundary, {@code d} columns from it (see
     * {@code map_fog_edge.png} read from the fog outward): 50 % ordered dither to the void at 1, 25 % at 2, a
     * {@code VOID_EDGE} rim blend at 3. High contrast draws a one-pixel white edge instead. Deterministic by world
     * position, so it never shimmers. Fog itself stays opaque: nothing hidden is ever drawn dimmed.
     */
    public static int fogEdge(int argb, int d, int wx, int wz, MapStyle style) {
        if (style == MapStyle.HIGH_CONTRAST) {
            return d == 1 ? 0xFFFFFFFF : argb;
        }
        if (d == 1) {
            return ((wx + wz) & 1) == 0 ? MapPalette.VOID : argb;
        }
        if (d == 2) {
            return (wx & 1) == 0 && (wz & 1) == 0 ? MapPalette.VOID : argb;
        }
        if (d == 3) {
            return 0xFF000000 | blendRgb(argb & 0xFFFFFF, MapPalette.VOID_EDGE & 0xFFFFFF, 128);
        }
        return argb;
    }

    private static int height(Tile t, int i, int fallback) {
        return t.present(i) ? t.height[i] : fallback;
    }

    private static int column(Tile t, TileColours c, int i, int x, int z, int h, int hn, int hw, RasterOptions o) {
        int f = t.flags[i] & 0xFF;
        int bi = t.biome[i] & 0xFF;
        MapStyle style = o.style;
        if ((f & Tile.F_LAVA) != 0) {
            return style == MapStyle.HIGH_CONTRAST ? MapPalette.HC_LAVA : MapPalette.LAVA;
        }
        boolean water = (f & Tile.F_WATER) != 0;
        int depth = t.depth[i] & 0xFF;
        if (style == MapStyle.HIGH_CONTRAST) {
            if (water) {
                return shore(t, i, x, z) ? 0xFFFFFFFF : MapPalette.HC_WATER;
            }
            int rel = h - o.seaLevel;
            int band = rel < 4 ? 0 : rel < 32 ? 1 : rel < 72 ? 2 : 3;
            return MapPalette.HC_BANDS[band];
        }
        int d = h - ((hn + hw) >> 1);
        int shade = d <= -2 ? SHADE[0] : d == -1 ? SHADE[1] : d == 0 ? SHADE[2] : d == 1 ? SHADE[3] : SHADE[4];
        if (style == MapStyle.HEIGHT) {
            if (water) {
                return 0xFF000000 | waterSteps(MapPalette.HEIGHT_WATER & 0xFFFFFF, depth, 0, false);
            }
            int band = Math.floorDiv(h - o.seaLevel, 4) * 4;
            int rgb = ramp(band);
            if (Math.floorMod(h, 16) == 0) {
                rgb = mulRgb(rgb, 205);
            }
            return 0xFF000000 | mulRgb(rgb, shade);
        }
        if (style == MapStyle.BIOME) {
            int rgb = water ? c.water[bi] : c.style[bi];
            int half = 256 + ((shade - 256) >> 1);
            rgb = mulRgb(rgb, half);
            boolean border = (z > 0 && t.present(i - Tile.SIZE) && t.biome[i - Tile.SIZE] != t.biome[i])
                    || (x > 0 && t.present(i - 1) && t.biome[i - 1] != t.biome[i]);
            if (border) {
                rgb = mulRgb(rgb, 205);
            }
            return 0xFF000000 | rgb;
        }
        int packed = c.block[t.block[i]];
        int base = tinted(packed, c, bi);
        if (water) {
            // the water texture tinted by the biome water colour, as the game draws it seen from above
            int w = multiply(c.water[bi], c.waterTexture);
            if (style == MapStyle.FLAT) {
                return 0xFF000000 | w;
            }
            return 0xFF000000 | waterSteps(w, depth, base, shore(t, i, x, z));
        }
        if (style == MapStyle.FLAT) {
            return 0xFF000000 | base;
        }
        return 0xFF000000 | mulRgb(base, shade);
    }

    private static int tinted(int packed, TileColours c, int bi) {
        int rgb = ColourTable.rgb(packed);
        switch (ColourTable.tint(packed)) {
            case ColourTable.TINT_GRASS:
                return multiply(rgb, c.grass[bi]);
            case ColourTable.TINT_FOLIAGE:
                return multiply(rgb, c.foliage[bi]);
            case ColourTable.TINT_WATER:
                return multiply(rgb, c.water[bi]);
            default:
                return rgb;
        }
    }

    /** Four depth steps on the water colour; the shallowest blends 35 % of the bed in; shoreline +12 %. */
    static int waterSteps(int water, int depth, int bed, boolean shore) {
        int rgb;
        if (depth <= 2) {
            rgb = blendRgb(water, bed, 90);
        } else if (depth <= 6) {
            rgb = mulRgb(water, 220);
        } else if (depth <= 15) {
            rgb = mulRgb(water, 184);
        } else {
            rgb = mulRgb(water, 148);
        }
        return shore ? mulRgb(rgb, 287) : rgb;
    }

    private static boolean shore(Tile t, int i, int x, int z) {
        return land(t, x > 0 ? i - 1 : -1) || land(t, x < 255 ? i + 1 : -1) || land(t, z > 0 ? i - Tile.SIZE : -1)
                || land(t, z < 255 ? i + Tile.SIZE : -1);
    }

    private static boolean land(Tile t, int i) {
        return i >= 0 && t.present(i) && (t.flags[i] & Tile.F_FLUID) == 0;
    }

    /** Unsurveyed ground: void with a deterministic one-in-96 star speckle. */
    public static int fog(int wx, int wz, MapStyle style) {
        if (style == MapStyle.HIGH_CONTRAST) {
            return 0xFF000000;
        }
        int hsh = wx * 0x27D4EB2D ^ wz * 0x165667B1;
        hsh ^= hsh >>> 15;
        hsh *= 0x2C1B3C6D;
        hsh ^= hsh >>> 12;
        return Integer.remainderUnsigned(hsh, 96) == 0 ? MapPalette.VOID_STAR : MapPalette.VOID;
    }

    static int ramp(int rel) {
        int[] s = MapPalette.HEIGHT_STOPS;
        if (rel <= s[0]) {
            return s[1] & 0xFFFFFF;
        }
        for (int k = 2; k < s.length; k += 2) {
            if (rel <= s[k]) {
                int a = s[k - 2];
                int t = (rel - a) * 256 / Math.max(1, s[k] - a);
                return blendRgb(s[k - 1] & 0xFFFFFF, s[k + 1] & 0xFFFFFF, t);
            }
        }
        return s[s.length - 1] & 0xFFFFFF;
    }

    /** Multiplies each channel by {@code f / 256}, clamped. */
    public static int mulRgb(int rgb, int f) {
        int r = Math.min(255, (((rgb >> 16) & 0xFF) * f) >> 8);
        int g = Math.min(255, (((rgb >> 8) & 0xFF) * f) >> 8);
        int b = Math.min(255, ((rgb & 0xFF) * f) >> 8);
        return (r << 16) | (g << 8) | b;
    }

    private static int scale(int argb, int f) {
        return (argb & 0xFF000000) | mulRgb(argb & 0xFFFFFF, f);
    }

    /** Per-channel multiply of two RGB colours. */
    public static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    /** {@code a} blended toward {@code b} by {@code t / 256}. */
    public static int blendRgb(int a, int b, int t) {
        int u = 256 - t;
        int r = (((a >> 16) & 0xFF) * u + ((b >> 16) & 0xFF) * t) >> 8;
        int g = (((a >> 8) & 0xFF) * u + ((b >> 8) & 0xFF) * t) >> 8;
        int bl = ((a & 0xFF) * u + (b & 0xFF) * t) >> 8;
        return (r << 16) | (g << 8) | bl;
    }

    private static int mix(int argb, int over, int t) {
        return (argb & 0xFF000000) | blendRgb(argb & 0xFFFFFF, over & 0xFFFFFF, t);
    }

    /** The night tint as a vertex colour multiplier for {@code darkness} in 0..1 (0 = day). */
    public static int nightTint(float darkness) {
        float s = Math.max(0f, Math.min(1f, darkness)) * MapPalette.NIGHT_MAX;
        int n = MapPalette.NIGHT;
        int r = Math.round(255 + (((n >> 16) & 0xFF) - 255) * s);
        int g = Math.round(255 + (((n >> 8) & 0xFF) - 255) * s);
        int b = Math.round(255 + ((n & 0xFF) - 255) * s);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Sky darkness 0..1 from the time of day (0..23999), eased over the vanilla twilight. */
    public static float darkness(long dayTime) {
        int t = (int) Math.floorMod(dayTime, 24000L);
        float d;
        if (t < 12000) {
            d = 0f;
        } else if (t < 13800) {
            d = (t - 12000) / 1800f;
        } else if (t < 22200) {
            d = 1f;
        } else {
            d = 1f - (t - 22200) / 1800f;
        }
        return d * d * (3 - 2 * d);
    }
}
