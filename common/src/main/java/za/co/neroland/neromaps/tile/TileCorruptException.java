package za.co.neroland.neromaps.tile;

/**
 * The one exception a tile decode may throw: bad length, varint, palette, inflate error, hash mismatch, trailing
 * bytes or an unknown schema. Callers drop the tile and recapture it; never a crash, never an INFO line per tile.
 */
public final class TileCorruptException extends Exception {

    private static final long serialVersionUID = 1L;

    public TileCorruptException(String reason) {
        super(reason, null, false, false);
    }
}
