package za.co.neroland.neromaps.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neromaps.store.Waypoint;

/**
 * A printed area: the {@code neromaps:wall_print} item component on a Wall Map printed at the Cartography Desk, and
 * what every panel of a wall-map array stores. An area and a dimension, <b>never an owner</b>: the centre of the
 * array in block coordinates and {@code span}, the blocks each panel block covers (north up, east right).
 *
 * <p>What a viewer sees of it is decided per viewer at read time (decision 007, the private reading): the wall map
 * draws server tiles through the same fog-checked path as the world map, so
 * every viewer sees only the parts of the area inside <i>their own</i> fog of war (or everything on a public-map
 * server). A wall map can never show anyone terrain they could not already see.
 */
public record WallPrint(String dimension, int centreX, int centreZ, int span) {

    /** The allowed spans (blocks per panel block); a panel texture is 32 px, so 1, 2 or 4 blocks per pixel. */
    public static final int[] SPANS = {32, 64, 128};
    public static final int PANEL_PX = 32;
    public static final int MAX_COORD = 30_000_000;

    public static final Codec<WallPrint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dim").forGetter(WallPrint::dimension),
            Codec.INT.fieldOf("x").forGetter(WallPrint::centreX),
            Codec.INT.fieldOf("z").forGetter(WallPrint::centreZ),
            Codec.INT.optionalFieldOf("span", 64).forGetter(WallPrint::span)
    ).apply(i, WallPrint::new));

    public WallPrint {
        dimension = Waypoint.cleanId(dimension == null ? "" : dimension, "minecraft:overworld");
        centreX = Math.max(-MAX_COORD, Math.min(MAX_COORD, centreX));
        centreZ = Math.max(-MAX_COORD, Math.min(MAX_COORD, centreZ));
        span = snap(span);
    }

    public static int snap(int span) {
        int best = SPANS[0];
        for (int s : SPANS) {
            if (Math.abs(s - span) < Math.abs(best - span)) {
                best = s;
            }
        }
        return best;
    }

    /** The next span in {@link #SPANS}, wrapping. */
    public static int nextSpan(int span) {
        for (int i = 0; i < SPANS.length; i++) {
            if (SPANS[i] == span) {
                return SPANS[(i + 1) % SPANS.length];
            }
        }
        return SPANS[0];
    }

    /** Blocks per map pixel: 1, 2 or 4, which is also the server tile level of detail 0, 1 or 2. */
    public int blocksPerPixel() {
        return span / PANEL_PX;
    }

    public int lod() {
        return Integer.numberOfTrailingZeros(blocksPerPixel());
    }

    /** The world x of the west edge of panel column {@code col} in a {@code cols}-wide array. */
    public int panelMinX(int col, int cols) {
        return centreX - cols * span / 2 + col * span;
    }

    /** The world z of the north edge of panel row {@code row} (0 = top) in a {@code rows}-high array. */
    public int panelMinZ(int row, int rows) {
        return centreZ - rows * span / 2 + row * span;
    }
}
