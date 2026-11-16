package za.co.neroland.neromaps.engine;

import java.util.Arrays;
import java.util.function.LongPredicate;

import org.jetbrains.annotations.Nullable;

/**
 * An open-addressing long-to-object map with linear probing and backward-shift deletion. {@link #get} never
 * allocates (no boxing), which the per-frame minimap path relies on. Not thread-safe.
 */
public final class LongObjMap<V> {

    private long[] keys;
    private Object[] values;
    private boolean[] used;
    private int size;
    private int mask;

    public LongObjMap() {
        this(64);
    }

    public LongObjMap(int capacity) {
        int cap = Integer.highestOneBit(Math.max(4, capacity * 2 - 1));
        keys = new long[cap];
        values = new Object[cap];
        used = new boolean[cap];
        mask = cap - 1;
    }

    private static int mix(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public @Nullable V get(long k) {
        int i = mix(k) & mask;
        while (used[i]) {
            if (keys[i] == k) {
                return (V) values[i];
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    public boolean containsKey(long k) {
        int i = mix(k) & mask;
        while (used[i]) {
            if (keys[i] == k) {
                return true;
            }
            i = (i + 1) & mask;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public @Nullable V put(long k, V v) {
        if ((size + 1) * 4 > keys.length * 3) {
            grow();
        }
        int i = mix(k) & mask;
        while (used[i]) {
            if (keys[i] == k) {
                V old = (V) values[i];
                values[i] = v;
                return old;
            }
            i = (i + 1) & mask;
        }
        used[i] = true;
        keys[i] = k;
        values[i] = v;
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    public @Nullable V remove(long k) {
        int i = mix(k) & mask;
        while (used[i]) {
            if (keys[i] == k) {
                V old = (V) values[i];
                shift(i);
                size--;
                return old;
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    private void shift(int gap) {
        int i = gap;
        while (true) {
            i = (i + 1) & mask;
            if (!used[i]) {
                break;
            }
            int home = mix(keys[i]) & mask;
            boolean move = gap <= i ? (home <= gap || home > i) : (home <= gap && home > i);
            if (move) {
                keys[gap] = keys[i];
                values[gap] = values[i];
                gap = i;
            }
        }
        used[gap] = false;
        values[gap] = null;
    }

    private void grow() {
        long[] ok = keys;
        Object[] ov = values;
        boolean[] ou = used;
        int cap = ok.length * 2;
        keys = new long[cap];
        values = new Object[cap];
        used = new boolean[cap];
        mask = cap - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) {
            if (ou[i]) {
                put(ok[i], cast(ov[i]));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }

    public void clear() {
        Arrays.fill(used, false);
        Arrays.fill(values, null);
        size = 0;
    }

    /** Visits every entry. The visitor must not modify the map. */
    public void forEach(Visitor<V> visitor) {
        for (int i = 0; i < keys.length; i++) {
            if (used[i]) {
                visitor.visit(keys[i], cast(values[i]));
            }
        }
    }

    /** Removes every key the predicate accepts. */
    public void removeIf(LongPredicate predicate) {
        long[] doomed = new long[size];
        int n = 0;
        for (int i = 0; i < keys.length; i++) {
            if (used[i] && predicate.test(keys[i])) {
                doomed[n++] = keys[i];
            }
        }
        for (int i = 0; i < n; i++) {
            remove(doomed[i]);
        }
    }

    @FunctionalInterface
    public interface Visitor<V> {
        void visit(long key, V value);
    }
}
