package za.co.neroland.neromaps.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapViewer;

import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.server.tiles.ServerTileStore;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileKey;

/** A server for the link module tests: a real store, a real {@link LinkTiles}, a hand-cranked worker. */
final class FakeWorld implements MapsLinkWorld {

    static final String OW = "minecraft:overworld";

    final MapPlayerState state = new MapPlayerState();
    final Map<Long, ServerTileStore.Encoded> stored = new HashMap<>();
    final ArrayDeque<Consumer<LinkTiles.Source>> jobs = new ArrayDeque<>();
    int submitted;
    final LinkTiles tiles = new LinkTiles((dim, job) -> {
        submitted++;
        jobs.add(job);
        return true;
    });
    final LinkTiles.Listener listener = tiles.listener(OW, java.nio.file.Path.of("fake", "ow"));
    final Map<UUID, Online> online = new HashMap<>();
    final List<String> changes = new ArrayList<>();
    boolean publicMap;
    boolean capture = true;
    long now = 1_000_000L;

    final LinkTiles.Source source = new LinkTiles.Source() {
        @Override
        public ServerTileStore.@Nullable Encoded encoded(int lod, int tx, int tz) {
            return stored.get(TileKey.tile(lod, tx, tz));
        }

        @Override
        public void forEachEtag(LinkTiles.KeyEtag sink) {
            stored.forEach((k, e) -> sink.accept(k, e.etag()));
        }
    };

    /** Stores a tile as the capture worker would and tells the link index. */
    void put(int lod, int tx, int tz, Tile t) {
        byte[] b = TileCodec.encode(t);
        long etag;
        try {
            etag = TileCodec.contentHash(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        long key = TileKey.tile(lod, tx, tz);
        ServerTileStore.Encoded before = stored.put(key, new ServerTileStore.Encoded(b, etag));
        if (lod == 0) {
            // what the capture store reports: each LOD-0 chunk whose pixels differ from what was stored
            Tile old = null;
            try {
                old = before == null ? null : TileCodec.decode(before.bytes());
            } catch (Exception e) {
                old = null;
            }
            for (int cz = 0; cz < 16; cz++) {
                for (int cx = 0; cx < 16; cx++) {
                    if (old == null || !sameChunk(old, t, cx, cz)) {
                        listener.chunkChanged(tx * 16 + cx, tz * 16 + cz);
                    }
                }
            }
        }
        listener.written(key, b, etag);
    }

    private static boolean sameChunk(Tile a, Tile b, int cx, int cz) {
        for (int z = cz * 16; z < cz * 16 + 16; z++) {
            for (int x = cx * 16; x < cx * 16 + 16; x++) {
                int i = Tile.index(x, z);
                if (a.present(i) != b.present(i)) {
                    return false;
                }
                if (a.present(i) && (a.flags[i] != b.flags[i] || a.height[i] != b.height[i] || a.depth[i] != b.depth[i]
                        || !a.blockId(a.block[i]).equals(b.blockId(b.block[i]))
                        || !a.biomeId(a.biome[i] & 0xFF).equals(b.biomeId(b.biome[i] & 0xFF)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Runs every queued worker job (and the ones they queue). */
    void runWorker() {
        Consumer<LinkTiles.Source> j;
        while ((j = jobs.poll()) != null) {
            j.accept(source);
        }
    }

    @Override
    public MapPlayerState state() {
        return state;
    }

    @Override
    public LinkTiles tiles() {
        return tiles;
    }

    @Override
    public boolean publicMap() {
        return publicMap;
    }

    @Override
    public boolean capture() {
        return capture;
    }

    @Override
    public int waypointLimit() {
        return 100;
    }

    @Override
    public String denyList() {
        return "rude";
    }

    @Override
    public List<Dim> dimensions() {
        return List.of(new Dim(OW, 63, false), new Dim("minecraft:the_nether", 32, true));
    }

    @Override
    public @Nullable Online online(UUID player) {
        return online.get(player);
    }

    @Override
    public @Nullable UUID onlineByName(String name) {
        for (Map.Entry<UUID, Online> e : online.entrySet()) {
            if (e.getValue().name().equals(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    @Override
    public String nameOf(UUID player) {
        Online o = online.get(player);
        return o != null ? o.name() : za.co.neroland.neromaps.server.MapServer.handle(player);
    }

    @Override
    public JsonArray layers(UUID viewer, String dim, int x0, int z0, int x1, int z1, boolean[] isOnline) {
        // the live seam's conversion, as that requester: online at their position, or offline with no position
        Online o = online.get(viewer);
        isOnline[0] = o != null;
        MapViewer v = o == null ? MapViewer.forTest(viewer, null, null, null)
                : MapViewer.forTest(viewer, null, key(o.dim()), BlockPos.containing(o.x(), o.y(), o.z()));
        return LinkLayers.build(v, key(dim), new MapBounds(x0, z0, x1, z1), k -> k);
    }

    static ResourceKey<Level> key(String dim) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
    }

    @Override
    public long nowMillis() {
        return now;
    }

    @Override
    public long today() {
        return now / 86_400_000L;
    }

    @Override
    public void ensureTiles() {
    }

    @Override
    public void changed(Change change, UUID... players) {
        changes.add(change.name() + ":" + players.length);
    }
}
