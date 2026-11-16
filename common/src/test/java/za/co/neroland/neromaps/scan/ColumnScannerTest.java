package za.co.neroland.neromaps.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.tile.PerfLog;
import za.co.neroland.neromaps.tile.Tile;

class ColumnScannerTest {

    static final int STONE = 1;
    static final int WATER = 2;
    static final int SAND = 3;
    static final int GLASS = 4;
    static final int NETHERRACK = 5;
    static final int LAVA = 6;
    static final int STAIRS = 7;

    @Test
    void surfacePassesThroughInvisibleBlocks() {
        FakeSource s = new FakeSource(-64, 320).fill(-64, 70, ColumnSource.KIND_SOLID, STONE)
                .fill(71, 71, ColumnSource.KIND_INVISIBLE, GLASS);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanSurface(s, out);
        assertEquals(70, out.height[0]);
        assertEquals(STONE, out.block[0]);
        assertEquals(Tile.F_PRESENT, out.flags[0]);
        assertEquals(7, out.biome[0]);
    }

    @Test
    void oceanRecordsDepthAndFloor() {
        FakeSource s = new FakeSource(-64, 320).fill(-64, 20, ColumnSource.KIND_SOLID, STONE)
                .fill(21, 21, ColumnSource.KIND_SOLID, SAND).fill(22, 61, ColumnSource.KIND_WATER, WATER);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanSurface(s, out);
        assertEquals(61, out.height[5]);
        assertEquals(SAND, out.block[5], "the floor block under the water");
        assertEquals(40, out.depth[5] & 0xFF);
        assertTrue((out.flags[5] & Tile.F_WATER) != 0);
    }

    @Test
    void waterloggedTopIsTheSurface() {
        FakeSource s = new FakeSource(0, 128).fill(0, 63, ColumnSource.KIND_SOLID, STONE)
                .fill(64, 64, ColumnSource.KIND_WATERLOGGED, STAIRS);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanSurface(s, out);
        assertEquals(64, out.height[0]);
        assertEquals(STAIRS, out.block[0]);
        assertTrue((out.flags[0] & Tile.F_WATERLOGGED_TOP) != 0);
    }

    @Test
    void depthIsCappedAt255() {
        FakeSource s = new FakeSource(-64, 320).fill(-64, -60, ColumnSource.KIND_SOLID, STONE)
                .fill(-59, 300, ColumnSource.KIND_WATER, WATER);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanSurface(s, out);
        assertEquals(255, out.depth[0] & 0xFF);
    }

    @Test
    void roofedScanSkipsTheCeiling() {
        FakeSource s = new FakeSource(0, 256).fill(0, 30, ColumnSource.KIND_SOLID, NETHERRACK)
                .fill(31, 31, ColumnSource.KIND_LAVA, LAVA).fill(100, 127, ColumnSource.KIND_SOLID, NETHERRACK);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanRoofed(s, 127, out);
        assertEquals(31, out.height[0]);
        assertTrue((out.flags[0] & Tile.F_LAVA) != 0);
        assertTrue((out.flags[0] & Tile.F_CEILING) != 0);
    }

    @Test
    void caveSliceFindsTheFloorBelowThePlayer() {
        FakeSource s = new FakeSource(-64, 320).fill(-64, 40, ColumnSource.KIND_SOLID, STONE)
                .fill(45, 90, ColumnSource.KIND_SOLID, STONE);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanCave(s, 43, out); // air 41..44
        assertEquals(40, out.height[0]);
        ColumnScanner.scanCave(s, 50, out); // inside rock: walks down to the cave
        assertEquals(40, out.height[0]);
        ColumnScanner.scanCave(s, 80, out); // solid rock for more than 16 blocks: the rock at the slice
        assertEquals(80, out.height[0]);
    }

    @Test
    void voidColumnsArePresentAndEmpty() {
        FakeSource s = new FakeSource(0, 256);
        ChunkColumns out = new ChunkColumns();
        ColumnScanner.scanSurface(s, out);
        assertEquals(Tile.F_PRESENT, out.flags[0]);
        assertEquals(0, out.block[0]);
    }

    /** M2: scan cost per chunk on synthetic sections (relative numbers only; no assertion on time). */
    @Test
    void benchmarkM2() {
        FakeSource normal = new FakeSource(-64, 320).fill(-64, 70, ColumnSource.KIND_SOLID, STONE);
        FakeSource ocean = new FakeSource(-64, 320).fill(-64, 21, ColumnSource.KIND_SOLID, SAND)
                .fill(22, 61, ColumnSource.KIND_WATER, WATER);
        FakeSource nether = new FakeSource(0, 256).fill(0, 30, ColumnSource.KIND_SOLID, NETHERRACK)
                .fill(100, 127, ColumnSource.KIND_SOLID, NETHERRACK);
        ChunkColumns out = new ChunkColumns();
        PerfLog.line(String.format(java.util.Locale.ROOT, "M2 scan ns/chunk: normal %.0f, ocean-40 %.0f, nether roof %.0f",
                bench(() -> ColumnScanner.scanSurface(normal, out)), bench(() -> ColumnScanner.scanSurface(ocean, out)),
                bench(() -> ColumnScanner.scanRoofed(nether, 127, out))));
    }

    static double bench(Runnable r) {
        for (int i = 0; i < 2000; i++) {
            r.run();
        }
        double best = Double.MAX_VALUE;
        for (int k = 0; k < 5; k++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                r.run();
            }
            best = Math.min(best, (System.nanoTime() - t0) / 1000.0);
        }
        return best;
    }
}
