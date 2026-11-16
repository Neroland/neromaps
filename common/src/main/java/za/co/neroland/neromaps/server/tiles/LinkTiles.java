package za.co.neroland.neromaps.server.tiles;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.fog.TileMask;
import za.co.neroland.neromaps.fog.Visibility;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;
import za.co.neroland.neromaps.tile.TileLod;

/**
 * The {@code tiles} section of the {@code neromaps} link module: a cursor/want-mask protocol over blocks of 8 x 8 server
 * tiles, fog-checked on every read.
 *
 * <p><b>Threads.</b> {@link #index} and {@link #page} run on the server thread (the bridge calls snapshots there) and
 * do only visibility bit work, map lookups and string assembly: no inflate, deflate, base64, hashing of tile bytes or
 * file I/O. Everything else (learning the etags of tiles already on disk, masking, encoding, base64) runs on the capture
 * worker, which fills a concurrent etag index, a bounded index of masked variants and a bounded cache of
 * <b>pre-encoded base64 strings</b>. A cache miss answers {@code pending} and queues the worker.
 *
 * <p><b>Fog.</b> A tile with no visible chunk is reported exactly like a tile that was never captured: {@code null} in
 * the index, silently omitted from a page, nothing queued, so neither content, shape nor timing tells them apart. A
 * partly visible tile is sent as a masked copy, and everything the viewer can observe about it is derived from that
 * copy only: its etag is the content hash of the masked bytes, its change stamp moves only when those bytes change,
 * the cursor is the newest stamp among the tiles this viewer can see, and a change in a hidden chunk is served from the
 * previous masked copy while the worker rebuilds it in the background (it never turns the tile {@code pending}). So a
 * block change outside a viewer's fog changes nothing they can read: not the etag, the {@code changed} bits, the
 * cursor, the pending mask or the {@code tiles} events (which are filtered per changed chunk).
 *
 * <p><b>Size.</b> A page stops before {@link #PAGE_BYTES} (sum of base64 lengths + {@value #ENTRY_OVERHEAD} per entry,
 * with {@value #PAGE_RESERVE} bytes kept for the JSON around them); the rest is listed in {@code next}. Each viewer may
 * draw at most {@link #VIEWER_BYTES_PER_MINUTE} of tile payload per minute; past that the wanted tiles come back as
 * {@code pending} with a {@code retryMs}.
 *
 * <p>Holds no identity on disk: viewer ids live in memory only (rate windows, the "recently used the app" set for
 * coarse tile events) and end with the server or the player's erasure.
 */
public final class LinkTiles {

    public static final int PAGE_BYTES = 262_144;
    public static final int ENTRY_OVERHEAD = 48;
    public static final int PAGE_RESERVE = 512;
    public static final long VIEWER_BYTES_PER_MINUTE = 8L << 20;
    public static final long PAYLOAD_CACHE_BYTES = 48L << 20;
    /** Memory for the masked-variant index (masks plus their etags and stamps). */
    public static final long MASKED_CACHE_BYTES = 16L << 20;
    public static final int RETRY_MS = 750;
    public static final int MAX_LOD = TileLod.MAX_LOD;
    /** How long after a player's last tile read they still receive coarse {@code tiles} events. */
    public static final long RECENT_MS = 10 * 60_000L;
    /** Changed LOD-0 chunks remembered per dimension between two tile events. */
    public static final int MAX_CHANGED_CHUNKS = 16_384;
    public static final int BLOCK = 8;
    public static final int TILES = BLOCK * BLOCK;

    /**
     * The {@code etags[i]} placeholder an index answers while a tile's etag is not known yet for this viewer: during
     * the warm-up after a server start (the dimension's etags are still being learned from disk), and the first time a
     * viewer's mask of a partly visible tile is asked for (its masked etag is computed on the worker). It is never 16
     * hex digits, so it never equals an etag the app holds: the app asks for the tile, the page answers it as
     * {@code pending} (or omits it if it turns out to be missing) and nothing the app holds is deleted. An index with
     * any placeholder has an empty {@code cursor}, so the app keeps the one it holds and asks again.
     */
    public static final String WARMING_ETAG = "pending";

    /** What the worker reads a dimension's tiles through (the {@link ServerTileStore}). */
    public interface Source {
        @Nullable ServerTileStore.Encoded encoded(int lod, int tx, int tz);

        /** Every stored tile's content etag (file headers only). */
        void forEachEtag(KeyEtag sink);
    }

    @FunctionalInterface
    public interface KeyEtag {
        void accept(long key, long etag);
    }

    /** Runs a job on the capture worker with a dimension's {@link Source}; false when it could not be queued. */
    @FunctionalInterface
    public interface Worker {
        boolean submit(String dim, Consumer<Source> job);
    }

    /** Store callbacks (worker thread). */
    public interface Listener {
        void written(long key, byte[] bytes, long etag);

        void removed(long key);

        /** The pixels of LOD-0 chunk {@code (chunkX, chunkZ)} changed (feeds the per-chunk {@code tiles} events). */
        void chunkChanged(int chunkX, int chunkZ);
    }

    /** A pre-encoded tile: the content it was built from, the etag the viewer sees and its base64. */
    private record Payload(long contentEtag, long etag, String b64) {
        long bytes() {
            return b64.length() + 64L;
        }
    }

    /**
     * One mask's view of a tile: the content it was last built from, the masked etag, the stamp of the last change of
     * the masked bytes, and whether the mask shows no pixel at all ({@code empty}: reported like a missing tile).
     */
    private record Masked(String variantKey, TileMask mask, long contentEtag, long etag, long seq, boolean empty) {
        long bytes() {
            return mask.byteSize() + 160L;
        }
    }

    private static final class DimIndex {
        final ConcurrentHashMap<Long, long[]> etags = new ConcurrentHashMap<>(); // key -> {content etag, stamp}
        final Set<Long> changedChunks = ConcurrentHashMap.newKeySet();
        volatile boolean learned;
        volatile boolean learning;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The live instance, fed by {@link CaptureService}'s worker. */
    public static final LinkTiles LIVE = new LinkTiles((dim, job) -> CaptureService.onWorker(dim, job::accept));

    private final Worker worker;
    private final ConcurrentHashMap<String, DimIndex> dims = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, String> dirs = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Payload> payloads = new LinkedHashMap<>(256, 0.75f, true);
    private long payloadBytes;
    // guarded by `masked` (taken before `payloads`, never after)
    private final LinkedHashMap<String, Masked> masked = new LinkedHashMap<>(256, 0.75f, true);
    private final Map<String, Set<String>> variants = new HashMap<>(); // dim|key -> masked cache keys
    private long maskedBytes;
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    /** Change stamps: strictly increasing, close to wall-clock milliseconds, so a stamp carries no count of writes. */
    private final AtomicLong clock = new AtomicLong();
    // server thread only
    private final Map<UUID, long[]> spend = new HashMap<>(); // viewer -> {windowStartMs, bytes}
    private final Map<UUID, Long> recent = new HashMap<>();
    private volatile String epoch = newEpoch();
    // counters (global, no identity)
    public final AtomicLong indexCalls = new AtomicLong();
    public final AtomicLong pageCalls = new AtomicLong();
    public final AtomicLong serverNanos = new AtomicLong();
    public final AtomicLong tilesServed = new AtomicLong();
    public final AtomicLong bytesServed = new AtomicLong();
    public final AtomicLong misses = new AtomicLong();
    public final AtomicLong oversize = new AtomicLong();
    public final AtomicLong encodeNanos = new AtomicLong();
    public final AtomicLong learnNanos = new AtomicLong();
    public final AtomicLong sameWrites = new AtomicLong();
    public volatile long maxPageBytes;

    public LinkTiles(Worker worker) {
        this.worker = worker;
    }

    private static String newEpoch() {
        return Long.toString(RANDOM.nextLong() & 0xFFFFFFFFFFL, 36);
    }

    /** Forgets everything (server stopping). The next server gets a new epoch, so every cursor goes stale. */
    public void reset() {
        dims.clear();
        dirs.clear();
        synchronized (masked) {
            masked.clear();
            variants.clear();
            maskedBytes = 0;
        }
        synchronized (payloads) {
            payloads.clear();
            payloadBytes = 0;
        }
        inflight.clear();
        spend.clear();
        recent.clear();
        epoch = newEpoch();
    }

    /** Drops a viewer's in-memory state (logout of the app is not visible to us; erasure and server stop are). */
    public void forget(UUID viewer) {
        spend.remove(viewer);
        recent.remove(viewer);
    }

    public String epoch() {
        return epoch;
    }

    private DimIndex dim(String dim) {
        return dims.computeIfAbsent(dim, k -> new DimIndex());
    }

    private long stamp() {
        long now = System.currentTimeMillis();
        return clock.updateAndGet(p -> Math.max(p + 1, now));
    }

    // --- worker-side feeds ------------------------------------------------------------------------------------

    /** The store listener for one dimension, and the reverse map from its directory for disk deletions. */
    public Listener listener(String dim, Path dir) {
        dirs.put(dir.toAbsolutePath().normalize(), dim);
        return new Listener() {
            @Override
            public void written(long key, byte[] bytes, long etag) {
                DimIndex d = dim(dim);
                long[] cur = d.etags.get(key);
                if (cur != null && cur[0] == etag) {
                    sameWrites.incrementAndGet();
                    return; // identical bytes: nothing any viewer can read has changed
                }
                d.etags.put(key, new long[] {etag, stamp()});
                String full = cacheKey(dim, key, null);
                boolean demanded;
                synchronized (payloads) {
                    demanded = payloads.containsKey(full);
                }
                if (demanded) {
                    // someone reads this tile through the app: keep its base64 ready
                    long t0 = System.nanoTime();
                    putPayload(full, new Payload(etag, etag, Base64.getEncoder().encodeToString(bytes)));
                    encodeNanos.addAndGet(System.nanoTime() - t0);
                }
                refreshVariants(dim, key);
            }

            @Override
            public void removed(long key) {
                dim(dim).etags.remove(key);
                dropVariants(dim, key);
            }

            @Override
            public void chunkChanged(int chunkX, int chunkZ) {
                DimIndex d = dim(dim);
                if (d.changedChunks.size() < MAX_CHANGED_CHUNKS) {
                    d.changedChunks.add(chunkKey(chunkX, chunkZ));
                }
            }
        };
    }

    /** A server tile file was deleted (eviction or corruption): forget its etag. Any thread. */
    public void fileDeleted(Path file) {
        Path lodDir = file.getParent();
        if (lodDir == null) {
            return;
        }
        Path dimDir = lodDir.getParent();
        if (dimDir == null) {
            return;
        }
        String dim = dirs.get(dimDir.toAbsolutePath().normalize());
        if (dim == null) {
            return;
        }
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        int end = name.indexOf('.', dot + 1);
        try {
            int lod = Integer.parseInt(lodDir.getFileName().toString());
            long key = TileKey.tile(lod, Integer.parseInt(name.substring(0, dot)),
                    Integer.parseInt(name.substring(dot + 1, end)));
            dim(dim).etags.remove(key);
            dropVariants(dim, key);
        } catch (RuntimeException e) {
            // a foreign file
        }
    }

    /** Starts learning a dimension's on-disk etags once (worker). */
    void learn(String dim) {
        DimIndex d = dim(dim);
        if (d.learned || d.learning) {
            return;
        }
        d.learning = true;
        boolean queued = worker.submit(dim, src -> {
            long t0 = System.nanoTime();
            try {
                src.forEachEtag((key, etag) -> d.etags.putIfAbsent(key, new long[] {etag, 0L}));
            } finally {
                learnNanos.addAndGet(System.nanoTime() - t0);
                d.learned = true;
                d.learning = false;
            }
        });
        if (!queued) {
            d.learning = false;
        }
    }

    /** True once a dimension's etag index reflects the disk (tests and warm-up). */
    public boolean learned(String dim) {
        DimIndex d = dims.get(dim);
        return d != null && d.learned;
    }

    // --- masked variants ----------------------------------------------------------------------------------------

    private static String variantKey(String dim, long key) {
        return dim + '|' + key;
    }

    /** The masked entry for exactly {@code m} (the cache key is a hash; the mask bits are compared on a hit). */
    private @Nullable Masked maskedFor(String ck, TileMask m) {
        synchronized (masked) {
            Masked e = masked.get(ck);
            return e != null && e.mask().sameAs(m) ? e : null;
        }
    }

    private void putMasked(String ck, Masked e) {
        synchronized (masked) {
            Masked old = masked.put(ck, e);
            maskedBytes += e.bytes() - (old == null ? 0 : old.bytes());
            variants.computeIfAbsent(e.variantKey(), k -> new HashSet<>()).add(ck);
            Iterator<Map.Entry<String, Masked>> it = masked.entrySet().iterator();
            while (maskedBytes > MASKED_CACHE_BYTES && it.hasNext()) {
                Map.Entry<String, Masked> x = it.next();
                if (x.getKey().equals(ck)) {
                    continue;
                }
                maskedBytes -= x.getValue().bytes();
                it.remove();
                Set<String> vs = variants.get(x.getValue().variantKey());
                if (vs != null) {
                    vs.remove(x.getKey());
                    if (vs.isEmpty()) {
                        variants.remove(x.getValue().variantKey());
                    }
                }
                removePayload(x.getKey());
            }
        }
    }

    /** Rebuilds every cached mask of a tile whose content changed (worker). */
    private void refreshVariants(String dim, long key) {
        List<Masked> todo = new java.util.ArrayList<>();
        List<String> cks = new java.util.ArrayList<>();
        synchronized (masked) {
            Set<String> vs = variants.get(variantKey(dim, key));
            if (vs == null) {
                return;
            }
            for (String ck : vs) {
                Masked e = masked.get(ck);
                if (e != null) {
                    todo.add(e);
                    cks.add(ck);
                }
            }
        }
        for (int i = 0; i < todo.size(); i++) {
            enqueue(dim, TileKey.lod(key), TileKey.tx(key), TileKey.tz(key), todo.get(i).mask(), cks.get(i));
        }
    }

    /** The tile is gone: forget its masked variants and their payloads. */
    private void dropVariants(String dim, long key) {
        synchronized (masked) {
            Set<String> vs = variants.remove(variantKey(dim, key));
            if (vs == null) {
                return;
            }
            for (String ck : vs) {
                Masked e = masked.remove(ck);
                if (e != null) {
                    maskedBytes -= e.bytes();
                }
                removePayload(ck);
            }
        }
    }

    // --- server thread: the two steps ------------------------------------------------------------------------

    /** The 64 masks of a block (null = nothing visible). */
    static TileMask[] masks(Visibility v, int lod, int bx, int bz) {
        TileMask[] out = new TileMask[TILES];
        for (int i = 0; i < TILES; i++) {
            TileMask m = TileMask.of(v, lod, bx * BLOCK + (i & 7), bz * BLOCK + (i >> 3));
            out[i] = m.none() ? null : m;
        }
        return out;
    }

    /**
     * {@code since=<cursor>}: the index of a block. While the dimension's on-disk etags are still being learned (a few
     * seconds after a server start) this answers the warm-up shape instead of failing: {@code cursor} empty (the app
     * keeps the cursor it holds), every visible tile whose etag is not known yet as {@link #WARMING_ETAG}, those tiles
     * listed in {@code pending}, and a {@code retryMs}. Hidden tiles are {@code null} as always. The same shape answers
     * a partly visible tile whose masked etag has not been computed for this viewer's mask yet.
     */
    public JsonObject index(String dim, int lod, int bx, int bz, String since, Visibility v) {
        long t0 = System.nanoTime();
        indexCalls.incrementAndGet();
        DimIndex d = dim(dim);
        if (!d.learned) {
            learn(dim);
            JsonObject warm = warming(d, dim, lod, bx, bz, v);
            serverNanos.addAndGet(System.nanoTime() - t0);
            return warm;
        }
        TileMask[] masks = masks(v, lod, bx, bz);
        long fog = 0xCBF29CE484222325L ^ lod;
        String[] etags = new String[TILES];
        long[] seqs = new long[TILES];
        long pending = 0;
        long cursorSeq = 0;
        for (int i = 0; i < TILES; i++) {
            TileMask m = masks[i];
            fog = (fog ^ (m == null ? 0 : m.hash() ^ (m.all() ? 1 : 2))) * 0x100000001B3L;
            fog ^= fog >>> 29;
            if (m == null) {
                continue;
            }
            int tx = bx * BLOCK + (i & 7);
            int tz = bz * BLOCK + (i >> 3);
            long key = TileKey.tile(lod, tx, tz);
            long[] e = d.etags.get(key);
            if (e == null) {
                continue; // never captured (or evicted): identical to hidden
            }
            if (m.all()) {
                etags[i] = hex(e[0]);
                seqs[i] = e[1];
            } else {
                String ck = cacheKey(dim, key, m);
                Masked me = maskedFor(ck, m);
                if (me == null) {
                    etags[i] = WARMING_ETAG;
                    pending |= 1L << i;
                    enqueue(dim, lod, tx, tz, m, ck);
                    continue;
                }
                if (me.contentEtag() != e[0]) {
                    enqueue(dim, lod, tx, tz, m, ck); // rebuilt in the background; the viewer sees the last copy
                }
                if (me.empty()) {
                    continue; // nothing of it is visible: identical to missing
                }
                etags[i] = hex(me.etag());
                seqs[i] = me.seq();
            }
            cursorSeq = Math.max(cursorSeq, seqs[i]);
        }
        String fogPart = Long.toString(fog & Long.MAX_VALUE, 36);
        boolean fresh = false;
        long sinceSeq = 0;
        String[] parts = since == null ? new String[0] : since.split("\\.", -1);
        if (parts.length == 3 && parts[0].equals(epoch) && parts[2].equals(fogPart)) {
            try {
                sinceSeq = Long.parseLong(parts[1], 36);
                fresh = true;
            } catch (NumberFormatException e) {
                fresh = false;
            }
        }
        long changed = 0;
        JsonArray arr = new JsonArray(TILES);
        for (int i = 0; i < TILES; i++) {
            if (etags[i] == null) {
                arr.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            arr.add(etags[i]);
            if (!fresh || seqs[i] > sinceSeq || (pending & (1L << i)) != 0) {
                changed |= 1L << i;
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("cursor", pending != 0 ? "" : epoch + "." + Long.toString(cursorSeq, 36) + "." + fogPart);
        out.add("etags", arr);
        out.addProperty("changed", hex(changed));
        if (pending != 0) {
            out.addProperty("pending", hex(pending));
            out.addProperty("retryMs", RETRY_MS);
        }
        serverNanos.addAndGet(System.nanoTime() - t0);
        return out;
    }

    /** The index answer while {@code d} is still being learned (see {@link #index}). */
    private JsonObject warming(DimIndex d, String dim, int lod, int bx, int bz, Visibility v) {
        TileMask[] masks = masks(v, lod, bx, bz);
        JsonArray arr = new JsonArray(TILES);
        long changed = 0;
        long pending = 0;
        for (int i = 0; i < TILES; i++) {
            TileMask m = masks[i];
            if (m == null) {
                arr.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            long key = TileKey.tile(lod, bx * BLOCK + (i & 7), bz * BLOCK + (i >> 3));
            long[] e = d.etags.get(key);
            Masked me = e == null || m.all() ? null : maskedFor(cacheKey(dim, key, m), m);
            if (e != null && m.all()) {
                arr.add(hex(e[0])); // written since the start: already known
            } else if (me != null && me.empty()) {
                arr.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            } else if (me != null) {
                arr.add(hex(me.etag()));
            } else {
                arr.add(WARMING_ETAG);
                pending |= 1L << i;
            }
            changed |= 1L << i;
        }
        JsonObject out = new JsonObject();
        out.addProperty("cursor", "");
        out.add("etags", arr);
        out.addProperty("changed", hex(changed));
        out.addProperty("pending", hex(pending));
        out.addProperty("retryMs", RETRY_MS);
        return out;
    }

    /** {@code want=<mask>}: a page of pre-encoded tiles. */
    public JsonObject page(UUID viewer, String dim, int lod, int bx, int bz, long want, Visibility v, long nowMs) {
        long t0 = System.nanoTime();
        pageCalls.incrementAndGet();
        recent.put(viewer, nowMs);
        DimIndex d = dim(dim);
        if (!d.learned) {
            learn(dim);
        }
        long[] window = spend.computeIfAbsent(viewer, k -> new long[] {nowMs, 0L});
        if (nowMs - window[0] >= 60_000L) {
            window[0] = nowMs;
            window[1] = 0;
        }
        boolean overBudget = window[1] >= VIEWER_BYTES_PER_MINUTE;
        TileMask[] masks = masks(v, lod, bx, bz);
        JsonObject tiles = new JsonObject();
        long pending = 0;
        long next = 0;
        long used = 0;
        long limit = PAGE_BYTES - PAGE_RESERVE;
        for (int i = 0; i < TILES; i++) {
            if ((want & (1L << i)) == 0) {
                continue;
            }
            TileMask m = masks[i];
            if (m == null) {
                continue; // hidden: omitted, nothing queued
            }
            int tx = bx * BLOCK + (i & 7);
            int tz = bz * BLOCK + (i >> 3);
            long key = TileKey.tile(lod, tx, tz);
            long[] e = d.etags.get(key);
            if (e == null && d.learned) {
                continue; // missing: omitted, nothing queued (identical to hidden)
            }
            if (overBudget) {
                pending |= 1L << i;
                continue;
            }
            String ck = cacheKey(dim, key, m.all() ? null : m);
            Payload p;
            synchronized (payloads) {
                p = payloads.get(ck);
            }
            if (m.all()) {
                if (p == null || e == null || p.contentEtag != e[0]) {
                    misses.incrementAndGet();
                    pending |= 1L << i;
                    enqueue(dim, lod, tx, tz, null, ck);
                    continue;
                }
            } else {
                Masked me = maskedFor(ck, m);
                if (me != null && e != null && me.contentEtag() != e[0]) {
                    enqueue(dim, lod, tx, tz, m, ck); // background rebuild; the last masked copy is still current
                }
                if (me != null && me.empty()) {
                    continue; // nothing of it is visible: identical to missing
                }
                if (me == null || p == null || p.etag != me.etag()) {
                    misses.incrementAndGet();
                    pending |= 1L << i;
                    enqueue(dim, lod, tx, tz, m, ck);
                    continue;
                }
            }
            long cost = p.b64.length() + (long) ENTRY_OVERHEAD;
            if (cost > limit) {
                oversize.incrementAndGet();
                continue; // can never fit a page: not offered (a terrain tile this large does not occur in practice)
            }
            if (used + cost > limit) {
                next |= 1L << i;
                continue;
            }
            JsonObject t = new JsonObject();
            t.addProperty("e", hex(p.etag));
            t.addProperty("b", p.b64);
            tiles.add(Integer.toString(i), t);
            used += cost;
            tilesServed.incrementAndGet();
        }
        window[1] += used;
        bytesServed.addAndGet(used);
        maxPageBytes = Math.max(maxPageBytes, used);
        JsonObject out = new JsonObject();
        out.add("tiles", tiles);
        out.addProperty("pending", hex(pending));
        out.addProperty("next", hex(next));
        int retry = 0;
        if (pending != 0) {
            retry = overBudget ? (int) Math.max(RETRY_MS, 60_000L - (nowMs - window[0])) : RETRY_MS;
        }
        out.addProperty("retryMs", retry);
        serverNanos.addAndGet(System.nanoTime() - t0);
        return out;
    }

    private void enqueue(String dim, int lod, int tx, int tz, @Nullable TileMask mask, String ck) {
        if (!inflight.add(ck)) {
            return;
        }
        boolean queued = worker.submit(dim, src -> fill(src, dim, lod, tx, tz, mask, ck));
        if (!queued) {
            inflight.remove(ck);
        }
    }

    /** Worker: reads, masks if needed, base64-encodes and caches one tile. */
    void fill(Source src, String dim, int lod, int tx, int tz, @Nullable TileMask mask, String ck) {
        long t0 = System.nanoTime();
        try {
            long key = TileKey.tile(lod, tx, tz);
            DimIndex d = dim(dim);
            ServerTileStore.Encoded e = src.encoded(lod, tx, tz);
            if (e == null) {
                d.etags.remove(key);
                dropVariants(dim, key);
                return;
            }
            long[] cur = d.etags.get(key);
            if (cur == null || cur[0] != e.etag()) {
                d.etags.put(key, new long[] {e.etag(), stamp()});
            }
            if (mask == null) {
                putPayload(ck, new Payload(e.etag(), e.etag(), Base64.getEncoder().encodeToString(e.bytes())));
                return;
            }
            byte[] mb = mask.encodeMasked(TileCodec.decode(e.bytes()));
            boolean empty = mb == null;
            long etag = empty ? 0L : TileCodec.contentHash(mb);
            Masked old = maskedFor(ck, mask);
            long seq = old != null && old.empty() == empty && old.etag() == etag ? old.seq() : stamp();
            putMasked(ck, new Masked(variantKey(dim, key), mask, e.etag(), etag, seq, empty));
            if (empty) {
                removePayload(ck);
                return;
            }
            Payload p;
            synchronized (payloads) {
                p = payloads.get(ck);
            }
            String b64 = p != null && p.etag == etag ? p.b64 : Base64.getEncoder().encodeToString(mb);
            putPayload(ck, new Payload(e.etag(), etag, b64));
        } catch (TileCorruptException | RuntimeException ex) {
            // a corrupt tile is recovered by the store on its next read; nothing is sent meanwhile
        } finally {
            inflight.remove(ck);
            encodeNanos.addAndGet(System.nanoTime() - t0);
        }
    }

    private void putPayload(String ck, Payload p) {
        synchronized (payloads) {
            Payload old = payloads.put(ck, p);
            payloadBytes += p.bytes() - (old == null ? 0 : old.bytes());
            Iterator<Map.Entry<String, Payload>> it = payloads.entrySet().iterator();
            while (payloadBytes > PAYLOAD_CACHE_BYTES && it.hasNext()) {
                Map.Entry<String, Payload> e = it.next();
                if (e.getKey().equals(ck)) {
                    continue;
                }
                payloadBytes -= e.getValue().bytes();
                it.remove();
            }
        }
    }

    private void removePayload(String ck) {
        synchronized (payloads) {
            Payload old = payloads.remove(ck);
            if (old != null) {
                payloadBytes -= old.bytes();
            }
        }
    }

    private static String cacheKey(String dim, long key, @Nullable TileMask mask) {
        return dim + '|' + key + (mask == null ? "" : "|" + Long.toHexString(mask.hash()));
    }

    // --- events -----------------------------------------------------------------------------------------------

    /** Viewers who read tiles in the last {@link #RECENT_MS} (server thread); prunes the rest. */
    public Set<UUID> recentViewers(long nowMs) {
        recent.values().removeIf(t -> nowMs - t > RECENT_MS);
        return Set.copyOf(recent.keySet());
    }

    /** A LOD-0 chunk as one long ({@link #chunkX} / {@link #chunkZ} read it back). */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    public static int chunkZ(long chunkKey) {
        return (int) chunkKey;
    }

    /** Takes the LOD-0 chunks ({@link #chunkKey}) whose pixels changed since the last call, per dimension. */
    public Map<String, long[]> drainChangedChunks() {
        Map<String, long[]> out = new HashMap<>();
        dims.forEach((dim, d) -> {
            if (d.changedChunks.isEmpty()) {
                return;
            }
            Long[] keys = d.changedChunks.toArray(new Long[0]);
            for (Long k : keys) {
                d.changedChunks.remove(k);
            }
            long[] arr = new long[keys.length];
            for (int i = 0; i < keys.length; i++) {
                arr[i] = keys[i];
            }
            out.put(dim, arr);
        });
        return out;
    }

    /** The LOD-0 block extent of a dimension's known tiles {minBx, minBz, maxBx, maxBz}, or null (public map). */
    public int @Nullable [] knownExtent(String dim) {
        DimIndex d = dims.get(dim);
        if (d == null) {
            return null;
        }
        int[] ext = null;
        for (Long key : d.etags.keySet()) {
            if (TileKey.lod(key) != 0) {
                continue;
            }
            int bx = TileKey.tx(key) >> 3;
            int bz = TileKey.tz(key) >> 3;
            if (ext == null) {
                ext = new int[] {bx, bz, bx, bz};
            } else {
                ext[0] = Math.min(ext[0], bx);
                ext[1] = Math.min(ext[1], bz);
                ext[2] = Math.max(ext[2], bx);
                ext[3] = Math.max(ext[3], bz);
            }
        }
        return ext;
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    /** World border: a block coordinate beyond this never holds a tile. */
    public static final long WORLD_LIMIT = 30_000_000L;

    /** True when tile {@code (tx, tz)} at {@code lod} lies inside the world border (no coordinate maths can wrap). */
    public static boolean inWorld(int lod, int tx, int tz) {
        long side = 256L << lod;
        return Math.abs((long) tx) * side <= WORLD_LIMIT + side && Math.abs((long) tz) * side <= WORLD_LIMIT + side;
    }

    /** True when block {@code (bx, bz)} at {@code lod} lies inside the world border. */
    public static boolean blockInWorld(int lod, int bx, int bz) {
        return inWorld(lod, bx * BLOCK, bz * BLOCK) && inWorld(lod, bx * BLOCK + BLOCK - 1, bz * BLOCK + BLOCK - 1);
    }

    public static String hex(long v) {
        String s = Long.toHexString(v);
        return "0000000000000000".substring(s.length()) + s;
    }

    /** Parses a 1-16 digit hex mask; empty for anything else (callers treat it as a bad request). */
    public static java.util.OptionalLong parseMask(@Nullable String s) {
        if (s == null || s.isEmpty() || s.length() > 16) {
            return java.util.OptionalLong.empty();
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return java.util.OptionalLong.empty();
            }
        }
        return java.util.OptionalLong.of(Long.parseUnsignedLong(s, 16));
    }

    public java.util.List<String> perfLines() {
        long calls = indexCalls.get() + pageCalls.get();
        int variantsNow;
        synchronized (masked) {
            variantsNow = masked.size();
        }
        return java.util.List.of(String.format(
                "link tiles: %d index + %d page calls, %.1f us/call on the server thread, %d tiles / %d KiB served,"
                        + " max page %d B, %d misses, %d oversize, %d masked variants, %d identical writes skipped,"
                        + " worker encode %.1f ms, learn %.1f ms",
                indexCalls.get(), pageCalls.get(), calls == 0 ? 0.0 : serverNanos.get() / 1000.0 / calls,
                tilesServed.get(), bytesServed.get() >> 10, maxPageBytes, misses.get(), oversize.get(), variantsNow,
                sameWrites.get(), encodeNanos.get() / 1e6, learnNanos.get() / 1e6));
    }
}
