package za.co.neroland.neromaps.scan;

/** The scanner's output for one chunk: 16 x 16 columns, index {@code z * 16 + x}, opaque block/biome keys. */
public final class ChunkColumns {

    public final short[] height = new short[256];
    public final int[] block = new int[256];
    public final int[] biome = new int[256];
    public final byte[] depth = new byte[256];
    public final byte[] flags = new byte[256];
}
