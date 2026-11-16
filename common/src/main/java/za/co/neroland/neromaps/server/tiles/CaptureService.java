package za.co.neroland.neromaps.server.tiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.scan.ColumnScanner;

/**
 * Server terrain capture (decision 006). Block changes and newly explored chunks only add a dirty mark (always on,
 * cheap). Capture itself runs only while the {@code serverCapture} policy is on <em>and</em> something needs server
 * tiles: a tile request within {@code captureDemandMinutes}, or a loaded wall map. Then, at the end of each tick, it
 * snapshots dirty chunks that are already loaded until {@code captureBudgetMicros} is spent (a quarter of it while the
 * server is behind, nothing below {@code captureMinTps}); one worker thread scans, encodes, rebuilds the LOD pyramid
 * and writes atomically under {@code <world>/data/neromaps/tiles}. No chunk is ever loaded, generated, ticketed or
 * kept for the map.
 */
public final class CaptureService {

    public static final int QUEUE = 512;
    private static final String DIR = "data/neromaps/tiles";

    public static final CapturePerf PERF = new CapturePerf();

    private static @Nullable MinecraftServer server;
    private static final Map<String, DirtyMarks> MARKS = new HashMap<>();
    private static final ConcurrentLinkedQueue<Object[]> RECAPTURE = new ConcurrentLinkedQueue<>();
    private static volatile double lastMspt;
    private static @Nullable ThreadPoolExecutor worker;
    private static @Nullable ArrayBlockingQueue<Runnable> queue;
    /** Worker-owned. */
    private static final Map<String, ServerTileStore> STORES = new HashMap<>();
    private static @Nullable TileDiskCache disk;
    private static volatile long demandUntil;
    private static volatile int wallMaps;
    private static long estimateNanos = 20_000;
    private static int ticks;
    private static int rotate;

    private CaptureService() {
    }

    // --- marks (server thread, always on) ---------------------------------------------------------------------

    public static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static DirtyMarks marks(String dim) {
        return MARKS.computeIfAbsent(dim, k -> new DirtyMarks());
    }

    /** A block changed (common mixin on {@code ServerLevel.sendBlockUpdated}). Marks the chunk; nothing more. */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread()) {
            return;
        }
        marks(dimension(level)).mark(pos.getX() >> 4, pos.getZ() >> 4);
        PERF.marks.incrementAndGet();
    }

    /** A chunk was explored for the first time by someone: capture it when capture next runs. */
    public static void explored(ServerLevel level, int chunkX, int chunkZ) {
        marks(dimension(level)).mark(chunkX, chunkZ);
        PERF.marks.incrementAndGet();
    }

    // --- demand -----------------------------------------------------------------------------------------------

    /** Something asked for server tiles (a link request, a shared-area request): capture runs for a while. */
    public static void noteDemand() {
        demandUntil = System.nanoTime() + NeroMapsConfig.captureDemandMinutes() * 60_000_000_000L;
    }

    /** Loaded wall maps (maintained by the wall map block entities in a later stage). */
    public static void setWallMaps(int count) {
        wallMaps = Math.max(0, count);
    }

    public static boolean demanded() {
        return wallMaps > 0 || System.nanoTime() - demandUntil < 0;
    }

    // --- tick -------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer s) {
        if (s != server) {
            stop();
            server = s;
        }
        ticks++;
        Object[] rc;
        while ((rc = RECAPTURE.poll()) != null) {
            marks((String) rc[0]).mark(DirtyMarks.x((Long) rc[1]), DirtyMarks.z((Long) rc[1]));
        }
        PERF.ticks.incrementAndGet();
        if (!NeroMapsConfig.serverCapture() || !demanded()) {
            PERF.idle = true;
            PERF.idleTicks.incrementAndGet();
            return;
        }
        PERF.idle = false;
        ensureWorker(s);
        double mspt = s.getAverageTickTimeNanos() / 1_000_000.0;
        lastMspt = mspt;
        long budget = CaptureBudget.nanos(NeroMapsConfig.captureBudgetMicros(), mspt, NeroMapsConfig.captureMinTps());
        if (ticks % 200 == 0) {
            for (DirtyMarks m : MARKS.values()) {
                m.retryParked(4096);
            }
        }
        long t0 = System.nanoTime();
        List<ServerLevel> levels = new ArrayList<>();
        s.getAllLevels().forEach(levels::add);
        int n = levels.size();
        for (int i = 0; i < n && budget > 0; i++) {
            ServerLevel level = levels.get((rotate + i) % n);
            String dim = dimension(level);
            DirtyMarks m = MARKS.get(dim);
            if (m == null || m.size() == 0) {
                continue;
            }
            long spent = System.nanoTime() - t0;
            CaptureLoop.Stats st = CaptureLoop.run(m, new LevelChunkAccess(level), snap -> submit(dim, snap),
                    budget - spent, System::nanoTime, estimateNanos);
            PERF.snapshots.addAndGet(st.snapshots);
            PERF.misses.addAndGet(st.misses);
            PERF.refused.addAndGet(st.refused);
            if (st.snapshots > 0) {
                estimateNanos = Math.max(1_000, st.worstSnapshotNanos);
            }
            if (st.refused > 0) {
                break;
            }
        }
        rotate++;
        long dt = System.nanoTime() - t0;
        PERF.tickNanos.addAndGet(dt);
        if (dt > PERF.worstTickNanos) {
            PERF.worstTickNanos = dt;
        }
        ArrayBlockingQueue<Runnable> q = queue;
        PERF.queueDepth = q == null ? 0 : q.size();
    }

    // --- worker -----------------------------------------------------------------------------------------------

    private static synchronized void ensureWorker(MinecraftServer s) {
        if (worker != null) {
            return;
        }
        Path root = s.getWorldPath(LevelResource.ROOT).resolve(DIR);
        TileDiskCache d = new TileDiskCache(root, NeroMapsConfig.serverTileDiskMb() * 1048576L);
        disk = d;
        d.onDelete(LinkTiles.LIVE::fileDeleted);
        ArrayBlockingQueue<Runnable> q = new ArrayBlockingQueue<>(QUEUE);
        queue = q;
        ThreadPoolExecutor w = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, q, r -> {
            Thread t = new Thread(r, "NeroMaps capture");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        worker = w;
        w.execute(() -> {
            d.scan();
            PERF.diskBytes = d.totalBytes();
        });
    }

    private static boolean submit(String dim, ChunkAccess.Snapshot snap) {
        ThreadPoolExecutor w = worker;
        if (w == null) {
            return false;
        }
        try {
            w.execute(() -> scan(dim, snap));
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** Runs {@code job} on the worker with the dimension's store; false when the queue is full or not running. */
    static boolean onWorker(String dim, Consumer<ServerTileStore> job) {
        ThreadPoolExecutor w = worker;
        if (w == null) {
            return false;
        }
        try {
            w.execute(() -> job.accept(store(dim)));
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** Starts the worker if needed (tile requests may arrive before capture ever ran). */
    public static void ensureRunning(MinecraftServer s) {
        if (server != s) {
            return;
        }
        ensureWorker(s);
    }

    private static ServerTileStore store(String dim) {
        ServerTileStore st = STORES.get(dim);
        if (st == null) {
            TileDiskCache d = disk;
            int c = dim.indexOf(':');
            String ns = c < 0 ? "minecraft" : dim.substring(0, c);
            String path = c < 0 ? dim : dim.substring(c + 1);
            Path dir = d.root().resolve(TileDiskCache.safe(ns)).resolve(TileDiskCache.safe(path.replace('/', '_')));
            st = new ServerTileStore(d, dir, NeroMapsConfig.serverTileCacheMb() * 1048576L, PERF,
                    key -> RECAPTURE.add(new Object[] {dim, key}));
            st.setListener(LinkTiles.LIVE.listener(dim, dir));
            STORES.put(dim, st);
        }
        return st;
    }

    private static final ThreadLocal<ChunkColumns> COLUMNS = ThreadLocal.withInitial(ChunkColumns::new);

    private static void scan(String dim, ChunkAccess.Snapshot snap) {
        try {
            long t0 = System.nanoTime();
            ChunkColumns cols = COLUMNS.get();
            if (snap.roofed()) {
                ColumnScanner.scanRoofed(snap, snap.roofTop(), cols);
            } else {
                ColumnScanner.scanSurface(snap, cols);
            }
            String[] blocks = new String[256];
            String[] biomes = new String[256];
            for (int i = 0; i < 256; i++) {
                blocks[i] = snap.blockId(cols.block[i]);
                biomes[i] = snap.biomeId(cols.biome[i]);
            }
            ServerTileStore st = store(dim);
            st.apply(snap.chunkX(), snap.chunkZ(), cols, blocks, biomes);
            PERF.scans.incrementAndGet();
            PERF.scanNanos.addAndGet(System.nanoTime() - t0);
            ArrayBlockingQueue<Runnable> q = queue;
            if (st.pendingWrites() >= 8 || q == null || q.isEmpty()) {
                st.flush();
                TileDiskCache d = disk;
                PERF.diskBytes = d == null ? 0 : d.totalBytes();
            }
        } catch (RuntimeException e) {
            PERF.writeErrors.incrementAndGet();
            NeroMapsCommon.LOGGER.debug("[NeroMaps] capture scan failed ({})", e.getClass().getName());
        }
    }

    /** Flushes and stops the worker (server stopping). Waits briefly so pending tiles reach disk. */
    public static synchronized void stop() {
        ThreadPoolExecutor w = worker;
        worker = null;
        queue = null;
        if (w != null) {
            try {
                w.execute(() -> STORES.values().forEach(ServerTileStore::flush));
            } catch (RejectedExecutionException e) {
                // queue full: the tiles in flight are recaptured next time
            }
            w.shutdown();
            try {
                w.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        STORES.clear();
        MARKS.clear();
        RECAPTURE.clear();
        disk = null;
        server = null;
        demandUntil = 0;
        wallMaps = 0;
        lastMspt = 0;
    }

    public static void resetCounters() {
        PERF.reset();
    }

    public static int dirtyCount() {
        int n = 0;
        for (DirtyMarks m : MARKS.values()) {
            n += m.size();
        }
        return n;
    }

    public static List<String> perfLines() {
        List<String> l = new ArrayList<>(PERF.lines());
        l.add(String.format("dirty chunks %d, server tick %.1f ms (tps %.1f), demand %s", dirtyCount(), lastMspt,
                1000.0 / Math.max(50.0, lastMspt), demanded() ? "yes" : "no"));
        return l;
    }
}
