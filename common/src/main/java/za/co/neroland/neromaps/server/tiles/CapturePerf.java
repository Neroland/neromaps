package za.co.neroland.neromaps.server.tiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server capture counters for {@code /neromaps perf} and the M6 benchmarks. Global numbers only: no
 * player, position, dimension name or tile coordinate ever appears here.
 */
public final class CapturePerf {

    public final AtomicLong ticks = new AtomicLong();
    public final AtomicLong idleTicks = new AtomicLong();
    public final AtomicLong tickNanos = new AtomicLong();
    public volatile long worstTickNanos;
    public final AtomicLong snapshots = new AtomicLong();
    public final AtomicLong misses = new AtomicLong();
    public final AtomicLong refused = new AtomicLong();
    public final AtomicLong marks = new AtomicLong();
    public final AtomicLong scans = new AtomicLong();
    public final AtomicLong scanNanos = new AtomicLong();
    public final AtomicLong encodes = new AtomicLong();
    public final AtomicLong encodeNanos = new AtomicLong();
    public final AtomicLong writes = new AtomicLong();
    public final AtomicLong writeErrors = new AtomicLong();
    public final AtomicLong reads = new AtomicLong();
    public final AtomicLong corrupt = new AtomicLong();
    public final AtomicLong lodBuilds = new AtomicLong();
    public final AtomicLong lodNanos = new AtomicLong();
    public final AtomicLong memoryEvictions = new AtomicLong();
    public final AtomicLong tileRequests = new AtomicLong();
    public final AtomicLong tilesSent = new AtomicLong();
    public final AtomicLong tilesHidden = new AtomicLong();
    public final AtomicLong maskNanos = new AtomicLong();
    public final AtomicLong masks = new AtomicLong();
    /** Calls that could load a chunk. The capture path has none; this stays 0 and the tests assert it. */
    public final AtomicLong chunkLoads = new AtomicLong();
    public volatile int queueDepth;
    public volatile long diskBytes;
    public volatile boolean idle = true;

    public void reset() {
        for (AtomicLong a : new AtomicLong[] {ticks, idleTicks, tickNanos, snapshots, misses, refused, marks, scans,
                scanNanos, encodes, encodeNanos, writes, writeErrors, reads, corrupt, lodBuilds, lodNanos,
                memoryEvictions, tileRequests, tilesSent, tilesHidden, maskNanos, masks}) {
            a.set(0);
        }
        worstTickNanos = 0;
    }

    private static String avg(AtomicLong nanos, AtomicLong n) {
        long c = n.get();
        return c == 0 ? "-" : String.format("%.1f us", nanos.get() / 1000.0 / c);
    }

    public List<String> lines() {
        List<String> l = new ArrayList<>();
        long t = ticks.get();
        l.add("capture " + (idle ? "idle" : "active") + ", ticks " + t + " (idle " + idleTicks.get() + ")");
        l.add("capture tick avg " + avg(tickNanos, ticks) + ", worst " + String.format("%.1f us", worstTickNanos / 1000.0));
        l.add("dirty marks " + marks.get() + ", snapshots " + snapshots.get() + ", unloaded skips " + misses.get()
                + ", queue full " + refused.get() + ", chunk loads " + chunkLoads.get());
        l.add("worker queue " + queueDepth + ", scans " + scans.get() + " (" + avg(scanNanos, scans) + ")");
        l.add("encodes " + encodes.get() + " (" + avg(encodeNanos, encodes) + "), writes " + writes.get()
                + ", write errors " + writeErrors.get());
        l.add("LOD builds " + lodBuilds.get() + " (" + avg(lodNanos, lodBuilds) + "), reads " + reads.get()
                + ", corrupt dropped " + corrupt.get() + ", memory evictions " + memoryEvictions.get());
        l.add("tile requests " + tileRequests.get() + ", sent " + tilesSent.get() + ", hidden " + tilesHidden.get()
                + ", masks " + masks.get() + " (" + avg(maskNanos, masks) + ")");
        l.add(String.format("tile disk %.1f MB", diskBytes / 1048576.0));
        return l;
    }
}
