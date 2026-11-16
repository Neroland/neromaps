package za.co.neroland.neromaps.scan;

/**
 * What the column scanner reads: an immutable snapshot of one chunk (16 x 16 columns, chunk-local x/z), safe to
 * read from a worker thread. Block and biome values are opaque keys the snapshot resolves to ids later.
 */
public interface ColumnSource {

    /** Air, or a block the vanilla map draws as nothing (glass, barriers, light). The scan passes through it. */
    int KIND_INVISIBLE = 0;
    int KIND_SOLID = 1;
    /** A water source/flowing block or an always-wet plant (kelp, seagrass, bubble column). */
    int KIND_WATER = 2;
    int KIND_LAVA = 3;
    int KIND_FLUID_OTHER = 4;
    /** A non-full block with a waterlogged property set: the surface, flagged {@code WATERLOGGED_TOP}. */
    int KIND_WATERLOGGED = 5;

    /** Lowest block Y in the world. */
    int minY();

    /** One above the highest block Y in the world. */
    int maxY();

    /** The WORLD_SURFACE heightmap value (first Y above the top non-air block), chunk-local x/z. */
    int surfaceY(int x, int z);

    /** True when the 16-block section holding {@code y} is all air or not in the snapshot. */
    boolean sectionEmpty(int y);

    int kind(int x, int y, int z);

    /** Opaque key of the block at a position. */
    int block(int x, int y, int z);

    /** Opaque key of the biome at a position. */
    int biome(int x, int y, int z);

    /** Key the snapshot uses for "nothing found" (void columns). */
    int airBlock();
}
