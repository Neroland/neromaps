package za.co.neroland.neromaps.spike;

/**
 * SPIKE. Dirty marks for the 16 x 16 chunks of one 256 x 256 texture, coalesced in a 256-bit set. Marking the same
 * chunk twice before it is uploaded costs one upload. Allocation-free.
 */
public final class SpikeDirtyChunks {

    /** Chunks per side. */
    public static final int CHUNKS = 16;

    private final long[] bits = new long[CHUNKS * CHUNKS / 64];
    private int count;

    /** Marks chunk {@code (cx, cz)}, each in {@code 0..15}. */
    public void mark(int cx, int cz) {
        int i = (cz & 15) * CHUNKS + (cx & 15);
        long bit = 1L << (i & 63);
        if ((bits[i >>> 6] & bit) == 0) {
            bits[i >>> 6] |= bit;
            count++;
        }
    }

    /** Marks every chunk (first fill). */
    public void markAll() {
        for (int i = 0; i < bits.length; i++) {
            bits[i] = -1L;
        }
        count = CHUNKS * CHUNKS;
    }

    /** Removes and returns the lowest dirty chunk index ({@code cz * 16 + cx}), or -1 when clean. */
    public int poll() {
        for (int w = 0; w < bits.length; w++) {
            long word = bits[w];
            if (word != 0) {
                int b = Long.numberOfTrailingZeros(word);
                bits[w] = word & ~(1L << b);
                count--;
                return (w << 6) | b;
            }
        }
        return -1;
    }

    /** Dirty chunks waiting. */
    public int count() {
        return count;
    }
}
