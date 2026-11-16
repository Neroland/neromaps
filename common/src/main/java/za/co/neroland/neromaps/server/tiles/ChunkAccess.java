package za.co.neroland.neromaps.server.tiles;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.scan.ColumnSource;

/**
 * The capture loop's only window onto the world: a copy of a chunk <em>if it is already loaded</em>, else null. There
 * is deliberately no method that loads, generates, tickets or holds a chunk; the Minecraft implementation uses
 * {@code getChunkNow}, which never does any of those.
 */
public interface ChunkAccess {

    /** An immutable snapshot of a loaded chunk, or null when it is not loaded (nothing is loaded to answer). */
    @Nullable Snapshot snapshotIfLoaded(int chunkX, int chunkZ);

    /** What the capture worker needs from one chunk: the columns source and its id resolvers. */
    interface Snapshot extends ColumnSource {
        int chunkX();

        int chunkZ();

        /** True for dimensions with a ceiling (the scan skips it). */
        boolean roofed();

        /** Highest Y a roofed scan considers (min Y + logical height - 1). */
        int roofTop();

        /** The block id for a key from {@link #block}; worker thread. */
        String blockId(int key);

        /** The biome id for a key from {@link #biome}; worker thread. */
        String biomeId(int key);
    }
}
