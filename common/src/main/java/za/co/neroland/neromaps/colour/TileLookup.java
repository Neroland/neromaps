package za.co.neroland.neromaps.colour;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.tile.Tile;

/** Neighbour access for the rasteriser's hillshade across tile edges. */
@FunctionalInterface
public interface TileLookup {

    TileLookup NONE = (lod, tx, tz) -> null;

    @Nullable
    Tile get(int lod, int tx, int tz);
}
