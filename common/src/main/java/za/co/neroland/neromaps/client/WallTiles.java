package za.co.neroland.neromaps.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.client.texture.WorldMapTextures;
import za.co.neroland.neromaps.colour.MapStyle;
import za.co.neroland.neromaps.colour.RasterOptions;
import za.co.neroland.neromaps.colour.TileLookup;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * Server tiles for wall maps and map tables (client memory only, never the disk cache). Renderers say which tiles
 * they would draw; once a second the wanted, stale tiles are asked for through the ordinary {@code tile_request},
 * which the server answers through the fog-of-war read path: a tile the viewer may not see is never
 * answered, a partly visible one arrives masked. So a wall map shows each viewer only their own explored terrain.
 * Bounded: {@value #MAX_TILES} tiles, {@value #ASK_EVERY_MS} ms between asks for one tile, one request per tick.
 * Render thread only.
 */
public final class WallTiles {

    public static final int MAX_TILES = 48;
    public static final long ASK_EVERY_MS = 10_000L;

    private record Entry(Tile tile, long etag) { }

    private static final LinkedHashMap<String, Entry> TILES = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<String, Long> ASKED = new HashMap<>();
    private static final Map<String, Map<Integer, LongOpenHashSet>> WANTED = new HashMap<>();
    private static final RasterOptions RASTER = new RasterOptions();
    private static @Nullable WorldMapTextures textures;
    private static int ticks;

    static {
        RASTER.set(MapStyle.DAY_NIGHT, false, false, 63);
    }

    private WallTiles() {
    }

    private static String key(String dim, long tileKey) {
        return dim + '|' + tileKey;
    }

    public static @Nullable Tile get(String dim, int lod, int tx, int tz) {
        Entry e = TILES.get(key(dim, TileKey.tile(lod, tx, tz)));
        return e == null ? null : e.tile;
    }

    /** A renderer would draw this tile. */
    public static void want(String dim, int lod, int tx, int tz) {
        WANTED.computeIfAbsent(dim, k -> new HashMap<>()).computeIfAbsent(lod, k -> new LongOpenHashSet())
                .add(TileKey.tile(lod, tx, tz));
    }

    /** The neighbour lookup the rasteriser uses for edge hillshade. */
    public static TileLookup lookup(String dim) {
        return (lod, tx, tz) -> get(dim, lod, tx, tz);
    }

    public static RasterOptions raster() {
        return RASTER;
    }

    public static WorldMapTextures textures() {
        WorldMapTextures t = textures;
        if (t == null) {
            t = new WorldMapTextures(16);
            textures = t;
        }
        return t;
    }

    /** Every tile the server sends lands here too (and in the world map's shared cache). */
    public static void accept(Payloads.TileData p) {
        long tk = TileKey.tile(p.lod(), p.tx(), p.tz());
        String k = key(p.dimension(), tk);
        if (p.data().length == 0) {
            TILES.get(k); // unchanged: touch it
            return;
        }
        try {
            TILES.put(k, new Entry(TileCodec.decode(p.data()), p.etag()));
            while (TILES.size() > MAX_TILES) {
                TILES.remove(TILES.keySet().iterator().next());
            }
        } catch (TileCorruptException e) {
            // a malformed tile is dropped
        }
    }

    /** Client tick: one request per second for wanted tiles not asked for recently. */
    public static void tick() {
        if (++ticks % 20 != 0) {
            WorldMapTextures t = textures;
            if (t != null) {
                t.beginFrame();
            }
            return;
        }
        if (WANTED.isEmpty() || !ClientState.INSTANCE.serverHasMod()) {
            WANTED.clear();
            return;
        }
        long now = System.currentTimeMillis();
        outer:
        for (Map.Entry<String, Map<Integer, LongOpenHashSet>> d : WANTED.entrySet()) {
            for (Map.Entry<Integer, LongOpenHashSet> l : d.getValue().entrySet()) {
                long[] keys = new long[Payloads.MAX_TILE_REQUEST];
                long[] etags = new long[keys.length];
                int n = 0;
                for (long tk : l.getValue()) {
                    String k = key(d.getKey(), tk);
                    Long asked = ASKED.get(k);
                    if (asked != null && now - asked < ASK_EVERY_MS) {
                        continue;
                    }
                    ASKED.put(k, now);
                    Entry e = TILES.get(k);
                    keys[n] = tk;
                    etags[n++] = e == null ? 0L : e.etag;
                    if (n == keys.length) {
                        break;
                    }
                }
                if (n > 0) {
                    Services.NETWORK.sendToServer(new Payloads.TileRequest(d.getKey(), l.getKey(),
                            java.util.Arrays.copyOf(keys, n), java.util.Arrays.copyOf(etags, n)));
                    break outer; // one request per second keeps well inside the server's tile limiter
                }
            }
        }
        WANTED.clear();
        if (ASKED.size() > 512) {
            ASKED.values().removeIf(t -> now - t > ASK_EVERY_MS);
        }
    }

    /** Leaving a server: everything goes. */
    public static void clear() {
        TILES.clear();
        ASKED.clear();
        WANTED.clear();
        WorldMapTextures t = textures;
        if (t != null) {
            t.close();
            textures = null;
        }
    }
}
