package za.co.neroland.neromaps.tile;

/** Packs (lod, tile x, tile z) or (chunk x, chunk z) into one long, so maps and sets need no boxing. */
public final class TileKey {

    private TileKey() {
    }

    /** Key of a tile at {@code lod}. Tile coordinates fit in 28 bits each at every LOD. */
    public static long tile(int lod, int tx, int tz) {
        return ((long) lod << 56) | ((tx & 0x0FFFFFFFL) << 28) | (tz & 0x0FFFFFFFL);
    }

    public static int lod(long key) {
        return (int) (key >>> 56);
    }

    public static int tx(long key) {
        return ((int) (key >>> 28) << 4) >> 4;
    }

    public static int tz(long key) {
        return ((int) key << 4) >> 4;
    }

    /** Key of a chunk position. */
    public static long chunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }
}
