package za.co.neroland.neromaps.server.tiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admission for the in-game tile path ({@link TileService}): a per-viewer byte budget per minute, a per-viewer cap on
 * tiles queued or waiting to be sent, and a cap on tile jobs all viewers together may hold on the capture worker, so
 * one player asking for their own explored tiles in a loop can neither flood the network nor starve capture and the
 * app's tile fills. Memory only, keyed by UUID for the session; never persisted or logged.
 */
public final class TileBudget {

    private final long bytesPerMinute;
    private final int maxInflight;
    private final int maxQueued;
    private final Map<UUID, long[]> spend = new ConcurrentHashMap<>(); // viewer -> {windowStartMs, bytes}
    private final Map<UUID, AtomicInteger> inflight = new ConcurrentHashMap<>();
    private final AtomicInteger queued = new AtomicInteger();

    public TileBudget(long bytesPerMinute, int maxInflight, int maxQueued) {
        this.bytesPerMinute = bytesPerMinute;
        this.maxInflight = maxInflight;
        this.maxQueued = maxQueued;
    }

    private long[] window(UUID viewer, long nowMs) {
        long[] w = spend.computeIfAbsent(viewer, k -> new long[] {nowMs, 0L});
        synchronized (w) {
            if (nowMs - w[0] >= 60_000L) {
                w[0] = nowMs;
                w[1] = 0;
            }
        }
        return w;
    }

    /** True when the viewer has used up this minute's bytes (their requests are dropped until the window turns). */
    public boolean overBudget(UUID viewer, long nowMs) {
        long[] w = window(viewer, nowMs);
        synchronized (w) {
            return w[1] >= bytesPerMinute;
        }
    }

    /** Reserves one tile job for {@code viewer}; false when they or everyone together are at the cap. */
    public boolean tryReserve(UUID viewer) {
        AtomicInteger mine = inflight.computeIfAbsent(viewer, k -> new AtomicInteger());
        if (mine.get() >= maxInflight) {
            return false;
        }
        if (queued.incrementAndGet() > maxQueued) {
            queued.decrementAndGet();
            return false;
        }
        mine.incrementAndGet();
        return true;
    }

    /** The job left the worker queue (it started, or it could not be queued). */
    public void dequeued() {
        queued.decrementAndGet();
    }

    /** The viewer's tile is done: answered and sent, dropped, or nothing to send. */
    public void finished(UUID viewer) {
        AtomicInteger mine = inflight.get(viewer);
        if (mine != null && mine.decrementAndGet() < 0) {
            mine.set(0);
        }
    }

    /** Charges {@code bytes} sent to the viewer against this minute. */
    public void sent(UUID viewer, long bytes, long nowMs) {
        long[] w = window(viewer, nowMs);
        synchronized (w) {
            w[1] += bytes;
        }
    }

    public int inflight(UUID viewer) {
        AtomicInteger mine = inflight.get(viewer);
        return mine == null ? 0 : mine.get();
    }

    public int queued() {
        return queued.get();
    }

    /** Drops the viewer's windows (logout, erasure). Jobs still in flight finish against a fresh counter. */
    public void forget(UUID viewer) {
        spend.remove(viewer);
        inflight.remove(viewer);
    }

    public void reset() {
        spend.clear();
        inflight.clear();
        queued.set(0);
    }
}
