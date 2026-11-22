package za.co.neroland.neromaps.store;

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * NeroMaps' section of Neroland Core's {@code /neroland data exportme} (Core 1.20.1), the read-side twin of
 * the {@code neromaps:players} eraser.
 *
 * <p>A readable summary of the player's own record, not the raw save: Core's combined export travels as
 * chat text and is capped, and it must never name another player. So explored areas are chunk counts per
 * dimension (not region bitsets), and shares are scopes and counts (never the other player's UUID).
 * {@code /neromaps export} still hands over the complete record, explored bitsets included. Reads the store
 * only, so offline players are covered. Writes and logs nothing; live position is never stored.</p>
 */
public final class CoreExport {

    private CoreExport() {
    }

    /** The live summary for {@code player}, or {@code null} when NeroMaps holds nothing about them. */
    @Nullable
    public static JsonObject export(MinecraftServer server, UUID player) {
        return summary(PlayerStore.get(server), player);
    }

    /** The pure core, for the plain-JVM tests. */
    @Nullable
    static JsonObject summary(MapPlayerState state, UUID player) {
        PlayerRecord r = state.find(player).orElse(null);
        int[] received = new int[1];
        int[] offered = new int[1];
        JsonArray receivedScopes = new JsonArray();
        state.forEach((other, rec) -> {
            if (other.equals(player)) {
                return;
            }
            for (Grant g : rec.grants()) {
                if (g.to().equals(player)) {
                    received[0]++;
                    receivedScopes.add(g.scope().kind().name());
                }
            }
            for (ShareRequest q : rec.incoming()) {
                if (q.from().equals(player)) {
                    offered[0]++;
                }
            }
        });
        boolean empty = (r == null || r.isEmpty()) && received[0] == 0 && offered[0] == 0;
        if (empty) {
            return null;
        }
        JsonObject out = new JsonObject();
        JsonArray waypoints = new JsonArray();
        JsonArray deaths = new JsonArray();
        JsonObject explored = new JsonObject();
        JsonArray given = new JsonArray();
        if (r != null) {
            for (Waypoint w : r.waypoints()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("set", w.set());
                o.addProperty("dimension", w.dimension());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("shared", w.shared());
                waypoints.add(o);
            }
            for (DeathPoint d : r.deaths()) {
                JsonObject o = new JsonObject();
                o.addProperty("dimension", d.dimension());
                o.addProperty("x", d.x());
                o.addProperty("y", d.y());
                o.addProperty("z", d.z());
                deaths.add(o);
            }
            for (String dim : r.explored().dimensions()) {
                explored.addProperty(dim, r.explored().chunkCount(dim));
            }
            for (Grant g : r.grants()) {
                JsonObject o = new JsonObject();
                o.addProperty("scope", g.scope().kind().name());
                o.addProperty("arg", g.scope().arg());
                given.add(o);
            }
            out.addProperty("deathWaypoints", r.deathWaypoints());
            out.addProperty("acceptShareRequests", r.acceptRequests());
            out.addProperty("shareRequestsWaitingForYou", r.incoming().size());
        }
        out.add("waypoints", waypoints);
        out.add("deathPoints", deaths);
        out.add("exploredChunksByDimension", explored);
        out.add("sharesYouGave", given);
        out.add("sharesGivenToYou", receivedScopes);
        out.addProperty("shareOffersYouMadeStillWaiting", offered[0]);
        out.addProperty("note", "Live position is never stored. /neromaps export gives the complete record, "
                + "including the explored-map bitsets.");
        return out;
    }
}
