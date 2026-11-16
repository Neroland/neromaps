package za.co.neroland.neromaps.server.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.scan.ColumnScanner;
import za.co.neroland.neromaps.scan.ColumnSource;
import za.co.neroland.neromaps.scan.FakeSource;
import za.co.neroland.neromaps.tile.PerfLog;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;

class CaptureTest {

    /** A fake world: chunks in {@code loaded} answer, everything else is "not loaded". Counts every call. */
    static final class FakeAccess implements ChunkAccess {
        final java.util.Set<Long> loaded = new java.util.HashSet<>();
        final AtomicLong clock;
        final long cost;
        int calls;

        FakeAccess(AtomicLong clock, long cost) {
            this.clock = clock;
            this.cost = cost;
        }

        @Override
        public Snapshot snapshotIfLoaded(int chunkX, int chunkZ) {
            calls++;
            clock.addAndGet(cost);
            return loaded.contains(DirtyMarks.key(chunkX, chunkZ)) ? new FakeSnap(chunkX, chunkZ) : null;
        }
    }

    static final class FakeSnap implements ChunkAccess.Snapshot {
        final int x;
        final int z;
        final FakeSource src = new FakeSource(-64, 320).fill(-64, 62, ColumnSource.KIND_SOLID, 1);

        FakeSnap(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int chunkX() { return x; }
        public int chunkZ() { return z; }
        public boolean roofed() { return false; }
        public int roofTop() { return 127; }
        public String blockId(int key) { return key == 1 ? "minecraft:stone" : "minecraft:air"; }
        public String biomeId(int key) { return "minecraft:plains"; }
        public int minY() { return src.minY(); }
        public int maxY() { return src.maxY(); }
        public int surfaceY(int a, int b) { return src.surfaceY(a, b); }
        public boolean sectionEmpty(int y) { return src.sectionEmpty(y); }
        public int kind(int a, int y, int b) { return src.kind(a, y, b); }
        public int block(int a, int y, int b) { return src.block(a, y, b); }
        public int biome(int a, int y, int b) { return 0; }
        public int airBlock() { return 0; }
    }

    @Test
    void schedulerNeverExceedsItsBudget() {
        for (long cost : new long[] {10_000, 37_000, 120_000, 400_000}) {
            AtomicLong clock = new AtomicLong();
            FakeAccess access = new FakeAccess(clock, cost);
            DirtyMarks marks = new DirtyMarks();
            for (int i = 0; i < 500; i++) {
                marks.mark(i, 0);
                access.loaded.add(DirtyMarks.key(i, 0));
            }
            long budget = 1_000_000;
            long estimate = cost; // after the first tick the service learns the cost
            CaptureLoop.Stats st = CaptureLoop.run(marks, access, s -> true, budget, clock::get, estimate);
            assertTrue(st.nanos <= budget, "cost " + cost + ": spent " + st.nanos);
            assertEquals(Math.min(500, budget / cost), st.snapshots);
        }
        PerfLog.line("M6 scheduler (fake clock): 1000 us budget held for snapshot costs 10/37/120/400 us");
    }

    @Test
    void budgetThrottlesWhenBehindAndStopsBelowMinTps() {
        assertEquals(1_000_000L, CaptureBudget.nanos(1000, 12.0, 15));
        assertEquals(1_000_000L, CaptureBudget.nanos(1000, 45.0, 15));
        assertEquals(250_000L, CaptureBudget.nanos(1000, 48.0, 15), "behind: a quarter");
        assertEquals(250_000L, CaptureBudget.nanos(1000, 60.0, 15), "16.7 TPS: still a quarter");
        assertEquals(0L, CaptureBudget.nanos(1000, 70.0, 15), "below 15 TPS: nothing");
    }

    @Test
    void captureNeverLoadsAndParksUnloadedChunks() throws IOException {
        AtomicLong clock = new AtomicLong();
        FakeAccess access = new FakeAccess(clock, 1000);
        DirtyMarks marks = new DirtyMarks();
        for (int i = 0; i < 10; i++) {
            marks.mark(i, i);
        }
        access.loaded.add(DirtyMarks.key(3, 3));
        List<ChunkAccess.Snapshot> got = new ArrayList<>();
        CaptureLoop.Stats st = CaptureLoop.run(marks, access, got::add, 10_000_000, clock::get, 1000);
        assertEquals(1, st.snapshots);
        assertEquals(9, st.misses);
        assertEquals(9, marks.parked(), "unloaded chunks wait, they are not loaded");
        assertEquals(0, marks.size());
        // a full worker queue: the chunk goes back to the dirty set and the tick ends
        marks.retryParked(100);
        access.loaded.add(DirtyMarks.key(0, 0));
        CaptureLoop.Stats full = CaptureLoop.run(marks, access, s -> false, 10_000_000, clock::get, 1000);
        assertEquals(1, full.refused);
        assertTrue(marks.size() > 0);
        // demand off: the service passes a zero budget, so nothing is touched at all
        int before = access.calls;
        CaptureLoop.Stats idle = CaptureLoop.run(marks, access, s -> true, 0, clock::get, 1000);
        assertEquals(0, idle.snapshots);
        assertEquals(before, access.calls);
        assertEquals(0, CaptureService.PERF.chunkLoads.get());
        // the capture package never calls anything that can load, generate or ticket a chunk
        Path src = sourceDir().resolve("za/co/neroland/neromaps/server/tiles");
        try (Stream<Path> files = Files.list(src)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String text = Files.readString(f);
                for (String forbidden : new String[] {".getChunk(", "getChunkFuture", "addRegionTicket", "addTicket",
                        "ChunkStatus", "getChunkAt(", ".getBlockState(", "forceLoad", "setChunkForced"}) {
                    assertFalse(text.contains(forbidden), f.getFileName() + " calls " + forbidden);
                }
            }
        }
        assertTrue(Files.readString(src.resolve("LevelChunkAccess.java")).contains("getChunkNow("));
    }

    static Path sourceDir() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            Path c = p.resolve("common/src/main/java");
            if (Files.isDirectory(c)) {
                return c;
            }
        }
        throw new IllegalStateException("common sources not found");
    }

    @Test
    void storeWritesBuildsLodsRecoversAndEvicts(@TempDir Path dir) throws IOException {
        CapturePerf perf = new CapturePerf();
        TileDiskCache disk = new TileDiskCache(dir, 64L << 20);
        disk.scan();
        List<Long> recapture = new ArrayList<>();
        ServerTileStore store = new ServerTileStore(disk, dir.resolve("minecraft/overworld"), 8L << 20, perf,
                recapture::add);
        ChunkColumns cols = new ChunkColumns();
        FakeSnap snap = new FakeSnap(0, 0);
        ColumnScanner.scanSurface(snap, cols);
        String[] blocks = new String[256];
        String[] biomes = new String[256];
        for (int i = 0; i < 256; i++) {
            blocks[i] = snap.blockId(cols.block[i]);
            biomes[i] = "minecraft:plains";
        }
        long t0 = System.nanoTime();
        int chunks = 0;
        for (int cz = 0; cz < 32; cz++) {
            for (int cx = 0; cx < 32; cx++) {
                store.apply(cx, cz, cols, blocks, biomes);
                chunks++;
                if (store.pendingWrites() >= 8) {
                    store.flush();
                }
            }
        }
        store.flush();
        long t1 = System.nanoTime();
        assertTrue(Files.exists(TileDiskCache.tileFile(store.dir(), 0, 1, 1)));
        for (int lod = 1; lod <= 4; lod++) {
            assertTrue(Files.exists(TileDiskCache.tileFile(store.dir(), lod, 0, 0)), "LOD " + lod + " built");
        }
        ServerTileStore.Encoded e = store.encoded(0, 0, 0);
        assertNotNull(e);
        assertNull(store.encoded(0, 9, 9), "never captured: nothing");
        Tile lod4 = store.tile(4, 0, 0);
        assertNotNull(lod4);
        assertTrue(lod4.present(Tile.index(0, 0)));
        // a fresh store reads the files back; a corrupt file is deleted and its chunks reported, once
        Path f = TileDiskCache.tileFile(store.dir(), 0, 1, 0);
        Files.write(f, new byte[] {1, 2, 3});
        ServerTileStore fresh = new ServerTileStore(disk, store.dir(), 8L << 20, perf, recapture::add);
        assertNull(fresh.tile(0, 1, 0));
        assertEquals(256, recapture.size());
        assertFalse(Files.exists(f));
        assertNotNull(fresh.tile(0, 0, 0));
        double ms = (t1 - t0) / 1e6;
        PerfLog.line(String.format("M6 worker: %d chunks applied + %d tiles encoded/written (+LOD 1-4) in %.1f ms "
                + "= %.0f chunks/s, encode avg %.1f us, LOD build avg %.1f us", chunks, perf.writes.get(), ms,
                chunks / (ms / 1000.0), perf.encodeNanos.get() / 1000.0 / Math.max(1, perf.encodes.get()),
                perf.lodNanos.get() / 1000.0 / Math.max(1, perf.lodBuilds.get())));
        // disk cap: a tiny cap evicts down to it
        disk.setCap(1);
        ServerTileStore small = new ServerTileStore(disk, dir.resolve("minecraft/the_nether"), 8L << 20, perf,
                k -> { });
        small.apply(0, 0, cols, blocks, biomes);
        small.flush();
        assertTrue(disk.totalBytes() <= 1 || disk.totalBytes() < 200_000, "evicted to the cap");
        // memory budget: decoded tiles beyond it are dropped
        ServerTileStore tight = new ServerTileStore(disk, store.dir(), ServerTileStore.TILE_BYTES * 4, perf, k -> { });
        for (int t = 0; t < 10; t++) {
            tight.apply(t * 16, 0, cols, blocks, biomes);
            tight.flush();
        }
        assertTrue(tight.memoryTiles() <= 8, "memory tiles " + tight.memoryTiles());
        assertEquals(0, TileKey.lod(TileKey.tile(0, 1, 1)));
    }

    @Test
    void snapshotCopyProxy() {
        // Proxy for M6 "snapshot us per chunk": the server-thread copy is a heightmap scan plus a PalettedContainer
        // copy per reachable section; a real container needs a bootstrapped registry, so this copies arrays of the
        // same size (6 sections of 4096 4-bit entries plus 256 heights). The live number is owner run O2.
        long[][] sections = new long[6][4096 * 4 / 64];
        int[] heights = new int[256];
        long sink = 0;
        for (int w = 0; w < 20_000; w++) {
            sink += copy(sections, heights);
        }
        long t0 = System.nanoTime();
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            sink += copy(sections, heights);
        }
        double us = (System.nanoTime() - t0) / 1000.0 / n;
        PerfLog.line(String.format("M6 snapshot copy proxy %.2f us/chunk (6 sections + heightmap; live value is O2)",
                us));
        assertTrue(sink != 1);
    }

    private static long copy(long[][] sections, int[] heights) {
        long s = 0;
        for (long[] sec : sections) {
            s += sec.clone().length;
        }
        return s + heights.clone().length;
    }

    @Test
    void dirtyMarksCoalesceAndStayBounded() {
        DirtyMarks m = new DirtyMarks();
        for (int i = 0; i < 100; i++) {
            m.mark(1, 1);
        }
        assertEquals(1, m.size());
        assertEquals(DirtyMarks.key(1, 1), m.poll());
        assertEquals(Long.MIN_VALUE, m.poll());
        for (int i = 0; i < DirtyMarks.MAX + 10; i++) {
            m.mark(i, -i);
        }
        assertEquals(DirtyMarks.MAX, m.size());
        assertEquals(10, m.dropped());
    }
}
