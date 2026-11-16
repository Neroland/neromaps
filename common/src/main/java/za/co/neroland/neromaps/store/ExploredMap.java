package za.co.neroland.neromaps.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * One player's fog of war (decision 007): per dimension, a sparse map of 32 x 32-chunk regions, each a 1 024-bit set
 * ({@code long[16]}) of explored chunks. No timestamps, no order, no positions finer than a chunk. A cap on regions
 * per dimension bounds memory and file size; past it new regions are simply not recorded.
 */
public final class ExploredMap {

    public static final int REGION_SHIFT = 5;
    public static final int REGION_CHUNKS = 1 << REGION_SHIFT;
    public static final int WORDS = REGION_CHUNKS * REGION_CHUNKS / 64;

    private record Region(long key, long[] bits) {
        static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("k").forGetter(Region::key),
                Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of).fieldOf("b").forGetter(Region::bits)
        ).apply(i, Region::new));
    }

    private record Dim(String dimension, List<Region> regions) {
        static final Codec<Dim> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Dim::dimension),
                Region.CODEC.listOf().fieldOf("regions").forGetter(Dim::regions)
        ).apply(i, Dim::new));
    }

    public static final Codec<ExploredMap> CODEC = Dim.CODEC.listOf().xmap(ExploredMap::fromDims, ExploredMap::toDims);

    private final Map<String, Long2ObjectOpenHashMap<long[]>> dims = new HashMap<>();
    /** Bumped on every change; lets readers cache derived masks. */
    private int version;
    /** Regions refused because the cap was reached (a count only; never a position). */
    private long refused;

    public ExploredMap() {
    }

    private static ExploredMap fromDims(List<Dim> list) {
        ExploredMap m = new ExploredMap();
        for (Dim d : list) {
            Long2ObjectOpenHashMap<long[]> map = m.dims.computeIfAbsent(Waypoint.cleanId(d.dimension(), "minecraft:overworld"),
                    k -> new Long2ObjectOpenHashMap<>());
            for (Region r : d.regions()) {
                long[] bits = new long[WORDS];
                System.arraycopy(r.bits(), 0, bits, 0, Math.min(WORDS, r.bits().length));
                map.put(r.key(), bits);
            }
        }
        return m;
    }

    private List<Dim> toDims() {
        List<Dim> out = new ArrayList<>(dims.size());
        dims.forEach((dim, map) -> {
            List<Region> regions = new ArrayList<>(map.size());
            for (Long2ObjectMap.Entry<long[]> e : map.long2ObjectEntrySet()) {
                regions.add(new Region(e.getLongKey(), e.getValue().clone()));
            }
            out.add(new Dim(dim, regions));
        });
        return out;
    }

    public static long regionKey(int regionX, int regionZ) {
        return ((long) regionZ << 32) | (regionX & 0xFFFFFFFFL);
    }

    private static int bit(int chunkX, int chunkZ) {
        return ((chunkZ & (REGION_CHUNKS - 1)) << REGION_SHIFT) | (chunkX & (REGION_CHUNKS - 1));
    }

    /**
     * Marks one chunk explored. Returns true when the bit was newly set; false when it was already set or the region
     * cap refused a new region.
     */
    public boolean mark(String dimension, int chunkX, int chunkZ, int maxRegions) {
        Long2ObjectOpenHashMap<long[]> map = dims.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
        long key = regionKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        long[] bits = map.get(key);
        if (bits == null) {
            if (map.size() >= maxRegions) {
                refused++;
                return false;
            }
            bits = new long[WORDS];
            map.put(key, bits);
        }
        int b = bit(chunkX, chunkZ);
        long mask = 1L << (b & 63);
        if ((bits[b >>> 6] & mask) != 0) {
            return false;
        }
        bits[b >>> 6] |= mask;
        version++;
        return true;
    }

    public boolean isExplored(String dimension, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null) {
            return false;
        }
        long[] bits = map.get(regionKey(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
        if (bits == null) {
            return false;
        }
        int b = bit(chunkX, chunkZ);
        return (bits[b >>> 6] & (1L << (b & 63))) != 0;
    }

    /**
     * The live bits of one region ({@link #WORDS} longs, bit {@code (z & 31) << 5 | (x & 31)}), or null when the region
     * holds nothing. Read-only for the caller; server thread only.
     */
    public long @org.jetbrains.annotations.Nullable [] regionBits(String dimension, long key) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        return map == null ? null : map.get(key);
    }

    /** True when any chunk of the 16 x 16-chunk tile {@code (tx, tz)} is explored (one region lookup). */
    public boolean anyInTile(String dimension, int tx, int tz) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null) {
            return false;
        }
        // a tile is a quarter of a region: 16 x 16 chunks at offset ((tx & 1) * 16, (tz & 1) * 16)
        long[] bits = map.get(regionKey(tx >> 1, tz >> 1));
        if (bits == null) {
            return false;
        }
        int ox = (tx & 1) << 4;
        int oz = (tz & 1) << 4;
        long rowMask = 0xFFFFL << ox; // 16 bits of a 32-bit row; two rows per long
        for (int z = 0; z < 16; z++) {
            int row = oz + z;
            long word = bits[row >>> 1] >>> ((row & 1) << 5);
            if ((word & rowMask) != 0) {
                return true;
            }
        }
        return false;
    }

    /** ORs this map's explored chunks of tile {@code (tx, tz)} into {@code mask} (256 bits, index {@code z*16+x}). */
    public void orTileMask(String dimension, int tx, int tz, long[] mask) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null) {
            return;
        }
        long[] bits = map.get(regionKey(tx >> 1, tz >> 1));
        if (bits == null) {
            return;
        }
        int ox = (tx & 1) << 4;
        int oz = (tz & 1) << 4;
        for (int z = 0; z < 16; z++) {
            int row = oz + z;
            long word = bits[row >>> 1] >>> (((row & 1) << 5) + ox);
            long sixteen = word & 0xFFFFL;
            mask[z >>> 2] |= sixteen << ((z & 3) << 4);
        }
    }

    /** Copies every explored chunk of {@code other} in {@code dimension} into this map (atlas merge). */
    public int mergeFrom(ExploredMap other, String dimension, int maxRegions) {
        Long2ObjectOpenHashMap<long[]> src = other.dims.get(dimension);
        if (src == null) {
            return 0;
        }
        Long2ObjectOpenHashMap<long[]> map = dims.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
        int added = 0;
        for (Long2ObjectMap.Entry<long[]> e : src.long2ObjectEntrySet()) {
            long[] bits = map.get(e.getLongKey());
            if (bits == null) {
                if (map.size() >= maxRegions) {
                    refused++;
                    continue;
                }
                bits = new long[WORDS];
                map.put(e.getLongKey(), bits);
            }
            long[] s = e.getValue();
            for (int i = 0; i < WORDS; i++) {
                long n = bits[i] | s[i];
                added += Long.bitCount(n ^ bits[i]);
                bits[i] = n;
            }
        }
        if (added > 0) {
            version++;
        }
        return added;
    }

    /** Visits a copy of every explored region of {@code dimension} (atlas recording). */
    public void forEachRegion(String dimension, java.util.function.BiConsumer<Long, long[]> visitor) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null) {
            return;
        }
        for (Long2ObjectMap.Entry<long[]> e : map.long2ObjectEntrySet()) {
            visitor.accept(e.getLongKey(), e.getValue().clone());
        }
    }

    /** Puts one region's bits (atlas reading); a region beyond {@code maxRegions} is refused. */
    public boolean putRegion(String dimension, long key, long[] bits, int maxRegions) {
        Long2ObjectOpenHashMap<long[]> map = dims.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
        long[] cur = map.get(key);
        if (cur == null) {
            if (map.size() >= maxRegions) {
                refused++;
                return false;
            }
            cur = new long[WORDS];
            map.put(key, cur);
        }
        for (int i = 0; i < WORDS && i < bits.length; i++) {
            cur[i] |= bits[i];
        }
        version++;
        return true;
    }

    public int regionCount(String dimension) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        return map == null ? 0 : map.size();
    }

    /** Explored chunk count in one dimension. */
    public long chunkCount(String dimension) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null) {
            return 0;
        }
        long n = 0;
        for (long[] bits : map.values()) {
            for (long w : bits) {
                n += Long.bitCount(w);
            }
        }
        return n;
    }

    public java.util.Set<String> dimensions() {
        return java.util.Collections.unmodifiableSet(dims.keySet());
    }

    /** The explored extent of one dimension as {minChunkX, minChunkZ, maxChunkX, maxChunkZ}, or null. */
    public int[] extent(String dimension) {
        Long2ObjectOpenHashMap<long[]> map = dims.get(dimension);
        if (map == null || map.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : map.keySet()) {
            int rx = (int) key;
            int rz = (int) (key >> 32);
            minX = Math.min(minX, rx << REGION_SHIFT);
            minZ = Math.min(minZ, rz << REGION_SHIFT);
            maxX = Math.max(maxX, (rx << REGION_SHIFT) + REGION_CHUNKS - 1);
            maxZ = Math.max(maxZ, (rz << REGION_SHIFT) + REGION_CHUNKS - 1);
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    public int version() {
        return version;
    }

    public long refused() {
        return refused;
    }

    public boolean isEmpty() {
        for (Long2ObjectOpenHashMap<long[]> m : dims.values()) {
            if (!m.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
