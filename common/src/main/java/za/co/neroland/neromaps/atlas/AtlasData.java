package za.co.neroland.neromaps.atlas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neromaps.store.ExploredMap;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * What an atlas carries (the {@code neromaps:atlas} item component): one dimension's explored-region coverage, its
 * capacity upgrades and an optional label typed at the Cartography Desk. Locked design: an atlas carries region
 * coverage only, <b>never an identity</b> — no UUID, no player name, no position history beyond the explored bits
 * (which are the same coarse coverage a copied paper map shows), no timestamps. The label passes {@link NameFilter}.
 *
 * <p>Capacity: {@value #BASE_REGIONS} regions (32 x 32 chunks each) plus {@value #REGIONS_PER_PAGE} per Page Pack;
 * {@value #MAX_PAGES_UNBOUND} packs fit without a Binding Ring, {@value #MAX_PAGES} with one. At most 144 regions,
 * about 18 KiB of bits, so an atlas in an inventory stays a small packet.
 */
public record AtlasData(String dimension, List<Region> regions, int pages, boolean binding, String label) {

    public static final int BASE_REGIONS = 16;
    public static final int REGIONS_PER_PAGE = 16;
    public static final int MAX_PAGES_UNBOUND = 3;
    public static final int MAX_PAGES = 8;
    public static final int MAX_LABEL = 24;

    /** One 32 x 32-chunk region: its key (as {@link ExploredMap#regionKey}) and 1 024 explored bits. */
    public record Region(long key, long[] bits) {
        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("k").forGetter(Region::key),
                Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of).fieldOf("b").forGetter(Region::bits)
        ).apply(i, Region::new));

        public Region {
            long[] fixed = new long[ExploredMap.WORDS];
            if (bits != null) {
                System.arraycopy(bits, 0, fixed, 0, Math.min(ExploredMap.WORDS, bits.length));
            }
            bits = fixed;
        }

        public int regionX() {
            return (int) key;
        }

        public int regionZ() {
            return (int) (key >> 32);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Region r && r.key == key && Arrays.equals(r.bits, bits);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(key) * 31 + Arrays.hashCode(bits);
        }

        @Override
        public String toString() {
            return "Region[" + regionX() + "," + regionZ() + "]";
        }
    }

    public static final Codec<AtlasData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("dim", "").forGetter(AtlasData::dimension),
            Region.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(AtlasData::regions),
            Codec.INT.optionalFieldOf("pages", 0).forGetter(AtlasData::pages),
            Codec.BOOL.optionalFieldOf("binding", false).forGetter(AtlasData::binding),
            Codec.STRING.optionalFieldOf("label", "").forGetter(AtlasData::label)
    ).apply(i, AtlasData::new));

    public static final AtlasData BLANK = new AtlasData("", List.of(), 0, false, "");

    public AtlasData {
        dimension = dimension == null || dimension.isEmpty() ? "" : Waypoint.cleanId(dimension, "");
        boolean bound = binding;
        pages = Math.max(0, Math.min(bound ? MAX_PAGES : MAX_PAGES_UNBOUND, pages));
        int cap = BASE_REGIONS + REGIONS_PER_PAGE * pages;
        List<Region> list = regions == null ? List.of() : regions;
        regions = List.copyOf(list.size() > cap ? list.subList(0, cap) : list);
        label = label == null ? "" : NameFilter.clean(label, MAX_LABEL);
    }

    /** Regions this atlas can hold. */
    public int capacity() {
        return BASE_REGIONS + REGIONS_PER_PAGE * pages;
    }

    public boolean filled() {
        return !regions.isEmpty() && !dimension.isEmpty();
    }

    public AtlasData withLabel(String newLabel) {
        return new AtlasData(dimension, regions, pages, binding, newLabel);
    }

    /** One more Page Pack, or null when this atlas cannot take another. */
    public AtlasData withPage() {
        int max = binding ? MAX_PAGES : MAX_PAGES_UNBOUND;
        return pages >= max ? null : new AtlasData(dimension, regions, pages + 1, binding, label);
    }

    /** The Binding Ring, or null when it already has one. */
    public AtlasData withBinding() {
        return binding ? null : new AtlasData(dimension, regions, pages, true, label);
    }

    /** The same capacity and label with no coverage (a blank copy target). */
    public AtlasData cleared() {
        return new AtlasData("", List.of(), pages, binding, label);
    }

    /**
     * Records {@code source}'s coverage of {@code dim}, nearest regions to the chunk {@code (cx, cz)} first, up to
     * this atlas's capacity. Capacity and label are kept; nothing about who recorded it is.
     */
    public AtlasData record(ExploredMap source, String dim, int cx, int cz) {
        List<Region> all = new ArrayList<>();
        source.forEachRegion(dim, (k, bits) -> all.add(new Region(k, bits)));
        int rx = cx >> ExploredMap.REGION_SHIFT;
        int rz = cz >> ExploredMap.REGION_SHIFT;
        all.sort(Comparator.comparingLong(r -> distSq(r, rx, rz)));
        int cap = capacity();
        return new AtlasData(dim, all.size() > cap ? all.subList(0, cap) : all, pages, binding, label);
    }

    /** The union of this atlas and {@code other} (same dimension), kept to this atlas's capacity. */
    public AtlasData merge(AtlasData other) {
        if (!other.filled()) {
            return this;
        }
        if (filled() && !dimension.equals(other.dimension)) {
            return this;
        }
        java.util.LinkedHashMap<Long, long[]> union = new java.util.LinkedHashMap<>();
        for (Region r : regions) {
            union.put(r.key(), r.bits().clone());
        }
        for (Region r : other.regions) {
            long[] cur = union.get(r.key());
            if (cur == null) {
                union.put(r.key(), r.bits().clone());
            } else {
                for (int i = 0; i < cur.length; i++) {
                    cur[i] |= r.bits()[i];
                }
            }
        }
        List<Region> out = new ArrayList<>(union.size());
        union.forEach((k, b) -> out.add(new Region(k, b)));
        return new AtlasData(other.dimension, out, pages, binding, label);
    }

    /** Explored chunks this atlas holds. */
    public long chunkCount() {
        long n = 0;
        for (Region r : regions) {
            for (long w : r.bits()) {
                n += Long.bitCount(w);
            }
        }
        return n;
    }

    /** Whether chunk {@code (cx, cz)} is covered. */
    public boolean covers(int cx, int cz) {
        long key = ExploredMap.regionKey(cx >> ExploredMap.REGION_SHIFT, cz >> ExploredMap.REGION_SHIFT);
        for (Region r : regions) {
            if (r.key() == key) {
                int b = ((cz & (ExploredMap.REGION_CHUNKS - 1)) << ExploredMap.REGION_SHIFT)
                        | (cx & (ExploredMap.REGION_CHUNKS - 1));
                return (r.bits()[b >>> 6] & (1L << (b & 63))) != 0;
            }
        }
        return false;
    }

    /** {minChunkX, minChunkZ, maxChunkX, maxChunkZ} of the covered chunks, or null when empty. */
    public int[] extent() {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Region r : regions) {
            long[] b = r.bits();
            for (int i = 0; i < ExploredMap.WORDS * 64; i++) {
                if ((b[i >>> 6] & (1L << (i & 63))) != 0) {
                    int x = (r.regionX() << ExploredMap.REGION_SHIFT) + (i & (ExploredMap.REGION_CHUNKS - 1));
                    int z = (r.regionZ() << ExploredMap.REGION_SHIFT) + (i >>> ExploredMap.REGION_SHIFT);
                    minX = Math.min(minX, x);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        return minX == Integer.MAX_VALUE ? null : new int[] {minX, minZ, maxX, maxZ};
    }

    /** Copies this atlas's coverage into {@code target} (the reader's own map); returns the regions written. */
    public int mergeInto(ExploredMap target, int maxRegions) {
        int n = 0;
        for (Region r : regions) {
            if (target.putRegion(dimension, r.key(), r.bits(), maxRegions)) {
                n++;
            }
        }
        return n;
    }

    private static long distSq(Region r, int rx, int rz) {
        long dx = r.regionX() - rx;
        long dz = r.regionZ() - rz;
        return dx * dx + dz * dz;
    }
}
