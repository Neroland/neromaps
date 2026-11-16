package za.co.neroland.neromaps.tile;

import org.jetbrains.annotations.Nullable;

/**
 * Level-of-detail downsampling: four children at level L become one parent at L + 1, each 2 x 2
 * block of child columns becoming one column. The column with the greatest height wins, except when at least two
 * of the four are fluid: then the shallowest fluid column wins. All four absent stays absent. Off-thread.
 */
public final class TileLod {

    public static final int MAX_LOD = 4;

    private TileLod() {
    }

    /**
     * Builds the parent of four children, any of which may be null (unexplored). {@code nw} is the child at
     * (2tx, 2tz), {@code ne} (2tx+1, 2tz), {@code sw} (2tx, 2tz+1), {@code se} (2tx+1, 2tz+1).
     */
    public static Tile downsample(@Nullable Tile nw, @Nullable Tile ne, @Nullable Tile sw, @Nullable Tile se,
            int parentLod) {
        Tile out = new Tile(parentLod);
        quadrant(out, nw, 0, 0);
        quadrant(out, ne, 128, 0);
        quadrant(out, sw, 0, 128);
        quadrant(out, se, 128, 128);
        return out;
    }

    private static void quadrant(Tile out, @Nullable Tile c, int ox, int oz) {
        if (c == null) {
            return;
        }
        int[] remapBlock = new int[Math.max(1, c.blockPaletteSize())];
        int[] remapBiome = new int[Math.max(1, c.biomePaletteSize())];
        java.util.Arrays.fill(remapBlock, -1);
        java.util.Arrays.fill(remapBiome, -1);
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int a = Tile.index(x * 2, z * 2);
                int pick = pick(c, a, a + 1, a + Tile.SIZE, a + Tile.SIZE + 1);
                if (pick < 0) {
                    continue;
                }
                int b = c.block[pick];
                if (remapBlock[b] < 0) {
                    remapBlock[b] = out.internBlock(c.blockId(b));
                }
                int bi = c.biome[pick] & 0xFF;
                if (remapBiome[bi] < 0) {
                    remapBiome[bi] = out.internBiome(c.biomeId(bi));
                }
                out.set(ox + x, oz + z, c.flags[pick] & 0xFF, c.height[pick], remapBlock[b], remapBiome[bi],
                        c.depth[pick] & 0xFF);
            }
        }
    }

    /** The winning column of four, or -1 when all are absent. */
    static int pick(Tile c, int i0, int i1, int i2, int i3) {
        int fluids = 0;
        int best = -1;
        int[] idx = {i0, i1, i2, i3};
        for (int i : idx) {
            if (c.present(i) && (c.flags[i] & Tile.F_FLUID) != 0) {
                fluids++;
            }
        }
        if (fluids >= 2) {
            for (int i : idx) {
                if (c.present(i) && (c.flags[i] & Tile.F_FLUID) != 0
                        && (best < 0 || (c.depth[i] & 0xFF) < (c.depth[best] & 0xFF))) {
                    best = i;
                }
            }
            return best;
        }
        for (int i : idx) {
            if (c.present(i) && (best < 0 || c.height[i] > c.height[best])) {
                best = i;
            }
        }
        return best;
    }
}
