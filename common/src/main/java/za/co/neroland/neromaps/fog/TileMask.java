package za.co.neroland.neromaps.fog;

import za.co.neroland.neromaps.store.ExploredMap;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;

/**
 * Per-chunk visibility of one tile and the masked copy built from it (fog of war is an access control on every server
 * read of terrain). At LOD {@code l} a tile covers {@code 16 << l} chunks per side and one chunk covers {@code 16 >> l}
 * pixels per side (LOD is capped at 4, so every pixel lies inside exactly one chunk). Hidden pixels are cleared to
 * absent, so a masked tile carries nothing from outside the viewer's fog. The etag a viewer sees for a masked tile is
 * the content hash of the masked bytes themselves ({@link #encodeMasked}), so it moves only when something the viewer
 * can see moves: a change in a hidden chunk leaves it, and every other signal derived from it, unchanged.
 */
public final class TileMask {

    private final int lod;
    private final int side;
    private final long[] bits;
    private int visible;

    private TileMask(int lod) {
        this.lod = lod;
        this.side = 16 << lod;
        this.bits = new long[(side * side + 63) >>> 6];
    }

    /**
     * Builds the mask on the server thread (the explored maps are server-thread state). Works region by region (one
     * bitset copy per 32-chunk row), so even a LOD-4 tile (256 x 256 chunks) costs a few thousand word operations, not
     * one lookup per chunk; {@link #ofChunks} is the reference it must agree with.
     */
    public static TileMask of(Visibility v, int lod, int tx, int tz) {
        TileMask m = new TileMask(lod);
        int cx0 = tx * m.side;
        int cz0 = tz * m.side;
        if (v.everything()) {
            java.util.Arrays.fill(m.bits, -1L);
            int tail = (m.side * m.side) & 63;
            if (tail != 0) {
                m.bits[m.bits.length - 1] = (1L << tail) - 1;
            }
            m.visible = m.side * m.side;
            return m;
        }
        long[] reg = new long[ExploredMap.WORDS];
        if (m.side < ExploredMap.REGION_CHUNKS) {
            // LOD 0: half a region on each axis
            if (!v.orRegion(cx0 >> ExploredMap.REGION_SHIFT, cz0 >> ExploredMap.REGION_SHIFT, reg)) {
                return m;
            }
            int ox = cx0 & (ExploredMap.REGION_CHUNKS - 1);
            int oz = cz0 & (ExploredMap.REGION_CHUNKS - 1);
            for (int z = 0; z < m.side; z++) {
                int row = oz + z;
                long bits = (reg[row >>> 1] >>> ((row & 1) << 5) >>> ox) & ((1L << m.side) - 1);
                m.put(z * m.side, bits);
            }
            return m;
        }
        int regions = m.side >> ExploredMap.REGION_SHIFT;
        int rx0 = cx0 >> ExploredMap.REGION_SHIFT;
        int rz0 = cz0 >> ExploredMap.REGION_SHIFT;
        for (int rz = 0; rz < regions; rz++) {
            for (int rx = 0; rx < regions; rx++) {
                java.util.Arrays.fill(reg, 0L);
                if (!v.orRegion(rx0 + rx, rz0 + rz, reg)) {
                    continue;
                }
                for (int row = 0; row < ExploredMap.REGION_CHUNKS; row++) {
                    long bits = (reg[row >>> 1] >>> ((row & 1) << 5)) & 0xFFFFFFFFL;
                    if (bits != 0) {
                        m.put((rz * ExploredMap.REGION_CHUNKS + row) * m.side + rx * ExploredMap.REGION_CHUNKS, bits);
                    }
                }
            }
        }
        return m;
    }

    /** ORs a row run of at most 32 bits starting at bit {@code index} (16-aligned, so it never straddles a word). */
    private void put(int index, long run) {
        if (run == 0) {
            return;
        }
        bits[index >>> 6] |= run << (index & 63);
        visible += Long.bitCount(run);
    }

    /** The per-chunk reference implementation of {@link #of} (tests compare the two). */
    public static TileMask ofChunks(Visibility v, int lod, int tx, int tz) {
        TileMask m = new TileMask(lod);
        int cx0 = tx * m.side;
        int cz0 = tz * m.side;
        for (int z = 0; z < m.side; z++) {
            for (int x = 0; x < m.side; x++) {
                if (v.chunk(cx0 + x, cz0 + z)) {
                    int i = z * m.side + x;
                    m.bits[i >>> 6] |= 1L << (i & 63);
                    m.visible++;
                }
            }
        }
        return m;
    }

    /** True when both masks cover the same chunks (tests). */
    public boolean sameAs(TileMask o) {
        return lod == o.lod && visible == o.visible && java.util.Arrays.equals(bits, o.bits);
    }

    public boolean none() {
        return visible == 0;
    }

    public boolean all() {
        return visible == side * side;
    }

    public boolean chunk(int localX, int localZ) {
        int i = localZ * side + localX;
        return (bits[i >>> 6] & (1L << (i & 63))) != 0;
    }

    /** A stable 64-bit hash of the mask. */
    public long hash() {
        long h = 0xCBF29CE484222325L ^ lod;
        for (long w : bits) {
            h ^= w;
            h *= 0x100000001B3L;
            h ^= h >>> 31;
        }
        return h;
    }

    /** Bytes held by the mask bits (cache accounting). */
    public long byteSize() {
        return bits.length * 8L;
    }

    /**
     * The encoded masked copy of {@code t} ({@link TileCodec#encode} of {@link #apply}), or null when the masked copy has
     * no present pixel: a tile the viewer can see nothing of is reported exactly like a missing one. Its
     * {@link TileCodec#contentHash} is the etag the viewer sees; it depends on the visible pixels only.
     */
    public byte @org.jetbrains.annotations.Nullable [] encodeMasked(Tile t) {
        Tile out = apply(t);
        boolean any = false;
        for (int i = 0; i < Tile.AREA && !any; i++) {
            any = out.present(i);
        }
        return any ? TileCodec.encode(out) : null;
    }

    /** A copy of {@code t} with every pixel of a hidden chunk cleared to absent. */
    public Tile apply(Tile t) {
        Tile out = t.copy();
        int px = 16 >> lod; // pixels per chunk side
        for (int cz = 0; cz < side; cz++) {
            for (int cx = 0; cx < side; cx++) {
                if (chunk(cx, cz)) {
                    continue;
                }
                for (int z = 0; z < px; z++) {
                    for (int x = 0; x < px; x++) {
                        out.set(cx * px + x, cz * px + z, 0, 0, 0, 0, 0);
                    }
                }
            }
        }
        return out;
    }
}
