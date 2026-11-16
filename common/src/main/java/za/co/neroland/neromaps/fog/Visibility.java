package za.co.neroland.neromaps.fog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.ExploredMap;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;

/**
 * Fog of war as an access control (decision 007). For one viewer and one dimension it gathers the explored maps the
 * viewer may see: their own (atlas merges are already copied in) and those of every player with a live
 * {@code EXPLORED_MAP} grant to them. Built per request against live grants, so a revocation takes effect on the next
 * read. The public-map policy makes everything visible. Every server read path of terrain asks this first.
 */
public final class Visibility {

    /** All 256 chunks of a tile visible. */
    public static final long[] FULL = {-1L, -1L, -1L, -1L};

    private final boolean everything;
    private final String dimension;
    private final List<ExploredMap> maps;

    private Visibility(boolean everything, String dimension, List<ExploredMap> maps) {
        this.everything = everything;
        this.dimension = dimension;
        this.maps = maps;
    }

    /** What {@code viewer} may see of {@code dimension} right now. */
    public static Visibility of(MapPlayerState state, UUID viewer, String dimension, boolean publicMap) {
        if (publicMap) {
            return new Visibility(true, dimension, List.of());
        }
        List<ExploredMap> maps = new ArrayList<>(2);
        state.find(viewer).map(PlayerRecord::explored).ifPresent(maps::add);
        for (UUID sharer : new ConsentLedger(state, () -> 0L).mapSharersFor(viewer, dimension)) {
            state.find(sharer).map(PlayerRecord::explored).ifPresent(maps::add);
        }
        return new Visibility(false, dimension, maps);
    }

    public boolean everything() {
        return everything;
    }

    public boolean chunk(int chunkX, int chunkZ) {
        if (everything) {
            return true;
        }
        for (ExploredMap m : maps) {
            if (m.isExplored(dimension, chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ORs the visible chunks of region {@code (regionX, regionZ)} (32 x 32 chunks, {@link ExploredMap} bit layout) into
     * {@code out}. Returns false when no map holds anything there. Not for {@link #everything()} views.
     */
    public boolean orRegion(int regionX, int regionZ, long[] out) {
        long key = ExploredMap.regionKey(regionX, regionZ);
        boolean any = false;
        for (ExploredMap m : maps) {
            long[] bits = m.regionBits(dimension, key);
            if (bits != null) {
                for (int i = 0; i < out.length && i < bits.length; i++) {
                    out[i] |= bits[i];
                }
                any = true;
            }
        }
        return any;
    }

    /** The dimension this view covers. */
    public String dimension() {
        return dimension;
    }

    /** True when any chunk of LOD-0 tile {@code (tx, tz)} is visible. */
    public boolean anyInTile(int tx, int tz) {
        if (everything) {
            return true;
        }
        for (ExploredMap m : maps) {
            if (m.anyInTile(dimension, tx, tz)) {
                return true;
            }
        }
        return false;
    }

    public static boolean empty(long[] mask) {
        return (mask[0] | mask[1] | mask[2] | mask[3]) == 0;
    }

    public static boolean full(long[] mask) {
        return (mask[0] & mask[1] & mask[2] & mask[3]) == -1L;
    }
}
