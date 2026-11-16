package za.co.neroland.neromaps.server.tiles;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

/**
 * The chunks of one dimension whose terrain changed since they were last captured. Marks are memory
 * only and coalesced per chunk; a restart loses them and the next exploration or block change re-marks. Bounded:
 * past {@link #MAX} marks the oldest are forgotten (their chunks are re-marked when they next change).
 */
public final class DirtyMarks {

    public static final int MAX = 262_144;

    private final LongLinkedOpenHashSet marks = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet unloaded = new LongLinkedOpenHashSet();
    private long dropped;

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) key;
    }

    public static int z(long key) {
        return (int) (key >> 32);
    }

    public synchronized void mark(int chunkX, int chunkZ) {
        long k = key(chunkX, chunkZ);
        unloaded.remove(k);
        if (marks.add(k) && marks.size() > MAX) {
            marks.removeFirstLong();
            dropped++;
        }
    }

    /** The oldest mark, removed; {@code Long.MIN_VALUE} when there is none. */
    public synchronized long poll() {
        return marks.isEmpty() ? Long.MIN_VALUE : marks.removeFirstLong();
    }

    /** A chunk that was not loaded when its turn came: it waits, without being polled again every tick. */
    public synchronized void parkUnloaded(long key) {
        if (unloaded.add(key) && unloaded.size() > MAX) {
            unloaded.removeFirstLong();
            dropped++;
        }
    }

    /** Puts parked chunks back in the queue (they are tried again; still-unloaded ones park again). */
    public synchronized int retryParked(int max) {
        int n = 0;
        LongIterator it = unloaded.iterator();
        while (it.hasNext() && n < max) {
            long k = it.nextLong();
            it.remove();
            marks.add(k);
            n++;
        }
        return n;
    }

    public synchronized int size() {
        return marks.size();
    }

    public synchronized int parked() {
        return unloaded.size();
    }

    public synchronized long dropped() {
        return dropped;
    }

    public synchronized void clear() {
        marks.clear();
        unloaded.clear();
    }
}
