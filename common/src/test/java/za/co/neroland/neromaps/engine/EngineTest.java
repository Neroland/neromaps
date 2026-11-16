package za.co.neroland.neromaps.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;

class EngineTest {

    @TempDir
    Path tmp;

    @Test
    void longObjMapMatchesHashMap() {
        LongObjMap<Long> m = new LongObjMap<>(4);
        HashMap<Long, Long> ref = new HashMap<>();
        Random r = new Random(9);
        for (int i = 0; i < 20000; i++) {
            long k = r.nextInt(500) - 250L;
            if (r.nextInt(3) == 0) {
                assertEquals(ref.remove(k), m.remove(k));
            } else {
                assertEquals(ref.put(k, (long) i), m.put(k, (long) i));
            }
            assertEquals(ref.size(), m.size());
        }
        for (long k = -260; k < 260; k++) {
            assertEquals(ref.get(k), m.get(k));
        }
    }

    @Test
    void tileKeysRoundTrip() {
        for (int tx : new int[] {-1, 0, 5, -12345, 1 << 20}) {
            long k = TileKey.tile(3, tx, -tx);
            assertEquals(3, TileKey.lod(k));
            assertEquals(tx, TileKey.tx(k));
            assertEquals(-tx, TileKey.tz(k));
            long c = TileKey.chunk(tx, -tx - 1);
            assertEquals(tx, TileKey.chunkX(c));
            assertEquals(-tx - 1, TileKey.chunkZ(c));
        }
    }

    @Test
    void clipmapRefillsNearestFirstAndTracksRevisions() {
        Clipmap c = new Clipmap(256);
        int[] rev = {1};
        Clipmap.Revisions r = (x, z) -> rev[0];
        c.retarget(100, -50, r, 1);
        assertEquals(256, c.dirtyCount());
        long first = c.pollNearest();
        assertEquals(100, TileKey.chunkX(first));
        assertEquals(-50, TileKey.chunkZ(first));
        while (c.pollNearest() != Long.MIN_VALUE) {
            // drain
        }
        c.retarget(100, -50, r, 1);
        assertEquals(0, c.dirtyCount(), "nothing changed, nothing to upload");
        c.retarget(101, -50, r, 1);
        assertEquals(16, c.dirtyCount(), "moving one chunk east refills one column of slots");
        rev[0] = 2;
        c.retarget(101, -50, r, 1);
        assertEquals(256, c.dirtyCount());
        assertEquals(Math.floorMod(101, 16) * 16, c.texelX(101));
    }

    private static ScanResult result(int cx, int cz, String block, int height, boolean cave) {
        ChunkColumns cols = new ChunkColumns();
        ScanResult r = new ScanResult(cx, cz, cols, cave, 1);
        for (int i = 0; i < 256; i++) {
            cols.flags[i] = (byte) Tile.F_PRESENT;
            cols.height[i] = (short) height;
            r.blockIds[i] = block;
            r.biomeIds[i] = "minecraft:plains";
        }
        return r;
    }

    @Test
    void storeAppliesSavesLoadsAndBuildsLods() throws Exception {
        TileDiskCache cache = new TileDiskCache(tmp.resolve("c"), 64L << 20);
        Path dir = cache.dimensionDir("k", "default", "minecraft", "overworld");
        MapStore s = new MapStore(cache, dir, Runnable::run, 16);
        s.apply(result(3, 4, "minecraft:stone", 70, false));
        s.apply(result(20, 4, "minecraft:sand", 64, false));
        Tile t = s.get(0, 0, 0);
        assertNotNull(t);
        assertEquals(70, t.height[Tile.index(3 * 16, 4 * 16)]);
        assertTrue(s.revision(3, 4, false) > 0);
        assertEquals(-1, s.revision(3, 4, true), "no cave slice yet");
        s.close();
        assertTrue(cache.exists(TileDiskCache.tileFile(dir, 0, 0, 0)));

        MapStore s2 = new MapStore(cache, dir, Runnable::run, 16);
        assertNull(s2.request(0, 0, 0), "first request starts the load");
        s2.tick(0, 0);
        Tile loaded = s2.request(0, 0, 0);
        assertNotNull(loaded);
        assertEquals("minecraft:stone", loaded.blockId(loaded.block[Tile.index(3 * 16, 4 * 16)]));
        // LOD 1 tile (0,0) covers LOD-0 tiles (0..1, 0..1)
        assertNull(s2.request(1, 0, 0));
        s2.tick(0, 0);
        Tile lod = s2.request(1, 0, 0);
        assertNotNull(lod);
        assertEquals(1, lod.lod);
        assertTrue(lod.present(Tile.index(3 * 8, 4 * 8)));
        assertTrue(lod.present(Tile.index(128 + 4 * 8, 4 * 8)), "the east child landed in the east half");
        assertTrue(cache.exists(TileDiskCache.tileFile(dir, 1, 0, 0)), "built LODs are cached");
    }

    @Test
    void scansWaitForTheDiskTileAndLiveChunksWin() throws Exception {
        TileDiskCache cache = new TileDiskCache(tmp.resolve("c2"), 64L << 20);
        Path dir = cache.dimensionDir("k", "default", "minecraft", "overworld");
        MapStore s = new MapStore(cache, dir, Runnable::run, 16);
        s.apply(result(0, 0, "minecraft:stone", 70, false));
        s.apply(result(1, 0, "minecraft:stone", 71, false));
        s.close();
        MapStore s2 = new MapStore(cache, dir, Runnable::run, 16);
        s2.apply(result(1, 0, "minecraft:dirt", 90, false)); // queued behind the load
        s2.tick(0, 0);
        Tile t = s2.get(0, 0, 0);
        assertNotNull(t);
        assertEquals(70, t.height[Tile.index(0, 0)], "chunk from disk");
        assertEquals(90, t.height[Tile.index(16, 0)], "the live scan replaced the cached chunk");
    }

    @Test
    void corruptCacheFilesAreDroppedAndCounted() throws Exception {
        TileDiskCache cache = new TileDiskCache(tmp.resolve("c3"), 64L << 20);
        Path dir = cache.dimensionDir("k", "default", "minecraft", "overworld");
        Path f = TileDiskCache.tileFile(dir, 0, 0, 0);
        cache.write(f, new byte[] {1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9});
        long before = PerfCounters.corruptTiles.get();
        MapStore s = new MapStore(cache, dir, Runnable::run, 16);
        s.request(0, 0, 0);
        s.tick(0, 0);
        assertEquals(before + 1, PerfCounters.corruptTiles.get());
        assertFalse(Files.exists(f), "a corrupt tile is deleted for recapture");
    }

    @Test
    void caveSlicesStayInMemory() {
        TileDiskCache cache = new TileDiskCache(tmp.resolve("c4"), 64L << 20);
        Path dir = cache.dimensionDir("k", "default", "minecraft", "overworld");
        MapStore s = new MapStore(cache, dir, Runnable::run, 16);
        s.apply(result(0, 0, "minecraft:stone", 12, true));
        assertNotNull(s.cave(0, 0));
        assertNull(s.get(0, 0, 0));
        s.close();
        assertFalse(cache.exists(TileDiskCache.tileFile(dir, 0, 0, 0)), "cave mode is never written to disk");
    }

    @Test
    void memoryBudgetEvictsAndSaves() {
        TileDiskCache cache = new TileDiskCache(tmp.resolve("c5"), 256L << 20);
        Path dir = cache.dimensionDir("k", "default", "minecraft", "overworld");
        MapStore s = new MapStore(cache, dir, Runnable::run, 16);
        for (int i = 0; i < 20; i++) {
            s.apply(result(i * 16 + 40, 0, "minecraft:stone", 64, false));
            s.tick(0, 0);
        }
        assertTrue(s.tileCount() <= 17);
        assertTrue(cache.exists(TileDiskCache.tileFile(dir, 0, 3, 0)), "an evicted unsaved tile was saved");
    }

    @Test
    void layoutAnchorsAndSafeArea() {
        MinimapSettings s = new MinimapSettings();
        MinimapLayout l = new MinimapLayout();
        l.compute(s, 480, 270, 0, 2, 3);
        assertEquals(480 - 4 - 3 - 96, l.left);
        assertEquals(4 + 3, l.top);
        l.compute(s, 480, 270, 2, 2, 3);
        assertEquals(4 + 3 + 52, l.top, "two effect rows push the map down");
        s.anchor = MinimapSettings.Anchor.BOTTOM_LEFT;
        l.compute(s, 480, 270, 2, 2, 3);
        assertEquals(7, l.left);
        assertTrue(l.readoutY + 2 * MinimapLayout.READOUT_LINE <= 270 - 4 + 2);
        s.anchor = MinimapSettings.Anchor.FREE;
        s.freeX = 5f;
        s.freeY = -3f;
        s.clamp();
        l.compute(s, 480, 270, 0, 0, 3);
        assertTrue(l.left - 3 >= 4 && l.right() + 3 <= 480 - 4, "free position stays inside the margin");
    }

    @Test
    void settingsRoundTripAndClamp() throws Exception {
        MinimapSettings s = new MinimapSettings();
        s.shape = MinimapSettings.Shape.ROUND;
        s.zoom = 4f;
        s.showLight = true;
        s.style = za.co.neroland.neromaps.colour.MapStyle.HEIGHT;
        Path f = tmp.resolve("config").resolve("neromaps-client.properties");
        s.save(f);
        MinimapSettings b = new MinimapSettings();
        b.load(f);
        assertEquals(MinimapSettings.Shape.ROUND, b.shape);
        assertEquals(4f, b.zoom);
        assertTrue(b.showLight);
        assertEquals(za.co.neroland.neromaps.colour.MapStyle.HEIGHT, b.style);
        b.size = 9999;
        b.uploadBudget = 99;
        b.zoomStep(true);
        assertEquals(MinimapSettings.MAX_SIZE, b.size);
        assertEquals(16, b.uploadBudget, "decision 004 B2 cap");
        Files.writeString(f, "size=abc\nshape=triangle\n");
        MinimapSettings c = new MinimapSettings();
        c.load(f);
        assertEquals(96, c.size);
        assertEquals(MinimapSettings.Shape.SQUARE, c.shape);
    }
}
