package za.co.neroland.neromaps.client.texture;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.colour.ColourTable;
import za.co.neroland.neromaps.colour.MapRasterizer;
import za.co.neroland.neromaps.colour.RasterOptions;
import za.co.neroland.neromaps.colour.TileLookup;
import za.co.neroland.neromaps.engine.LongObjMap;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * The world map's tile textures (decision 005): one 256 x 256 texture per visible tile at its level of detail,
 * pooled, least-recently-used, capped by the texture budget, filled a chunk patch at a time under a per-frame
 * patch budget so a big pan never stalls a frame. Render thread only.
 */
public final class WorldMapTextures {

    /** Chunk patches rasterised and uploaded per frame across all visible tiles. */
    public static final int PATCHES_PER_FRAME = 96;

    private static final class Entry {
        final MapTexture texture;
        final int[] revisions = new int[256];
        @Nullable Tile source;
        int rasterVersion = -1;
        long lastUse;

        Entry(MapTexture t) {
            texture = t;
            java.util.Arrays.fill(revisions, Integer.MIN_VALUE);
        }
    }

    private final LongObjMap<Entry> entries = new LongObjMap<>(128);
    private final java.util.ArrayDeque<MapTexture> free = new java.util.ArrayDeque<>();
    private final int[] patch = new int[256];
    private final int capacity;
    private int created;
    private long frame;
    private int budget;

    /** @param capacity textures this pool may hold */
    public WorldMapTextures(int capacity) {
        this.capacity = Math.max(16, capacity);
    }

    public void beginFrame() {
        frame++;
        budget = PATCHES_PER_FRAME;
    }

    /**
     * The texture for {@code tile} at {@code key}, refreshed within this frame's patch budget, or null if the pool
     * is exhausted this frame.
     */
    public @Nullable MapTexture texture(long key, Tile tile, TileLookup lookup, ColourTable table, RasterOptions o,
            int rasterVersion) {
        Entry e = entries.get(key);
        if (e == null) {
            MapTexture t = free.poll();
            if (t == null) {
                if (created >= capacity && !evict()) {
                    return null;
                }
                t = free.poll();
                if (t == null) {
                    t = new MapTexture("worldmap_" + created++, Tile.SIZE);
                }
            }
            e = new Entry(t);
            entries.put(key, e);
        }
        e.lastUse = frame;
        if (e.source != tile || e.rasterVersion != rasterVersion) {
            e.source = tile;
            e.rasterVersion = rasterVersion;
            java.util.Arrays.fill(e.revisions, Integer.MIN_VALUE);
        }
        int tx = TileKey.tx(key);
        int tz = TileKey.tz(key);
        for (int c = 0; c < 256 && budget > 0; c++) {
            int rev = tile.chunkRevision[c];
            if (e.revisions[c] != rev) {
                int cx = c & 15;
                int cz = c >> 4;
                MapRasterizer.chunk(tile, tx, tz, cx, cz, lookup, table, o, patch, 0, 16);
                e.texture.upload16(patch, 0, 16, cx * 16, cz * 16);
                e.revisions[c] = rev;
                budget--;
            }
        }
        return e.texture;
    }

    /** Frees the least-recently-used texture not drawn this frame. */
    private boolean evict() {
        long[] best = {Long.MIN_VALUE};
        long[] use = {Long.MAX_VALUE};
        long now = frame;
        entries.forEach((k, e) -> {
            if (e.lastUse < now && e.lastUse < use[0]) {
                use[0] = e.lastUse;
                best[0] = k;
            }
        });
        if (best[0] == Long.MIN_VALUE) {
            return false;
        }
        Entry e = entries.remove(best[0]);
        if (e != null) {
            free.add(e.texture);
        }
        return true;
    }

    /** Releases every texture (screen closed). */
    public void close() {
        entries.forEach((k, e) -> e.texture.close());
        entries.clear();
        for (MapTexture t : free) {
            t.close();
        }
        free.clear();
        PerfCounters.textureBytes = Math.max(0, PerfCounters.textureBytes);
    }
}
