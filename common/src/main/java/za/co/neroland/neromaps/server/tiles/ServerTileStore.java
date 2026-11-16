package za.co.neroland.neromaps.server.tiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;
import za.co.neroland.neromaps.tile.TileLod;

/**
 * One dimension's server tiles, owned by the capture worker thread. LOD-0 tiles are updated chunk by
 * chunk from scans and written as plain files ({@code <dir>/<lod>/<tx>.<tz>.nmt}) with atomic replace; parents at
 * LOD 1-4 are rebuilt incrementally from their four children whenever a child was written. Decoded tiles and their
 * encoded bytes are kept in memory up to a byte budget. A corrupt or unknown-schema file is deleted, counted, and its
 * 256 chunks are reported for recapture; nothing is logged per tile.
 */
public final class ServerTileStore implements LinkTiles.Source {

    /** Approximate memory of one decoded tile (column arrays). */
    public static final long TILE_BYTES = (long) Tile.AREA * 7;

    /** One encoded tile and its content etag. */
    public record Encoded(byte[] bytes, long etag) { }

    private final TileDiskCache disk;
    private final Path dir;
    private final LongConsumer recapture;
    private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    private final LinkedHashMap<Long, Encoded> encoded = new LinkedHashMap<>(64, 0.75f, true);
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final LongOpenHashSet staleParents = new LongOpenHashSet();
    private long encodedBytes;
    private long memoryBudget;
    private final CapturePerf perf;
    private LinkTiles.@Nullable Listener listener;

    /**
     * @param recapture told the packed chunk key ({@link DirtyMarks#key}) of every chunk of a corrupt LOD-0 tile
     */
    public ServerTileStore(TileDiskCache disk, Path dir, long memoryBudget, CapturePerf perf, LongConsumer recapture) {
        this.disk = disk;
        this.dir = dir;
        this.memoryBudget = Math.max(TILE_BYTES * 4, memoryBudget);
        this.perf = perf;
        this.recapture = recapture;
    }

    public void setMemoryBudget(long bytes) {
        memoryBudget = Math.max(TILE_BYTES * 4, bytes);
    }

    public Path dir() {
        return dir;
    }

    /** Tells the link module about every tile written or dropped (worker thread). */
    public void setListener(LinkTiles.@Nullable Listener l) {
        listener = l;
    }

    // --- writes ------------------------------------------------------------------------------------------------

    /**
     * Writes one scanned chunk into its LOD-0 tile (loaded from disk first, so other chunks are kept). A rescan that
     * leaves every pixel as it was (crops growing under a roof, redstone, furnaces) changes nothing: the tile is not
     * marked unsaved, nothing is rewritten and the link module is told nothing, so block activity that does not change
     * the map leaves no trace any viewer could read.
     */
    public void apply(int chunkX, int chunkZ, ChunkColumns c, String[] blockIds, String[] biomeIds) {
        int tx = chunkX >> 4;
        int tz = chunkZ >> 4;
        long key = TileKey.tile(0, tx, tz);
        Tile t = tile(0, tx, tz);
        boolean fresh = t == null;
        if (t == null) {
            t = new Tile(0);
        }
        int x0 = (chunkX & 15) << 4;
        int z0 = (chunkZ & 15) << 4;
        boolean diff = false;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = (z << 4) | x;
                int f = c.flags[i] & 0xFF;
                int ti = Tile.index(x0 + x, z0 + z);
                if ((f & Tile.F_PRESENT) == 0) {
                    if (t.present(ti)) {
                        t.set(x0 + x, z0 + z, 0, 0, 0, 0, 0);
                        diff = true;
                    }
                    continue;
                }
                int b = t.internBlock(blockIds[i]);
                int bi = t.internBiome(biomeIds[i]);
                int d = c.depth[i] & 0xFF;
                int h = Math.max(Tile.MIN_HEIGHT, Math.min(Tile.MAX_HEIGHT, c.height[i]));
                if (!t.present(ti) || (t.flags[ti] & 0xFF) != f || t.height[ti] != h || t.block[ti] != b
                        || (t.biome[ti] & 0xFF) != bi || (t.depth[ti] & 0xFF) != d) {
                    t.set(x0 + x, z0 + z, f, h, b, bi, d);
                    diff = true;
                }
            }
        }
        if (!diff) {
            return;
        }
        if (fresh) {
            tiles.put(key, t);
        }
        t.touchChunk(chunkX & 15, chunkZ & 15);
        t.unsaved = true;
        dirty.add(key);
        Encoded old = encoded.remove(key);
        if (old != null) {
            encodedBytes -= old.bytes.length;
        }
        LinkTiles.Listener l = listener;
        if (l != null) {
            l.chunkChanged(chunkX, chunkZ);
        }
    }

    /** Encodes and writes every changed LOD-0 tile, then rebuilds and writes the stale parents level by level. */
    public int flush() {
        int written = 0;
        LongIterator it = dirty.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            Tile t = tiles.get(key);
            if (t != null && t.unsaved) {
                write(key, t);
                written++;
                long p = TileKey.tile(1, TileKey.tx(key) >> 1, TileKey.tz(key) >> 1);
                staleParents.add(p);
            }
        }
        dirty.clear();
        for (int lod = 1; lod <= TileLod.MAX_LOD; lod++) {
            LongOpenHashSet next = new LongOpenHashSet();
            LongIterator pit = staleParents.iterator();
            while (pit.hasNext()) {
                long key = pit.nextLong();
                if (TileKey.lod(key) != lod) {
                    continue;
                }
                pit.remove();
                int tx = TileKey.tx(key);
                int tz = TileKey.tz(key);
                int child = lod - 1;
                long t0 = System.nanoTime();
                Tile parent = TileLod.downsample(tile(child, tx * 2, tz * 2), tile(child, tx * 2 + 1, tz * 2),
                        tile(child, tx * 2, tz * 2 + 1), tile(child, tx * 2 + 1, tz * 2 + 1), lod);
                perf.lodNanos.addAndGet(System.nanoTime() - t0);
                perf.lodBuilds.incrementAndGet();
                tiles.put(key, parent);
                write(key, parent);
                written++;
                if (lod < TileLod.MAX_LOD) {
                    next.add(TileKey.tile(lod + 1, tx >> 1, tz >> 1));
                }
            }
            staleParents.addAll(next);
        }
        trim();
        return written;
    }

    private void write(long key, Tile t) {
        long t0 = System.nanoTime();
        byte[] bytes = TileCodec.encode(t);
        perf.encodeNanos.addAndGet(System.nanoTime() - t0);
        perf.encodes.incrementAndGet();
        try {
            disk.write(file(key), bytes);
            perf.writes.incrementAndGet();
            t.unsaved = false;
            long etag = TileCodec.contentHash(bytes);
            Encoded old = encoded.put(key, new Encoded(bytes, etag));
            encodedBytes += bytes.length - (old == null ? 0 : old.bytes.length);
            LinkTiles.Listener l = listener;
            if (l != null) {
                l.written(key, bytes, etag);
            }
        } catch (IOException | TileCorruptException e) {
            perf.writeErrors.incrementAndGet();
        }
    }

    private Path file(long key) {
        return TileDiskCache.tileFile(dir, TileKey.lod(key), TileKey.tx(key), TileKey.tz(key));
    }

    // --- reads -------------------------------------------------------------------------------------------------

    /** A decoded tile from memory or disk, or null when none was captured (or the file was corrupt). */
    public @Nullable Tile tile(int lod, int tx, int tz) {
        long key = TileKey.tile(lod, tx, tz);
        Tile t = tiles.get(key);
        if (t != null) {
            return t;
        }
        Path f = file(key);
        if (!disk.exists(f)) {
            return null;
        }
        byte[] b = disk.read(f);
        if (b == null) {
            return null;
        }
        try {
            t = TileCodec.decode(b);
            tiles.put(key, t);
            Encoded old = encoded.put(key, new Encoded(b, TileCodec.contentHash(b)));
            encodedBytes += b.length - (old == null ? 0 : old.bytes.length);
            perf.reads.incrementAndGet();
            trim();
            return t;
        } catch (TileCorruptException e) {
            disk.delete(f);
            perf.corrupt.incrementAndGet();
            LinkTiles.Listener l = listener;
            if (l != null) {
                l.removed(key);
            }
            if (lod == 0) {
                for (int cz = 0; cz < 16; cz++) {
                    for (int cx = 0; cx < 16; cx++) {
                        recapture.accept(DirtyMarks.key(tx * 16 + cx, tz * 16 + cz));
                    }
                }
            } else {
                staleParents.add(key);
            }
            return null;
        }
    }

    /** The encoded bytes and etag of a tile, or null when none was captured. */
    @Override
    public @Nullable Encoded encoded(int lod, int tx, int tz) {
        long key = TileKey.tile(lod, tx, tz);
        Encoded e = encoded.get(key);
        if (e != null) {
            return e;
        }
        Tile t = tile(lod, tx, tz);
        if (t == null) {
            return null;
        }
        e = encoded.get(key);
        if (e == null) {
            byte[] bytes = TileCodec.encode(t);
            try {
                e = new Encoded(bytes, TileCodec.contentHash(bytes));
            } catch (TileCorruptException ex) {
                return null;
            }
            encoded.put(key, e);
            encodedBytes += bytes.length;
        }
        return e;
    }

    /** Every stored tile's content etag, read from file headers only (link index warm-up; worker thread). */
    @Override
    public void forEachEtag(LinkTiles.KeyEtag sink) {
        for (int lod = 0; lod <= TileLod.MAX_LOD; lod++) {
            for (int[] t : disk.list(dir, lod)) {
                long key = TileKey.tile(lod, t[0], t[1]);
                Encoded e = encoded.get(key);
                if (e != null) {
                    sink.accept(key, e.etag());
                    continue;
                }
                byte[] head = disk.readHead(file(key), TileCodec.HEADER);
                if (head == null) {
                    continue;
                }
                try {
                    sink.accept(key, TileCodec.contentHash(head));
                } catch (TileCorruptException ex) {
                    // left for the next read to recover
                }
            }
        }
    }

    /** Drops decoded tiles (saving unsaved ones first) and encoded bytes beyond the memory budget. */
    void trim() {
        long budget = memoryBudget;
        Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
        while ((tiles.size() * TILE_BYTES + encodedBytes) > budget && it.hasNext()) {
            Map.Entry<Long, Tile> e = it.next();
            if (e.getValue().unsaved) {
                continue; // written by the next flush, dropped after
            }
            it.remove();
            perf.memoryEvictions.incrementAndGet();
        }
        Iterator<Map.Entry<Long, Encoded>> ei = encoded.entrySet().iterator();
        while ((tiles.size() * TILE_BYTES + encodedBytes) > budget && ei.hasNext()) {
            encodedBytes -= ei.next().getValue().bytes.length;
            ei.remove();
        }
    }

    public int memoryTiles() {
        return tiles.size();
    }

    public long encodedBytes() {
        return encodedBytes;
    }

    public int pendingWrites() {
        return dirty.size();
    }
}
