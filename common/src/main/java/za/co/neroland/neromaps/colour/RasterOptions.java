package za.co.neroland.neromaps.colour;

/** Draw-time options baked into a raster. Changing any of them means re-rasterising (bump {@link #version}). */
public final class RasterOptions {

    public MapStyle style = MapStyle.DAY_NIGHT;
    public boolean chunkLines;
    public boolean gridLines;
    public int seaLevel = 63;
    /** Bumped by the owner whenever a field changes. */
    public int version;

    public void set(MapStyle style, boolean chunkLines, boolean gridLines, int seaLevel) {
        if (style != this.style || chunkLines != this.chunkLines || gridLines != this.gridLines
                || seaLevel != this.seaLevel) {
            this.style = style;
            this.chunkLines = chunkLines;
            this.gridLines = gridLines;
            this.seaLevel = seaLevel;
            version++;
        }
    }
}
