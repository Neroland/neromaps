package za.co.neroland.neromaps.engine;

/**
 * The minimap's toroidal window: an N x N texture where texel = world block mod N, so the minimap
 * is always one surface batch. The window is cut into 16 x 16 chunk slots. Each slot remembers which world chunk
 * and which revision it holds; {@link #retarget} marks slots whose wanted chunk or revision changed, and
 * {@link #pollNearest} hands them out nearest-first. Allocation-free after construction.
 */
public final class Clipmap {

    /** Revision of a chunk's source data, or a negative value when there is none. */
    @FunctionalInterface
    public interface Revisions {
        int revision(int chunkX, int chunkZ);
    }

    public final int size;
    public final int slots;
    private final int[] slotChunkX;
    private final int[] slotChunkZ;
    private final int[] slotRevision;
    private final long[] dirty;
    private final int[] spiralDx;
    private final int[] spiralDz;
    private int dirtyCount;
    private int centreX;
    private int centreZ;
    private int optionsVersion = Integer.MIN_VALUE;

    public Clipmap(int size) {
        if (size % 16 != 0 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("size");
        }
        this.size = size;
        this.slots = size / 16;
        int n = slots * slots;
        slotChunkX = new int[n];
        slotChunkZ = new int[n];
        slotRevision = new int[n];
        dirty = new long[(n + 63) / 64];
        java.util.Arrays.fill(slotChunkX, Integer.MIN_VALUE);
        // nearest-first order of chunk offsets inside the window
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        int half = slots / 2;
        java.util.Arrays.sort(order, (a, b) -> {
            int ax = a % slots - half;
            int az = a / slots - half;
            int bx = b % slots - half;
            int bz = b / slots - half;
            return Integer.compare(ax * ax + az * az, bx * bx + bz * bz);
        });
        spiralDx = new int[n];
        spiralDz = new int[n];
        for (int i = 0; i < n; i++) {
            spiralDx[i] = order[i] % slots - half;
            spiralDz[i] = order[i] / slots - half;
        }
    }

    public int dirtyCount() {
        return dirtyCount;
    }

    private int slot(int chunkX, int chunkZ) {
        return Math.floorMod(chunkZ, slots) * slots + Math.floorMod(chunkX, slots);
    }

    private void mark(int s) {
        long bit = 1L << (s & 63);
        if ((dirty[s >>> 6] & bit) == 0) {
            dirty[s >>> 6] |= bit;
            dirtyCount++;
        }
    }

    /** Forgets every slot (dimension change, style change): everything refills nearest-first. */
    public void invalidate() {
        java.util.Arrays.fill(slotChunkX, Integer.MIN_VALUE);
        optionsVersion = Integer.MIN_VALUE;
    }

    /**
     * Re-centres the window on the player's chunk and marks every slot whose wanted chunk, data revision or
     * raster-options version changed.
     */
    public void retarget(int playerChunkX, int playerChunkZ, Revisions revisions, int rasterVersion) {
        centreX = playerChunkX;
        centreZ = playerChunkZ;
        boolean all = rasterVersion != optionsVersion;
        optionsVersion = rasterVersion;
        int half = slots / 2;
        for (int dz = -half; dz < half; dz++) {
            for (int dx = -half; dx < half; dx++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                int s = slot(cx, cz);
                int rev = revisions.revision(cx, cz);
                if (all || slotChunkX[s] != cx || slotChunkZ[s] != cz || slotRevision[s] != rev) {
                    slotChunkX[s] = cx;
                    slotChunkZ[s] = cz;
                    slotRevision[s] = rev;
                    mark(s);
                }
            }
        }
    }

    /**
     * The dirty slot nearest the centre, cleared, as its chunk key ({@link za.co.neroland.neromaps.tile.TileKey#chunk}),
     * or {@link Long#MIN_VALUE} when none is dirty.
     */
    public long pollNearest() {
        if (dirtyCount == 0) {
            return Long.MIN_VALUE;
        }
        for (int k = 0; k < spiralDx.length; k++) {
            int cx = centreX + spiralDx[k];
            int cz = centreZ + spiralDz[k];
            int s = slot(cx, cz);
            long bit = 1L << (s & 63);
            if ((dirty[s >>> 6] & bit) != 0) {
                dirty[s >>> 6] &= ~bit;
                dirtyCount--;
                return za.co.neroland.neromaps.tile.TileKey.chunk(slotChunkX[s], slotChunkZ[s]);
            }
        }
        java.util.Arrays.fill(dirty, 0L);
        dirtyCount = 0;
        return Long.MIN_VALUE;
    }

    /** Texel x of a world chunk's west edge inside the window. */
    public int texelX(int chunkX) {
        return Math.floorMod(chunkX, slots) * 16;
    }

    public int texelZ(int chunkZ) {
        return Math.floorMod(chunkZ, slots) * 16;
    }
}
