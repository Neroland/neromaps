package za.co.neroland.neromaps.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.colour.TileLookup;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;
import za.co.neroland.neromaps.tile.TileLod;

/**
 * The client's map of one dimension: live LOD-0 tiles in memory (LRU-bounded), cave-slice tiles (memory only),
 * LOD tiles for the world map, and the disk cache behind them. Main thread owns every tile; the I/O executor
 * only ever sees copies or tiles that have left the map. Pure Java (no Minecraft types), so it is unit-tested.
 */
public final class MapStore implements TileLookup {

    private final LongObjMap<Tile> tiles = new LongObjMap<>(64);
    private final LongObjMap<Tile> caveTiles = new LongObjMap<>(16);
    private final LongObjMap<Tile> lodTiles = new LongObjMap<>(64);
    private final LongObjMap<ArrayList<ScanResult>> waiting = new LongObjMap<>();
    private final LongObjMap<Boolean> lodRequested = new LongObjMap<>();
    private final Set<Long> stale = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Object[]> loaded = new ConcurrentLinkedQueue<>();
    private final @Nullable TileDiskCache cache;
    private final @Nullable Path dir;
    private final Executor io;
    private int memoryTiles;
    private long clock;
    private volatile boolean closed;

    /** @param dir this dimension's cache directory, or null for a memory-only map */
    public MapStore(@Nullable TileDiskCache cache, @Nullable Path dir, Executor io, int memoryTiles) {
        this.cache = cache;
        this.dir = dir;
        this.io = io;
        this.memoryTiles = memoryTiles;
    }

    public void setMemoryTiles(int n) {
        memoryTiles = n;
    }

    public int tileCount() {
        return tiles.size();
    }

    // --- reads (main thread) ------------------------------------------------------------------------

    @Override
    public @Nullable Tile get(int lod, int tx, int tz) {
        if (lod == 0) {
            Tile t = tiles.get(TileKey.tile(0, tx, tz));
            if (t != null) {
                t.lastUse = clock;
            }
            return t;
        }
        return lodTiles.get(TileKey.tile(lod, tx, tz));
    }

    public @Nullable Tile cave(int tx, int tz) {
        return caveTiles.get(TileKey.tile(0, tx, tz));
    }

    /** A {@link TileLookup} over the cave slice. */
    public final TileLookup caveLookup = (lod, tx, tz) -> lod == 0 ? cave(tx, tz) : null;

    /** Per-chunk revision for the clipmap: -1 when the tile is not in memory. */
    public int revision(int chunkX, int chunkZ, boolean caveMode) {
        Tile t = caveMode ? cave(chunkX >> 4, chunkZ >> 4) : tiles.get(TileKey.tile(0, chunkX >> 4, chunkZ >> 4));
        return t == null ? -1 : t.chunkRevision[((chunkZ & 15) << 4) | (chunkX & 15)];
    }

    // --- writes (main thread) -----------------------------------------------------------------------

    /** Applies one scan result. Surface results for a tile still on disk wait until it is loaded. */
    public void apply(ScanResult r) {
        int tx = r.chunkX >> 4;
        int tz = r.chunkZ >> 4;
        long key = TileKey.tile(0, tx, tz);
        if (r.cave) {
            Tile t = caveTiles.get(key);
            if (t == null) {
                t = new Tile(0);
                caveTiles.put(key, t);
            }
            write(t, r);
            return;
        }
        Tile t = tiles.get(key);
        if (t == null) {
            ArrayList<ScanResult> w = waiting.get(key);
            if (w != null) {
                w.add(r);
                return;
            }
            if (cache != null && dir != null && cache.exists(TileDiskCache.tileFile(dir, 0, tx, tz))) {
                w = new ArrayList<>();
                w.add(r);
                waiting.put(key, w);
                requestLoad(tx, tz);
                return;
            }
            t = new Tile(0);
            tiles.put(key, t);
        }
        write(t, r);
        markAncestorsStale(tx, tz);
    }

    private void write(Tile t, ScanResult r) {
        ChunkColumns c = r.columns;
        int x0 = (r.chunkX & 15) << 4;
        int z0 = (r.chunkZ & 15) << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = (z << 4) | x;
                int f = c.flags[i] & 0xFF;
                if ((f & Tile.F_PRESENT) == 0) {
                    t.set(x0 + x, z0 + z, 0, 0, 0, 0, 0);
                    continue;
                }
                t.set(x0 + x, z0 + z, f, c.height[i], t.internBlock(r.blockIds[i]), t.internBiome(r.biomeIds[i]),
                        c.depth[i] & 0xFF);
            }
        }
        t.touchChunk(r.chunkX & 15, r.chunkZ & 15);
        t.lastUse = clock;
    }

    /** Drops the cave slice (slice moved, cave mode off, dimension left). */
    public void clearCave() {
        caveTiles.clear();
    }

    /** Main-thread housekeeping: installs loads, evicts beyond the memory budget. {@code keep*} is the player. */
    public void tick(int keepTx, int keepTz) {
        clock++;
        Object[] l;
        while ((l = loaded.poll()) != null) {
            install((long) l[0], (Tile) l[1]);
        }
        if (tiles.size() > memoryTiles) {
            evictOne(keepTx, keepTz);
        }
        PerfCounters.tilesInMemory = tiles.size() + lodTiles.size() + caveTiles.size();
    }

    private void install(long key, @Nullable Tile fromDisk) {
        if (TileKey.lod(key) > 0) {
            lodRequested.remove(key);
            if (fromDisk != null && !stale.contains(key)) {
                lodTiles.put(key, fromDisk);
                if (lodTiles.size() > 96) {
                    evictLod();
                }
            }
            return;
        }
        Tile t = fromDisk != null ? fromDisk : new Tile(0);
        Tile live = tiles.get(key);
        if (live != null) {
            // scanned while loading: the live chunks win
            for (int cz = 0; cz < 16; cz++) {
                for (int cx = 0; cx < 16; cx++) {
                    if (!live.chunkPresent(cx, cz) && t.chunkPresent(cx, cz)) {
                        live.copyChunkFrom(t, cx, cz);
                        live.touchChunk(cx, cz);
                    }
                }
            }
        } else {
            t.revision = 1;
            java.util.Arrays.fill(t.chunkRevision, 1);
            t.lastUse = clock;
            tiles.put(key, t);
        }
        ArrayList<ScanResult> w = waiting.remove(key);
        if (w != null) {
            Tile target = tiles.get(key);
            for (ScanResult r : w) {
                if (target != null) {
                    write(target, r);
                }
            }
            markAncestorsStale(TileKey.tx(key), TileKey.tz(key));
        }
    }

    private void evictOne(int keepTx, int keepTz) {
        long[] best = {Long.MIN_VALUE};
        long[] bestUse = {Long.MAX_VALUE};
        tiles.forEach((k, t) -> {
            int tx = TileKey.tx(k);
            int tz = TileKey.tz(k);
            if (Math.abs(tx - keepTx) <= 1 && Math.abs(tz - keepTz) <= 1) {
                return;
            }
            if (t.lastUse < bestUse[0]) {
                bestUse[0] = t.lastUse;
                best[0] = k;
            }
        });
        if (best[0] == Long.MIN_VALUE) {
            return;
        }
        Tile t = tiles.remove(best[0]);
        if (t != null && t.unsaved) {
            t.colours = null;
            saveAsync(best[0], t);
        }
    }

    private void evictLod() {
        long[] best = {Long.MIN_VALUE};
        long[] use = {Long.MAX_VALUE};
        lodTiles.forEach((k, t) -> {
            if (t.lastUse < use[0]) {
                use[0] = t.lastUse;
                best[0] = k;
            }
        });
        if (best[0] != Long.MIN_VALUE) {
            lodTiles.remove(best[0]);
        }
    }

    /** Queues a save of every unsaved tile (periodic flush, world-map open, dimension change). */
    public void flush() {
        tiles.forEach((k, t) -> {
            if (t.unsaved) {
                t.unsaved = false;
                saveAsync(k, t.copy());
            }
        });
    }

    private void saveAsync(long key, Tile t) {
        if (cache == null || dir == null) {
            return;
        }
        TileDiskCache c = cache;
        Path file = TileDiskCache.tileFile(dir, 0, TileKey.tx(key), TileKey.tz(key));
        submit(() -> {
            try {
                c.write(file, TileCodec.encode(t));
            } catch (IOException | RuntimeException e) {
                // cache only: the next session recaptures
            }
        });
    }

    private void requestLoad(int tx, int tz) {
        TileDiskCache c = cache;
        Path d = dir;
        if (c == null || d == null) {
            loaded.add(new Object[] {TileKey.tile(0, tx, tz), null});
            return;
        }
        long key = TileKey.tile(0, tx, tz);
        submit(() -> loaded.add(new Object[] {key, readTile(c, TileDiskCache.tileFile(d, 0, tx, tz))}));
    }

    private static @Nullable Tile readTile(TileDiskCache c, Path file) {
        byte[] b = c.read(file);
        if (b == null) {
            return null;
        }
        try {
            return TileCodec.decode(b);
        } catch (TileCorruptException e) {
            // dropped and recaptured; counted, never logged per tile
            PerfCounters.corruptTiles.incrementAndGet();
            c.delete(file);
            return null;
        }
    }

    private void submit(Runnable r) {
        if (closed) {
            return;
        }
        try {
            PerfCounters.ioQueue.incrementAndGet();
            io.execute(() -> {
                try {
                    r.run();
                } finally {
                    PerfCounters.ioQueue.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            PerfCounters.ioQueue.decrementAndGet();
        }
    }

    // --- level of detail (world map) ---------------------------------------------------------------------

    private void markAncestorsStale(int tx, int tz) {
        for (int l = 1; l <= TileLod.MAX_LOD; l++) {
            long k = TileKey.tile(l, tx >> l, tz >> l);
            stale.add(k);
            lodTiles.remove(k);
        }
    }

    /**
     * The tile at {@code lod} for the world map. LOD 0 comes from memory or is loaded; higher levels are read from
     * the cache or rebuilt on the worker from their children. Returns null while it is being fetched.
     */
    public @Nullable Tile request(int lod, int tx, int tz) {
        long key = TileKey.tile(lod, tx, tz);
        Tile t = lod == 0 ? tiles.get(key) : lodTiles.get(key);
        if (t != null) {
            t.lastUse = clock;
            return t;
        }
        if (lodRequested.containsKey(key) || (lod == 0 && waiting.containsKey(key))) {
            return null;
        }
        if (lod == 0) {
            if (cache != null && dir != null && cache.exists(TileDiskCache.tileFile(dir, 0, tx, tz))) {
                waiting.put(key, new ArrayList<>());
                requestLoad(tx, tz);
            }
            return null;
        }
        if (cache == null || dir == null) {
            return null;
        }
        lodRequested.put(key, Boolean.TRUE);
        TileDiskCache c = cache;
        Path d = dir;
        submit(() -> loaded.add(new Object[] {key, buildLod(c, d, lod, tx, tz)}));
        return null;
    }

    /** Worker: reads a fresh LOD tile from disk or rebuilds it from its children (recursively). */
    private @Nullable Tile buildLod(TileDiskCache c, Path d, int lod, int tx, int tz) {
        long key = TileKey.tile(lod, tx, tz);
        Path file = TileDiskCache.tileFile(d, lod, tx, tz);
        boolean wasStale = stale.remove(key);
        if (!wasStale && c.exists(file)) {
            Tile t = readTile(c, file);
            if (t != null) {
                return t;
            }
        }
        if (lod == 0) {
            return c.exists(file) ? readTile(c, file) : null;
        }
        Tile nw = buildLod(c, d, lod - 1, tx * 2, tz * 2);
        Tile ne = buildLod(c, d, lod - 1, tx * 2 + 1, tz * 2);
        Tile sw = buildLod(c, d, lod - 1, tx * 2, tz * 2 + 1);
        Tile se = buildLod(c, d, lod - 1, tx * 2 + 1, tz * 2 + 1);
        if (nw == null && ne == null && sw == null && se == null) {
            return null;
        }
        Tile parent = TileLod.downsample(nw, ne, sw, se, lod);
        try {
            c.write(file, TileCodec.encode(parent));
        } catch (IOException | RuntimeException e) {
            // memory copy still serves this session
        }
        return parent;
    }

    /** Stops accepting I/O; saves pending tiles first. */
    public void close() {
        flush();
        closed = true;
        tiles.clear();
        caveTiles.clear();
        lodTiles.clear();
        waiting.clear();
    }

    /** Visits LOD-0 tiles in memory (world map listing). */
    public void forEachTile(LongObjMap.Visitor<Tile> v) {
        tiles.forEach(v);
    }
}
