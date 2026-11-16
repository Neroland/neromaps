package za.co.neroland.neromaps.link;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neromaps.network.RateLimiter;
import za.co.neroland.neromaps.server.ServerHandlers;
import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.Grant;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.ShareRequest;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * The write side of the {@code neromaps} link module: waypoint add / edit / delete and share offer / accept /
 * decline / revoke, for the requesting player's own record only, validated exactly as in game (name filter, limit,
 * dimension, coordinate clamp, consent rules) and limited to {@value #PER_MINUTE} actions a minute per
 * player (offers also to 10 a minute). All of them touch only the store, so they work while the player is offline.
 * No teleport, no world change. Refusals are {@code VALIDATION} with a short reason. Server thread only.
 */
public final class MapsLinkActions implements LinkActionHandler {

    public static final int PER_MINUTE = 20;

    private final Supplier<@Nullable MapsLinkWorld> world;
    final RateLimiter actions = new RateLimiter(PER_MINUTE, 60_000L / PER_MINUTE);
    final RateLimiter offers = new RateLimiter(10, 6_000L);

    public MapsLinkActions(Supplier<@Nullable MapsLinkWorld> world) {
        this.world = world;
    }

    @Override
    public String moduleId() {
        return MapsLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return MapsLinkModule.ACTIONS;
    }

    @Override
    public boolean allowOffline(String actionId) {
        return true;
    }

    /** Forgets a player's rate windows (erasure, server stop). */
    public void forget(UUID player) {
        actions.forget(player);
        offers.forget(player);
    }

    public void reset() {
        actions.forgetAll();
        offers.forgetAll();
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        MapsLinkWorld w = world.get();
        if (w == null || playerId == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "not_running");
        }
        JsonObject p = params == null ? new JsonObject() : params;
        if (!actions.tryAcquire(playerId)) {
            return refuse("rate");
        }
        return switch (actionId == null ? "" : actionId) {
            case "waypoint_add" -> waypointAdd(w, playerId, p);
            case "waypoint_edit" -> waypointEdit(w, playerId, p);
            case "waypoint_delete" -> waypointDelete(w, playerId, p);
            case "share_offer" -> shareOffer(w, playerId, p);
            case "share_accept" -> shareAnswer(w, playerId, p, true);
            case "share_decline" -> shareAnswer(w, playerId, p, false);
            case "share_revoke" -> shareRevoke(w, playerId, p);
            default -> refuse("unknown_action");
        };
    }

    static LinkActionResult refuse(String reason) {
        return LinkActionResult.error(LinkActionResult.Error.VALIDATION, reason);
    }

    // --- waypoints --------------------------------------------------------------------------------------------

    /** Builds a waypoint from the body (id 0), or null with {@code why[0]} set. */
    static @Nullable Waypoint parse(MapsLinkWorld w, JsonObject p, int id, String[] why) {
        String name = str(p, "name");
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 32) {
            why[0] = "bad_name";
            return null;
        }
        String dim = str(p, "dim");
        boolean known = false;
        for (MapsLinkWorld.Dim d : w.dimensions()) {
            if (d.id().equals(dim)) {
                known = true;
                break;
            }
        }
        if (!known) {
            why[0] = "bad_dimension";
            return null;
        }
        Integer x = intOf(p, "x");
        Integer y = intOf(p, "y");
        Integer z = intOf(p, "z");
        if (x == null || y == null || z == null) {
            why[0] = "bad_position";
            return null;
        }
        Integer colour = intOf(p, "colour");
        int c = colour == null ? 0 : colour;
        if (c < 0 || c >= MapsLinkJson.COLOURS.length) {
            why[0] = "bad_colour";
            return null;
        }
        String glyph = str(p, "glyph");
        if (glyph != null && !MapsLinkJson.GLYPHS.contains(glyph)) {
            why[0] = "bad_glyph";
            return null;
        }
        Boolean beam = boolOf(p, "beam");
        int cx = clamp(x, ServerHandlers.MAX_COORD);
        int cy = clamp(y, ServerHandlers.MAX_Y);
        int cz = clamp(z, ServerHandlers.MAX_COORD);
        return new Waypoint(id, Waypoint.DEFAULT_SET, name, MapsLinkJson.COLOURS[c],
                MapsLinkJson.GLYPH_PREFIX + (glyph == null ? "dot" : glyph), dim, cx, cy, cz, false,
                beam == null || beam, true);
    }

    private static int clamp(int v, int max) {
        return Math.max(-max, Math.min(max, v));
    }

    static LinkActionResult result(MapPlayerState.Result r) {
        return switch (r) {
            case LIMIT -> refuse("limit");
            case NAME_EMPTY, NAME_DENIED -> refuse("bad_name");
            case NOT_FOUND -> refuse("not_found");
            default -> refuse("refused");
        };
    }

    private static LinkActionResult waypointAdd(MapsLinkWorld w, UUID me, JsonObject p) {
        String[] why = {"bad_request"};
        Waypoint wp = parse(w, p, 0, why);
        if (wp == null) {
            return refuse(why[0]);
        }
        int[] id = new int[1];
        MapPlayerState.Result r = w.state().addWaypoint(me, wp, w.waypointLimit(), w.denyList(), id);
        if (r != MapPlayerState.Result.OK) {
            return result(r);
        }
        w.changed(MapsLinkWorld.Change.WAYPOINTS, me);
        return ok("waypoint", own(w, me, id[0]));
    }

    private static LinkActionResult waypointEdit(MapsLinkWorld w, UUID me, JsonObject p) {
        int id = MapsLinkJson.waypointId(str(p, "id"));
        if (id < 0) {
            return refuse("not_found");
        }
        PlayerRecord rec = w.state().find(me).orElse(null);
        Waypoint old = rec == null ? null : rec.waypoint(id).orElse(null);
        if (old == null) {
            return refuse("not_found");
        }
        String[] why = {"bad_request"};
        Waypoint wp = parse(w, p, id, why);
        if (wp == null) {
            return refuse(why[0]);
        }
        // fields the app does not edit keep their in-game values
        wp = new Waypoint(id, old.set(), wp.name(), wp.colour(), wp.icon(), wp.dimension(), wp.x(), wp.y(), wp.z(),
                old.shared(), wp.beam(), old.distanceLabel());
        MapPlayerState.Result r = w.state().updateWaypoint(me, wp, w.denyList());
        if (r != MapPlayerState.Result.OK) {
            return result(r);
        }
        w.changed(MapsLinkWorld.Change.WAYPOINTS, recipientsAnd(w, me, id));
        return ok("waypoint", own(w, me, id));
    }

    private static UUID[] recipientsAnd(MapsLinkWorld w, UUID me, int waypointId) {
        List<UUID> out = new java.util.ArrayList<>();
        out.add(me);
        w.state().find(me).ifPresent(r -> {
            for (Grant g : r.grants()) {
                if (g.scope().waypointId() == waypointId) {
                    out.add(g.to());
                }
            }
        });
        return out.toArray(new UUID[0]);
    }

    private static LinkActionResult waypointDelete(MapsLinkWorld w, UUID me, JsonObject p) {
        String raw = str(p, "id");
        int death = MapsLinkJson.deathIndex(raw);
        if (death >= 0) {
            if (!w.state().removeDeath(me, death)) {
                return refuse("not_found");
            }
            w.changed(MapsLinkWorld.Change.WAYPOINTS, me);
            return deleted(raw);
        }
        int id = MapsLinkJson.waypointId(raw);
        if (id < 0) {
            return refuse("not_found");
        }
        UUID[] touched = recipientsAnd(w, me, id);
        MapPlayerState.Result r = w.state().removeWaypoint(me, id);
        if (r != MapPlayerState.Result.OK) {
            return result(r);
        }
        w.changed(MapsLinkWorld.Change.WAYPOINTS, touched);
        if (touched.length > 1) {
            w.changed(MapsLinkWorld.Change.SHARES, touched);
        }
        return deleted(raw);
    }

    private static LinkActionResult deleted(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("deleted", true);
        return LinkActionResult.ok(o);
    }

    private static JsonObject own(MapsLinkWorld w, UUID me, int id) {
        return w.state().find(me).flatMap(r -> r.waypoint(id)).map(MapsLinkJson::waypoint).orElseGet(JsonObject::new);
    }

    private static LinkActionResult ok(String key, JsonObject value) {
        JsonObject o = new JsonObject();
        o.add(key, value);
        return LinkActionResult.ok(o);
    }

    // --- sharing -------------------------------------------------------------------------------

    private LinkActionResult shareOffer(MapsLinkWorld w, UUID me, JsonObject p) {
        if (!offers.tryAcquire(me)) {
            return refuse("rate");
        }
        String name = str(p, "player");
        if (name == null || name.isBlank() || name.length() > 16) {
            return refuse("no_player");
        }
        UUID to = w.onlineByName(name);
        if (to == null) {
            return refuse("no_player");
        }
        ShareScope scope = scope(p);
        if (scope == null || (scope.kind() == ShareScope.Kind.EXPLORED_MAP && !ShareScope.ALL.equals(scope.arg())
                && !MapsLinkSnapshots.knownDimension(w, scope.arg()))) {
            return refuse("bad_scope"); // a map share names "*" or one of the server's dimensions, nothing else
        }
        long[] id = new long[1];
        ConsentLedger.Offer o = new ConsentLedger(w.state(), new java.security.SecureRandom()::nextLong)
                .offer(me, to, scope, w.today(), id);
        switch (o) {
            case SELF:
                return refuse("self");
            case ALREADY_SHARED:
                return refuse("already_shared");
            case NO_SUCH_WAYPOINT:
                return refuse("not_found");
            default:
                break;
        }
        if (id[0] != 0) {
            w.changed(MapsLinkWorld.Change.SHARE_REQUEST, to);
        }
        JsonObject out = new JsonObject();
        out.addProperty("sent", true);
        return LinkActionResult.ok(out);
    }

    static @Nullable ShareScope scope(JsonObject p) {
        String kind = str(p, "scope");
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "WAYPOINT": {
                int id = MapsLinkJson.waypointId(str(p, "waypointId"));
                return id < 0 ? null : ShareScope.waypoint(id);
            }
            case "EXPLORED_MAP": {
                String dim = str(p, "dim");
                return ShareScope.map(dim == null || dim.isBlank() ? ShareScope.ALL : Waypoint.cleanId(dim, ShareScope.ALL));
            }
            case "LIVE_POSITION":
                return ShareScope.livePosition();
            default:
                return null;
        }
    }

    private static LinkActionResult shareAnswer(MapsLinkWorld w, UUID me, JsonObject p, boolean accept) {
        long request = MapsLinkJson.requestId(str(p, "request"));
        if (request == 0) {
            return refuse("unknown_request");
        }
        ConsentLedger ledger = new ConsentLedger(w.state(), () -> 0L);
        // looked up in the caller's own incoming list only: nobody can answer for someone else (009)
        ShareRequest q = accept ? ledger.accept(me, request) : ledger.decline(me, request);
        if (q == null) {
            return refuse("unknown_request");
        }
        w.changed(MapsLinkWorld.Change.SHARES, me, q.from());
        JsonObject out = new JsonObject();
        if (accept) {
            out.add("grant", MapsLinkJson.grant(MapsLinkJson.grantId(q.from(), me, q.scope()), q.scope(),
                    w.nameOf(q.from()), w.state().find(q.from()).flatMap(r -> r.waypoint(q.scope().waypointId()))
                            .map(Waypoint::name).orElse(null)));
        } else {
            out.addProperty("declined", true);
        }
        return LinkActionResult.ok(out);
    }

    private static LinkActionResult shareRevoke(MapsLinkWorld w, UUID me, JsonObject p) {
        String grantId = str(p, "grantId");
        String direction = str(p, "direction");
        if (grantId == null || !("outgoing".equals(direction) || "incoming".equals(direction))) {
            return refuse("unknown_grant");
        }
        MapPlayerState state = w.state();
        UUID[] found = new UUID[2];
        ShareScope[] scope = new ShareScope[1];
        if ("outgoing".equals(direction)) {
            for (Grant g : state.find(me).map(PlayerRecord::grants).orElse(List.of())) {
                if (MapsLinkJson.grantId(me, g.to(), g.scope()).equals(grantId)) {
                    found[0] = me;
                    found[1] = g.to();
                    scope[0] = g.scope();
                    break;
                }
            }
        } else {
            state.forEach((sharer, r) -> {
                if (found[0] != null) {
                    return;
                }
                for (Grant g : r.grants()) {
                    if (g.to().equals(me) && MapsLinkJson.grantId(sharer, me, g.scope()).equals(grantId)) {
                        found[0] = sharer;
                        found[1] = me;
                        scope[0] = g.scope();
                        return;
                    }
                }
            });
        }
        if (found[0] == null || scope[0] == null) {
            return refuse("unknown_grant");
        }
        if (new ConsentLedger(state, () -> 0L).revokeGrant(found[0], found[1], scope[0]) == null) {
            return refuse("unknown_grant");
        }
        w.changed(MapsLinkWorld.Change.REVOKED, found[0], found[1]);
        JsonObject out = new JsonObject();
        out.addProperty("revoked", true);
        return LinkActionResult.ok(out);
    }

    // --- json helpers -----------------------------------------------------------------------------------------

    static @Nullable String str(JsonObject p, String key) {
        JsonElement e = p.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            return null;
        }
        String s = e.getAsString();
        return s.length() > 256 ? null : s;
    }

    static @Nullable Integer intOf(JsonObject p, String key) {
        JsonElement e = p.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double d = e.getAsDouble();
        if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 1e9) {
            return null;
        }
        return (int) Math.floor(d);
    }

    static @Nullable Boolean boolOf(JsonObject p, String key) {
        JsonElement e = p.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return e.getAsBoolean();
    }
}
