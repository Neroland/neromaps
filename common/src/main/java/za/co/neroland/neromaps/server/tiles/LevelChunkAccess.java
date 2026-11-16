package za.co.neroland.neromaps.server.tiles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;

/**
 * {@link ChunkAccess} over a live {@link ServerLevel}: {@code getChunkSource().getChunkNow(x, z)} returns a chunk only
 * when it is already fully loaded and never loads, generates or tickets one. The snapshot is a copy, so nothing holds
 * the chunk after this returns. Server thread only.
 */
public final class LevelChunkAccess implements ChunkAccess {

    private final ServerLevel level;

    public LevelChunkAccess(ServerLevel level) {
        this.level = level;
    }

    @Override
    public @Nullable Snapshot snapshotIfLoaded(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        return ServerChunkSnapshot.of(chunk, level);
    }
}
