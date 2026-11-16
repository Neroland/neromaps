package za.co.neroland.neromaps.link;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neromaps.fog.Visibility;
import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * Live events of the {@code neromaps} link module, player-scoped through Core's event bus (the bridge's notify
 * contract). Topics: {@code waypoints}, {@code shares}, {@code deaths} and a coarse {@code tiles}. Payloads carry no
 * name, coordinate or free text: the app re-reads the section over the authenticated link. Only a share request and a
 * death point carry {@code _notify} copy, and that copy is fixed English text with a deep link. Changes are coalesced
 * per player and published once a second from the server tick; {@code tiles} at most once per
 * {@value #TILES_INTERVAL_SECONDS} seconds, only to players who read tiles recently, and only blocks they can see.
 * Server thread only.
 */
public final class MapsLinkEvents {

    public static final int TILES_INTERVAL_SECONDS = 30;
    public static final int MAX_BLOCKS_PER_EVENT = 64;
    public static final String DEEP_LINK = "nerolink://server/{server}/" + MapsLinkModule.MODULE_ID;

    private static final int WAYPOINTS = 1;
    private static final int SHARES = 2;
    private static final int SHARE_REQUEST = 4;
    private static final int DEATH = 8;

    private static final Map<UUID, Integer> PENDING = new LinkedHashMap<>();
    private static Consumer<LinkEvent> publisher = e -> NeroLinkRegistry.eventBus().publish(e);
    private static int seconds;

    private MapsLinkEvents() {
    }

    /** Tests: where events go. */
    public static void publishTo(Consumer<LinkEvent> sink) {
        publisher = sink;
    }

    public static void reset() {
        PENDING.clear();
        seconds = 0;
    }

    /** Drops anything queued for an erased player. */
    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    private static void mark(UUID player, int bits) {
        if (player != null) {
            PENDING.merge(player, bits, (a, b) -> a | b);
        }
    }

    public static void waypoints(UUID player) {
        mark(player, WAYPOINTS);
    }

    public static void shares(UUID player) {
        mark(player, SHARES);
    }

    public static void shareRequest(UUID recipient) {
        mark(recipient, SHARES | SHARE_REQUEST);
    }

    public static void death(UUID player) {
        mark(player, WAYPOINTS | DEATH);
    }

    /** Once a second from the server tick. */
    public static void second(MapPlayerState state, LinkTiles tiles, boolean publicMap, long nowMs) {
        if (!PENDING.isEmpty()) {
            List<Map.Entry<UUID, Integer>> batch = new ArrayList<>(PENDING.entrySet());
            PENDING.clear();
            for (Map.Entry<UUID, Integer> e : batch) {
                flush(e.getKey(), e.getValue());
            }
        }
        if (++seconds % TILES_INTERVAL_SECONDS == 0) {
            tiles(state, tiles, publicMap, nowMs);
        }
    }

    private static void flush(UUID player, int bits) {
        if ((bits & WAYPOINTS) != 0 && (bits & DEATH) == 0) {
            publish(player, "waypoints", new JsonObject());
        }
        if ((bits & DEATH) != 0) {
            JsonObject p = new JsonObject();
            p.add("_notify", notify("Death point marked", "A death point was added to your map."));
            publish(player, "deaths", p);
        }
        if ((bits & SHARES) != 0) {
            JsonObject p = new JsonObject();
            if ((bits & SHARE_REQUEST) != 0) {
                p.addProperty("event", "request");
                p.add("_notify", notify("Map share request",
                        "Someone offered to share map data with you. Open the map to answer."));
            }
            publish(player, "shares", p);
        }
    }

    static JsonObject notify(String title, String body) {
        JsonObject n = new JsonObject();
        n.addProperty("title", title);
        n.addProperty("body", body);
        n.addProperty("deepLink", DEEP_LINK);
        return n;
    }

    private static void publish(UUID player, String topic, JsonObject payload) {
        try {
            publisher.accept(LinkEvent.forPlayer(MapsLinkModule.MODULE_ID, topic, player, payload));
        } catch (RuntimeException | LinkageError e) {
            // a missing or failing bridge never breaks the tick; nothing is logged about the player
        }
    }

    /** Coarse tile events: changed LOD-0 blocks each recent app reader can see. */
    static void tiles(MapPlayerState state, LinkTiles tiles, boolean publicMap, long nowMs) {
        Map<String, long[]> changed = tiles.drainChangedBlocks();
        if (changed.isEmpty()) {
            return;
        }
        Set<UUID> viewers = tiles.recentViewers(nowMs);
        for (UUID viewer : viewers) {
            for (Map.Entry<String, long[]> e : changed.entrySet()) {
                Visibility v = Visibility.of(state, viewer, e.getKey(), publicMap);
                JsonArray blocks = new JsonArray();
                for (long key : e.getValue()) {
                    int bx = TileKey.tx(key);
                    int bz = TileKey.tz(key);
                    if (visible(v, bx, bz)) {
                        JsonArray b = new JsonArray(2);
                        b.add(bx);
                        b.add(bz);
                        blocks.add(b);
                        if (blocks.size() >= MAX_BLOCKS_PER_EVENT) {
                            break;
                        }
                    }
                }
                if (!blocks.isEmpty()) {
                    JsonObject p = new JsonObject();
                    p.addProperty("dim", e.getKey());
                    p.addProperty("lod", 0);
                    p.add("blocks", blocks);
                    publish(viewer, "tiles", p);
                }
            }
        }
    }

    private static boolean visible(Visibility v, int bx, int bz) {
        if (v.everything()) {
            return true;
        }
        for (int i = 0; i < LinkTiles.TILES; i++) {
            if (v.anyInTile(bx * LinkTiles.BLOCK + (i & 7), bz * LinkTiles.BLOCK + (i >> 3))) {
                return true;
            }
        }
        return false;
    }
}
