package za.co.neroland.neromaps.perf;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global NeroMaps performance counters behind {@code /neromaps perf} (decision 004). Numbers only: no player,
 * position or name ever appears here. Render-thread fields are plain; worker-written ones are atomic.
 */
public final class PerfCounters {

    /** Exponential average of the minimap HUD body's CPU time, nanoseconds. */
    public static double hudNanos;
    /** Bytes allocated inside the HUD body during the last measured frame (must be 0 in steady state). */
    public static long hudBytesLastFrame;
    /** Largest HUD-body allocation seen since the last reset. */
    public static long hudBytesMax;
    /** Chunk patches uploaded in the last frame, and the most in any one frame since reset. */
    public static int uploadsLastFrame;
    public static int uploadsMax;
    /** Upload batches in the last frame (must never exceed 1 for the minimap). */
    public static int batchesLastFrame;
    public static long frames;
    /** Bytes held by NeroMaps textures (GPU plus any CPU-side copy, decision 005). */
    public static long textureBytes;
    public static int tilesInMemory;
    public static int dirtyChunks;

    public static final AtomicInteger scanQueue = new AtomicInteger();
    public static final AtomicLong scans = new AtomicLong();
    public static final AtomicLong scanNanos = new AtomicLong();
    public static final AtomicInteger ioQueue = new AtomicInteger();
    public static final AtomicLong corruptTiles = new AtomicLong();
    public static final AtomicLong cacheBytes = new AtomicLong();
    public static final AtomicLong tilesWritten = new AtomicLong();
    public static final AtomicLong tilesRead = new AtomicLong();

    private PerfCounters() {
    }

    public static void recordHud(long nanos, long bytes) {
        frames++;
        hudNanos = hudNanos == 0 ? nanos : hudNanos * 0.98 + nanos * 0.02;
        hudBytesLastFrame = bytes;
        if (bytes > hudBytesMax) {
            hudBytesMax = bytes;
        }
    }

    public static void recordUploads(int patches, int batches) {
        uploadsLastFrame = patches;
        batchesLastFrame = batches;
        if (patches > uploadsMax) {
            uploadsMax = patches;
        }
    }

    public static void reset() {
        hudNanos = 0;
        hudBytesMax = 0;
        uploadsMax = 0;
        frames = 0;
        scans.set(0);
        scanNanos.set(0);
        corruptTiles.set(0);
        tilesWritten.set(0);
        tilesRead.set(0);
    }
}
