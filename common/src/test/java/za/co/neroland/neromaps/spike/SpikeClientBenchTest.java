package za.co.neroland.neromaps.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * SPIKE (Stage 0 minimap draw path). CPU microbenchmarks and the zero-allocation check for the minimap's per-frame
 * path, with no Minecraft runtime. Numbers go to {@code build/neromaps-spike/bench.txt} of the node that ran them.
 * Timing assertions are deliberately loose (CI noise); the allocation assertion is the real gate.
 */
class SpikeClientBenchTest {

    private static final StringBuilder REPORT = new StringBuilder();

    private static void record(String line) {
        REPORT.append(line).append('\n');
    }

    @AfterAll
    static void write() throws IOException {
        Path out = Path.of("build", "neromaps-spike", "bench.txt");
        Files.createDirectories(out.getParent());
        String header = "java " + System.getProperty("java.version") + " / " + System.getProperty("os.name") + " / "
                + Runtime.getRuntime().availableProcessors() + " cpus\n";
        Files.writeString(out, header + REPORT, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static long allocated() {
        return THREADS.getCurrentThreadAllocatedBytes();
    }

    /** Bytes the measurement itself costs (two reads with nothing between), subtracted from every result. */
    private static long overhead() {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long a = allocated();
            min = Math.min(min, allocated() - a);
        }
        return min;
    }

    @Test
    void columnFillOfOneTile() {
        SpikeColumns cols = SpikeColumns.fake(7);
        int[] palette = SpikeTileFill.fakePalette();
        int[] out = new int[SpikeColumns.SIZE * SpikeColumns.SIZE];
        for (int i = 0; i < 200; i++) {
            SpikeTileFill.fill(cols, palette, out);
        }
        int runs = 500;
        long t0 = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            SpikeTileFill.fill(cols, palette, out);
        }
        double usPerTile = (System.nanoTime() - t0) / 1000.0 / runs;
        long a0 = allocated();
        SpikeTileFill.fill(cols, palette, out);
        long bytes = allocated() - a0 - overhead();
        // one dirty chunk (16 x 16)
        long t1 = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            SpikeTileFill.fillRect(cols, palette, out, (i & 15) * 16, ((i >> 4) & 15) * 16, 16, 16);
        }
        double nsPerChunk = (System.nanoTime() - t1) / 100_000.0;
        record(String.format("fill 256x256 tile: %.1f us/tile (%.2f ns/column), %d bytes allocated per tile", usPerTile,
                usPerTile * 1000 / 65536, bytes));
        record(String.format("fill one 16x16 chunk: %.0f ns", nsPerChunk));
        assertTrue((out[0] >>> 24) == 0xFF, "opaque output");
        assertTrue(bytes < 1024, "fill must not allocate: " + bytes);
        assertTrue(usPerTile < 20_000, "fill is absurdly slow: " + usPerTile + " us");
    }

    @Test
    void stagingCopyMatchesRgbaLayout() {
        int[] argb = new int[256 * 256];
        argb[17 * 256 + 33] = 0x80112233;
        ByteBuffer dst = SpikeStaging.allocate(16, 16);
        SpikeStaging.copyRect(argb, 256, 32, 16, 16, 16, dst);
        assertEquals(16 * 16 * 4, dst.remaining());
        int off = ((17 - 16) * 16 + (33 - 32)) * 4;
        assertEquals(0x11, dst.get(off) & 0xFF, "R");
        assertEquals(0x22, dst.get(off + 1) & 0xFF, "G");
        assertEquals(0x33, dst.get(off + 2) & 0xFF, "B");
        assertEquals(0x80, dst.get(off + 3) & 0xFF, "A");
        for (int i = 0; i < 20_000; i++) {
            SpikeStaging.copyRect(argb, 256, (i & 15) * 16, ((i >> 4) & 15) * 16, 16, 16, dst);
        }
        long a0 = allocated();
        long t0 = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            SpikeStaging.copyRect(argb, 256, (i & 15) * 16, ((i >> 4) & 15) * 16, 16, 16, dst);
        }
        long ns = System.nanoTime() - t0;
        long bytes = allocated() - a0 - overhead();
        record(String.format("staging copy 16x16 chunk -> RGBA8 direct buffer: %.0f ns, %d bytes allocated over 100k",
                ns / 100_000.0, bytes));
        assertTrue(bytes < 4096, "staging copy allocates: " + bytes);
    }

    @Test
    void dirtyChunksCoalesce() {
        SpikeDirtyChunks d = new SpikeDirtyChunks();
        d.mark(3, 4);
        d.mark(3, 4);
        d.mark(15, 15);
        assertEquals(2, d.count());
        assertEquals(4 * 16 + 3, d.poll());
        assertEquals(255, d.poll());
        assertEquals(-1, d.poll());
        d.markAll();
        assertEquals(256, d.count());
    }

    @Test
    void markerBatchOf200() {
        SpikeFrame f = fakeFrame();
        for (int i = 0; i < 20_000; i++) {
            f.markers.build(1000 + i * 0.01, -300, 0.3f, 0.95f, true, 64, 64, 64, 1f, 3f);
        }
        int runs = 200_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            f.markers.build(1000 + (i & 1023) * 0.01, -300 - (i & 1023) * 0.01, (float) Math.sin(i * 1e-3), (float) Math.cos(i * 1e-3), true, 64,
                    64, 64, 1f, 3f);
        }
        double ns = (System.nanoTime() - t0) / (double) runs;
        record(String.format("marker batch, 200 markers (project + rotate + circle cull + quad write): %.0f ns/frame,"
                + " %d visible", ns, f.markers.quads));
        assertTrue(f.markers.quads > 0 && f.markers.quads <= 200);
        assertTrue(ns < 1_000_000, "marker batch absurdly slow");
    }

    @Test
    void geometry() {
        SpikeMinimapGeometry g = new SpikeMinimapGeometry();
        g.build(false, 100, 100, 64, 0.5f, 0.5f, 1f / 256, 0f, 1f);
        assertEquals(1, g.quads);
        assertEquals(36f, g.xy[0], 1e-4);
        assertEquals(0.5f - 0.25f, g.uv[0], 1e-6);
        g.build(true, 100, 100, 64, 0.5f, 0.5f, 1f / 256, 0f, 1f);
        assertEquals(SpikeMinimapGeometry.SEGMENTS, g.quads);
        for (int v = 0; v < g.quads * 4; v++) {
            float dx = g.xy[v * 2] - 100;
            float dy = g.xy[v * 2 + 1] - 100;
            assertTrue(dx * dx + dy * dy <= 64 * 64 + 1e-2, "round vertex outside the circle");
        }
    }

    /** The whole per-frame CPU path, 10 000 frames, must allocate ~0 bytes per frame. */
    @Test
    void perFrameCpuPathAllocatesNothing() {
        SpikeFrame f = fakeFrame();
        f.round = true;
        f.rotate = true;
        // warm up past C2 compilation
        for (int i = 0; i < 50_000; i++) {
            f.update(1000 + (i & 1023) * 0.01, -300 - (i & 1023) * 0.01, i * 0.7f, 1920 / 3);
        }
        int frames = 10_000;
        long a0 = allocated();
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            f.update(1000 + (i & 1023) * 0.01, -300 - (i & 1023) * 0.01, i * 0.7f, 1920 / 3);
        }
        long ns = System.nanoTime() - t0;
        long bytes = allocated() - a0 - overhead();
        f.round = false;
        f.rotate = false;
        long t1 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            f.update(1000 + (i & 1023) * 0.01, -300 - (i & 1023) * 0.01, i * 0.7f, 1920 / 3);
        }
        long nsSquare = System.nanoTime() - t1;
        record(String.format("per-frame CPU path (round, rotating, 64 wedges + 200 markers): %.0f ns/frame;"
                + " square north-locked: %.0f ns/frame; %d of 200 markers visible", ns / (double) frames,
                nsSquare / (double) frames, f.markers.quads));
        record(String.format("allocation over %d frames: %d bytes total, %.3f bytes/frame (measurement overhead of"
                + " %d bytes subtracted)", frames, bytes, bytes / (double) frames, overhead()));
        assertTrue(bytes <= 16 * 1024, "per-frame path allocates: " + bytes + " bytes over " + frames + " frames");
    }

    private static SpikeFrame fakeFrame() {
        SpikeFrame f = new SpikeFrame();
        for (int i = 0; i < 200; i++) {
            double a = i * 2.399963;
            double d = 6 + i * 0.4;
            f.markers.add(1000 + Math.cos(a) * d, -300 + Math.sin(a) * d, 0xFF000000 | (i * 0x3F7A1D & 0xFFFFFF));
        }
        return f;
    }
}
