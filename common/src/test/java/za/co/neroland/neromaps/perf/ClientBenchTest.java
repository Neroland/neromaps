package za.co.neroland.neromaps.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.colour.ColourTable;
import za.co.neroland.neromaps.colour.MapRasterizer;
import za.co.neroland.neromaps.colour.RasterOptions;
import za.co.neroland.neromaps.colour.TileLookup;
import za.co.neroland.neromaps.engine.Clipmap;
import za.co.neroland.neromaps.engine.LongObjMap;
import za.co.neroland.neromaps.engine.MinimapFrame;
import za.co.neroland.neromaps.engine.MinimapLayout;
import za.co.neroland.neromaps.engine.RgbaStaging;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.tile.Fixtures;
import za.co.neroland.neromaps.tile.PerfLog;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;
import za.co.neroland.neromaps.tile.TileLod;

/**
 * Client microbenchmarks (M1, M3, M4, M5) and the allocation gates. Timing is logged, never asserted, so CI
 * cannot flake; allocation is asserted (the per-frame path must stay at zero in NeroMaps code).
 */
class ClientBenchTest {

    private static final int FRAMES = 10_000;
    private static final long FRAME_GATE_BYTES = 16 * 1024;

    private static double median5(Runnable r, int iterations) {
        for (int i = 0; i < iterations * 2; i++) {
            r.run();
        }
        double[] runs = new double[5];
        for (int k = 0; k < 5; k++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                r.run();
            }
            runs[k] = (System.nanoTime() - t0) / (double) iterations;
        }
        java.util.Arrays.sort(runs);
        return runs[2];
    }

    private static ColourTable table() {
        ColourTable t = new ColourTable(id -> 0x808080);
        t.merge(JsonParser.parseString("{\"blocks\":{\"minecraft:grass_block\":{\"rgb\":\"939393\",\"tint\":\"grass\"},"
                + "\"minecraft:oak_leaves\":{\"rgb\":\"8f8f8f\",\"tint\":\"foliage\"}}}").getAsJsonObject());
        return t;
    }

    @Test
    void m1CodecSpeed() {
        Tile[] fixtures = {Fixtures.plains(), Fixtures.ocean(), Fixtures.mountains()};
        String[] names = {"plains", "ocean", "mountains"};
        for (int i = 0; i < fixtures.length; i++) {
            Tile t = fixtures[i];
            byte[] enc = TileCodec.encode(t);
            double e = median5(() -> TileCodec.encode(t), 20);
            double d = median5(() -> {
                try {
                    TileCodec.decode(enc);
                } catch (TileCorruptException ex) {
                    throw new IllegalStateException(ex);
                }
            }, 20);
            PerfLog.line(String.format(java.util.Locale.ROOT, "M1 %s: encode %.2f ms, decode %.2f ms, %d B",
                    names[i], e / 1e6, d / 1e6, enc.length));
        }
    }

    @Test
    void m3RasterAndStagingAllocateNothing() {
        Tile t = Fixtures.plains();
        ColourTable table = table();
        RasterOptions o = new RasterOptions();
        int[] patch = new int[256];
        int[] whole = new int[Tile.AREA];
        ByteBuffer staging = RgbaStaging.allocate(16, 16);
        int[] c = {0};
        Runnable chunk = () -> {
            int k = c[0]++ & 255;
            MapRasterizer.chunk(t, 0, 0, k & 15, k >> 4, TileLookup.NONE, table, o, patch, 0, 16);
        };
        Runnable tile = () -> {
            for (int k = 0; k < 256; k++) {
                MapRasterizer.chunk(t, 0, 0, k & 15, k >> 4, TileLookup.NONE, table, o, whole, (k >> 4) * 16 * 256 + (k & 15) * 16, 256);
            }
        };
        Runnable stage = () -> RgbaStaging.copy(patch, 0, 16, 16, 16, staging);
        double perChunk = median5(chunk, 1000);
        double perTile = median5(tile, 20);
        double perStage = median5(stage, 1000);
        long a0 = AllocationMeter.current();
        for (int i = 0; i < 100; i++) {
            tile.run();
        }
        long tileBytes = AllocationMeter.current() - a0;
        PerfLog.line(String.format(java.util.Locale.ROOT,
                "M3 raster: chunk %.2f us, full tile %.1f us (%.2f ns/column), staging 16x16 %.2f us, alloc 100 tiles %d B",
                perChunk / 1e3, perTile / 1e3, perTile / Tile.AREA, perStage / 1e3, tileBytes));
        if (AllocationMeter.supported()) {
            assertTrue(tileBytes < 4096, "rasterising allocates nothing once warm: " + tileBytes);
        }
    }

    @Test
    void m4FramePathAllocatesNothing() {
        for (boolean round : new boolean[] {true, false}) {
            for (int markers : new int[] {128, 200}) {
                MinimapSettings s = new MinimapSettings();
                MinimapLayout l = new MinimapLayout();
                MinimapFrame f = new MinimapFrame();
                Clipmap clip = new Clipmap(256);
                int[] rev = {1};
                Clipmap.Revisions revs = (x, z) -> rev[0];
                for (int i = 0; i < markers; i++) {
                    double a = i * 2.399963;
                    double d = 6 + i * 0.4;
                    f.addMarker(Math.cos(a) * d, Math.sin(a) * d, 0xFF000000 | (i * 0x3F7A1D & 0xFFFFFF));
                }
                int[] frame = {0};
                Runnable body = () -> {
                    int n = frame[0]++;
                    l.compute(s, 960, 540, n & 1, 2, 3);
                    if ((n & 15) == 0) {
                        clip.retarget(n >> 8, 0, revs, 1);
                    }
                    for (int k = 0; k < 8 && clip.pollNearest() != Long.MIN_VALUE; k++) {
                        // drained
                    }
                    f.update(l, round, round, n * 0.7f, n * 0.05, 3.0, 1f, 256, MinimapSettings.Border.NERO, false);
                };
                for (int i = 0; i < 50_000; i++) {
                    body.run();
                }
                long a0 = AllocationMeter.current();
                for (int i = 0; i < FRAMES; i++) {
                    body.run();
                }
                long bytes = AllocationMeter.current() - a0;
                double ns = median5(body, 1000);
                PerfLog.line(String.format(java.util.Locale.ROOT,
                        "M4 frame path %s rotating=%s, %d markers: %.0f ns/frame, overlay quads %d, %d B over %d frames",
                        round ? "round" : "square", round, markers, ns, f.overlay.quads, bytes, FRAMES));
                if (AllocationMeter.supported()) {
                    assertTrue(bytes <= FRAME_GATE_BYTES, "per-frame path allocated " + bytes + " B");
                }
            }
        }
    }

    @Test
    void m5LodDownsample() {
        Tile a = Fixtures.plains();
        Tile b = Fixtures.ocean();
        Tile c = Fixtures.mountains();
        double ns = median5(() -> TileLod.downsample(a, b, c, a, 1), 20);
        PerfLog.line(String.format(java.util.Locale.ROOT, "M5 LOD downsample: %.2f ms per parent tile", ns / 1e6));
        Tile p = TileLod.downsample(a, b, c, a, 1);
        assertEquals(1, p.lod);
    }

    @Test
    void mapLookupAllocatesNothing() {
        LongObjMap<Tile> m = new LongObjMap<>();
        Tile t = new Tile(0);
        for (int i = 0; i < 100; i++) {
            m.put(TileKey.tile(0, i, -i), t);
        }
        long a0 = AllocationMeter.current();
        int hits = 0;
        for (int i = 0; i < 100_000; i++) {
            if (m.get(TileKey.tile(0, i % 100, -(i % 100))) != null) {
                hits++;
            }
        }
        long bytes = AllocationMeter.current() - a0;
        assertEquals(100_000, hits);
        if (AllocationMeter.supported()) {
            assertTrue(bytes < 4096, "primitive map lookups allocate " + bytes);
        }
    }
}
