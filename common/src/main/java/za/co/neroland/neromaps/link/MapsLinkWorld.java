package za.co.neroland.neromaps.link;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.ShareScope;

/**
 * Everything the {@code neromaps} link module reads or changes on the running server, behind one seam so the
 * snapshots and actions are plain Java over the store (tests drive a fake). Server thread only.
 */
public interface MapsLinkWorld {

    /** One server level the app may be told about. */
    record Dim(String id, int seaLevel, boolean roofed) { }

    /** An online player as the module may use them: the name is only ever shown to a consenting counterparty. */
    record Online(String name, String dim, double x, double y, double z, float yaw, boolean shareable) { }

    /** What changed, so the live world can resync in-game clients and publish link events. */
    enum Change { WAYPOINTS, SHARES, SHARE_REQUEST, REVOKED }

    MapPlayerState state();

    LinkTiles tiles();

    boolean publicMap();

    boolean capture();

    int waypointLimit();

    String denyList();

    List<Dim> dimensions();

    @Nullable Online online(UUID player);

    /** An online player by exact name (offers go to online players only, as in game). */
    @Nullable UUID onlineByName(String name);

    /** A counterparty's display name: their name while online, else a short non-UUID handle. */
    String nameOf(UUID player);

    /** Layer entries for {@code viewer} in bounds, from Core's map API, as that viewer; {@code online[0]} is set. */
    JsonArray layers(UUID viewer, String dim, int x0, int z0, int x1, int z1, boolean[] online);

    long nowMillis();

    long today();

    /** Starts the capture worker if it is not running (tile reads may come before any capture). */
    void ensureTiles();

    /** After a change made through the link: resync the players' clients and publish the matching link events. */
    void changed(Change change, UUID... players);

    /** A share {@code sharer} offered was accepted through the link (records the sharer's map milestone). */
    default void shareAccepted(UUID sharer, ShareScope scope) {
    }
}
