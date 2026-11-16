package za.co.neroland.neromaps.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * One 256 x 256 block-column tile: surface height, surface block and biome as indices into
 * per-tile palettes, fluid depth and a flags byte per column. Planar arrays, row-major z then x. Nothing else is
 * recorded: no entities, block entities, text or player data.
 *
 * <p>Not thread-safe. The client mutates live tiles on the main thread only; workers get {@link #copy()}s.
 */
public final class Tile {

    public static final int SIZE = 256;
    public static final int AREA = SIZE * SIZE;
    public static final int CHUNKS = SIZE / 16;
    /** Maximum palette sizes the codec accepts. */
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_BIOMES = 256;
    /** Heights are clamped to this range so a zigzag delta always fits the codec's 16 bits. */
    public static final int MIN_HEIGHT = -8192;
    public static final int MAX_HEIGHT = 8191;

    public static final int F_PRESENT = 1;
    public static final int F_WATER = 1 << 1;
    public static final int F_LAVA = 1 << 2;
    public static final int F_FLUID_OTHER = 1 << 3;
    public static final int F_CEILING = 1 << 4;
    public static final int F_WATERLOGGED_TOP = 1 << 5;
    /** Bits 6-7 are reserved; decoders mask them off. */
    public static final int F_KNOWN = 0x3F;
    public static final int F_FLUID = F_WATER | F_LAVA | F_FLUID_OTHER;

    public final byte[] flags = new byte[AREA];
    public final short[] height = new short[AREA];
    public final char[] block = new char[AREA];
    public final byte[] biome = new byte[AREA];
    public final byte[] depth = new byte[AREA];

    private final ArrayList<String> blockPalette = new ArrayList<>();
    private final HashMap<String, Integer> blockIndex = new HashMap<>();
    private final ArrayList<String> biomePalette = new ArrayList<>();
    private final HashMap<String, Integer> biomeIndex = new HashMap<>();

    /** Level of detail (0 = one column per block, 4 = one column per chunk). */
    public final int lod;
    /** Bumped on every change; render caches compare it. */
    public int revision;
    /** Per-chunk revision (16 x 16 chunks), for the clipmap and texture refresh. */
    public final int[] chunkRevision = new int[CHUNKS * CHUNKS];
    /** True when something changed since the last save. Main thread only. */
    public boolean unsaved;
    /** LRU stamp, main thread only. */
    public long lastUse;
    /** Render-side colour cache, owned by the colour code. Never serialised. */
    public Object colours;

    public Tile(int lod) {
        this.lod = lod;
    }

    public static int index(int x, int z) {
        return (z << 8) | x;
    }

    public boolean present(int i) {
        return (flags[i] & F_PRESENT) != 0;
    }

    /** Palette index of {@code id}, adding it if new. Ids past the cap share index 0. */
    public int internBlock(String id) {
        Integer i = blockIndex.get(id);
        if (i != null) {
            return i;
        }
        if (blockPalette.size() >= MAX_BLOCKS) {
            return 0;
        }
        int n = blockPalette.size();
        blockPalette.add(id);
        blockIndex.put(id, n);
        return n;
    }

    /** Palette index of {@code id}, adding it if new. Ids past the cap share index 0. */
    public int internBiome(String id) {
        Integer i = biomeIndex.get(id);
        if (i != null) {
            return i;
        }
        if (biomePalette.size() >= MAX_BIOMES) {
            return 0;
        }
        int n = biomePalette.size();
        biomePalette.add(id);
        biomeIndex.put(id, n);
        return n;
    }

    public String blockId(int paletteIndex) {
        return blockPalette.get(paletteIndex);
    }

    public String biomeId(int paletteIndex) {
        return biomePalette.get(paletteIndex);
    }

    public int blockPaletteSize() {
        return blockPalette.size();
    }

    public int biomePaletteSize() {
        return biomePalette.size();
    }

    /** Unmodifiable views, for the codec and tests. */
    public List<String> blockPalette() {
        return java.util.Collections.unmodifiableList(blockPalette);
    }

    public List<String> biomePalette() {
        return java.util.Collections.unmodifiableList(biomePalette);
    }

    /** Writes one column. {@code blockIdx}/{@code biomeIdx} are this tile's palette indices. */
    public void set(int x, int z, int flagBits, int h, int blockIdx, int biomeIdx, int fluidDepth) {
        int i = index(x, z);
        flags[i] = (byte) flagBits;
        height[i] = (short) Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, h));
        block[i] = (char) blockIdx;
        biome[i] = (byte) biomeIdx;
        depth[i] = (byte) Math.min(255, Math.max(0, fluidDepth));
    }

    /** Marks chunk {@code (cx, cz)} (tile-local, 0..15) as changed. */
    public void touchChunk(int cx, int cz) {
        revision++;
        chunkRevision[(cz << 4) | cx] = revision;
        unsaved = true;
    }

    /** True when any column of tile-local chunk {@code (cx, cz)} is present. */
    public boolean chunkPresent(int cx, int cz) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (int z = z0; z < z0 + 16; z++) {
            int row = z << 8;
            for (int x = x0; x < x0 + 16; x++) {
                if ((flags[row + x] & F_PRESENT) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Copies chunk {@code (cx, cz)} from {@code src} (any palette) into this tile. */
    public void copyChunkFrom(Tile src, int cx, int cz) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (int z = z0; z < z0 + 16; z++) {
            for (int x = x0; x < x0 + 16; x++) {
                int i = index(x, z);
                if ((src.flags[i] & F_PRESENT) == 0) {
                    flags[i] = 0;
                    height[i] = 0;
                    block[i] = 0;
                    biome[i] = 0;
                    depth[i] = 0;
                    continue;
                }
                set(x, z, src.flags[i] & 0xFF, src.height[i], internBlock(src.blockId(src.block[i])),
                        internBiome(src.biomeId(src.biome[i] & 0xFF)), src.depth[i] & 0xFF);
            }
        }
    }

    /** A deep copy (palettes included, render cache excluded). */
    public Tile copy() {
        Tile t = new Tile(lod);
        System.arraycopy(flags, 0, t.flags, 0, AREA);
        System.arraycopy(height, 0, t.height, 0, AREA);
        System.arraycopy(block, 0, t.block, 0, AREA);
        System.arraycopy(biome, 0, t.biome, 0, AREA);
        System.arraycopy(depth, 0, t.depth, 0, AREA);
        t.blockPalette.addAll(blockPalette);
        t.blockIndex.putAll(blockIndex);
        t.biomePalette.addAll(biomePalette);
        t.biomeIndex.putAll(biomeIndex);
        t.revision = revision;
        System.arraycopy(chunkRevision, 0, t.chunkRevision, 0, chunkRevision.length);
        return t;
    }

    /** Column-for-column equality (palette ids compared by name, absent columns ignored). */
    public boolean sameColumns(Tile o) {
        for (int i = 0; i < AREA; i++) {
            boolean p = present(i);
            if (p != o.present(i)) {
                return false;
            }
            if (!p) {
                continue;
            }
            if (flags[i] != o.flags[i] || height[i] != o.height[i] || depth[i] != o.depth[i]
                    || !blockId(block[i]).equals(o.blockId(o.block[i]))
                    || !biomeId(biome[i] & 0xFF).equals(o.biomeId(o.biome[i] & 0xFF))) {
                return false;
            }
        }
        return true;
    }

    /** Clears every column (palettes kept). */
    public void clear() {
        Arrays.fill(flags, (byte) 0);
        Arrays.fill(height, (short) 0);
        Arrays.fill(block, (char) 0);
        Arrays.fill(biome, (byte) 0);
        Arrays.fill(depth, (byte) 0);
    }
}
