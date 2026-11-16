package za.co.neroland.neromaps.server.tiles;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * One tick of capture for one dimension, pure and clock-injected (the M6 scheduler test drives it with a fake clock).
 * Pops dirty chunks and snapshots those that are loaded until the next snapshot would overrun the budget; an unloaded
 * chunk is parked, never loaded. Snapshots go to {@code sink} (the worker queue); if the sink refuses (queue full) the
 * chunk is marked dirty again and the tick ends.
 */
public final class CaptureLoop {

    /** Result counters of one run. */
    public static final class Stats {
        public int snapshots;
        public int misses;
        public int refused;
        public long nanos;
        public long worstSnapshotNanos;

        void add(Stats o) {
            snapshots += o.snapshots;
            misses += o.misses;
            refused += o.refused;
            nanos += o.nanos;
            worstSnapshotNanos = Math.max(worstSnapshotNanos, o.worstSnapshotNanos);
        }
    }

    private CaptureLoop() {
    }

    /**
     * Runs until {@code budgetNanos} would be exceeded. The estimate of the next snapshot's cost is the last one's,
     * so with steady costs the total never passes the budget.
     */
    public static Stats run(DirtyMarks marks, ChunkAccess access, Predicate<ChunkAccess.Snapshot> sink,
            long budgetNanos, LongSupplier clock, long estimateNanos) {
        Stats st = new Stats();
        if (budgetNanos <= 0) {
            return st;
        }
        long t0 = clock.getAsLong();
        long estimate = Math.max(1, estimateNanos);
        while (true) {
            long elapsed = clock.getAsLong() - t0;
            if (elapsed + estimate > budgetNanos) {
                break;
            }
            long key = marks.poll();
            if (key == Long.MIN_VALUE) {
                break;
            }
            long s0 = clock.getAsLong();
            ChunkAccess.Snapshot snap = access.snapshotIfLoaded(DirtyMarks.x(key), DirtyMarks.z(key));
            long cost = clock.getAsLong() - s0;
            if (snap == null) {
                st.misses++;
                marks.parkUnloaded(key);
                continue;
            }
            estimate = Math.max(1, cost);
            st.worstSnapshotNanos = Math.max(st.worstSnapshotNanos, cost);
            if (!sink.test(snap)) {
                st.refused++;
                marks.mark(DirtyMarks.x(key), DirtyMarks.z(key));
                break;
            }
            st.snapshots++;
        }
        st.nanos = clock.getAsLong() - t0;
        return st;
    }

    static void merge(Stats into, Stats add) {
        into.add(add);
    }
}
