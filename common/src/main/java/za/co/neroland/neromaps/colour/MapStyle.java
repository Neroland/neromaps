package za.co.neroland.neromaps.colour;

/** The map colour styles. All are draw-time transforms over one tile. */
public enum MapStyle {
    /** Base x biome tint x hillshade x water steps; night darkening is a vertex colour, not re-rasterised. */
    DAY_NIGHT,
    /** Flat biome colour, half hillshade, darker biome borders. */
    BIOME,
    /** Hypsometric ramp by height, 4-block bands, contour every 16 blocks. */
    HEIGHT,
    /** Base x biome tint only. */
    FLAT,
    /** Four neutral height bands, solid water with a white shoreline (accessibility). */
    HIGH_CONTRAST;

    public static MapStyle parse(String s, MapStyle fallback) {
        for (MapStyle v : values()) {
            if (v.name().equalsIgnoreCase(s)) {
                return v;
            }
        }
        return fallback;
    }
}
