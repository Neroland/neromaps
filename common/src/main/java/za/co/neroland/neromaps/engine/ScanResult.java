package za.co.neroland.neromaps.engine;

import za.co.neroland.neromaps.scan.ChunkColumns;

/** One scanned chunk, ids resolved on the worker, waiting to be applied on the main thread. */
public final class ScanResult {

    public final int chunkX;
    public final int chunkZ;
    public final ChunkColumns columns;
    public final String[] blockIds = new String[256];
    public final String[] biomeIds = new String[256];
    /** A cave slice (memory only, never written to disk) rather than surface terrain. */
    public final boolean cave;
    /** The session epoch it was scanned in; stale epochs are dropped. */
    public final int epoch;

    public ScanResult(int chunkX, int chunkZ, ChunkColumns columns, boolean cave, int epoch) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.columns = columns;
        this.cave = cave;
        this.epoch = epoch;
    }
}
