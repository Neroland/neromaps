package za.co.neroland.neromaps.link;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.neromaps.fog.Visibility;
import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.Grant;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.ShareRequest;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * The read side of the {@code neromaps} link module, schema 1 (the contract the companion app implements). Every
 * section is scoped to the requesting player's UUID: their own record, what others explicitly shared with them, and
 * terrain inside their fog of war (or everything on a public-map server). Runs on the server thread and never encodes,
 * compresses or reads files (tiles come pre-encoded from the worker, see {@link LinkTiles}). With empty params (a
 * WebSocket subscribe) every section answers a small valid document.
 */
public final class MapsLinkSnapshots implements LinkSnapshotProvider {

    public static final int V = 1;

    private final Supplier<@Nullable MapsLinkWorld> world;
    private final Supplier<@Nullable MapsPalette> palette;

    public MapsLinkSnapshots(Supplier<@Nullable MapsLinkWorld> world, Supplier<@Nullable MapsPalette> palette) {
        this.world = world;
        this.palette = palette;
    }

    @Override
    public String moduleId() {
        return MapsLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return MapsLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return MapsLinkModule.SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        MapsLinkWorld w = world.get();
        Map<String, String> p = params == null ? Map.of() : params;
        if (w == null || playerId == null) {
            JsonObject o = new JsonObject();
            o.addProperty("v", V);
            o.addProperty("offline", true);
            return o;
        }
        return switch (section == null ? "" : section) {
            case "dimensions" -> dimensions(w, playerId);
            case "tiles" -> tiles(w, playerId, p);
            case "palette" -> palette(p);
            case "waypoints" -> waypoints(w, playerId);
            case "layers" -> layers(w, playerId, p);
            case "position" -> position(w, playerId);
            case "shares" -> shares(w, playerId);
            default -> {
                JsonObject o = new JsonObject();
                o.addProperty("v", V);
                o.addProperty("error", "unknown_section");
                yield o;
            }
        };
    }

    private static JsonObject head(MapsLinkWorld w) {
        JsonObject o = new JsonObject();
        o.addProperty("v", V);
        o.addProperty("asOf", w.nowMillis());
        return o;
    }

    // --- dimensions -------------------------------------------------------------------------------------------

    static JsonObject dimensions(MapsLinkWorld w, UUID me) {
        MapPlayerState state = w.state();
        JsonObject o = head(w);
        o.addProperty("recordEpoch", state.find(me).map(PlayerRecord::epoch).orElse("0"));
        JsonObject policy = new JsonObject();
        policy.addProperty("capture", w.capture());
        policy.addProperty("publicMap", w.publicMap());
        policy.addProperty("maxWaypoints", w.waypointLimit());
        policy.addProperty("liveActions", true);
        o.add("policy", policy);
        JsonArray dims = new JsonArray();
        ConsentLedger ledger = new ConsentLedger(state, () -> 0L);
        for (MapsLinkWorld.Dim d : w.dimensions()) {
            int[] chunks = null; // {minCx, minCz, maxCx, maxCz} of what the requester may see
            if (w.publicMap()) {
                int[] known = w.tiles().knownExtent(d.id());
                if (known != null) {
                    chunks = new int[] {known[0] << 7, known[1] << 7, (known[2] << 7) + 127, (known[3] << 7) + 127};
                } else {
                    int[][] acc = {null};
                    state.forEach((id, r) -> acc[0] = union(acc[0], r.explored().extent(d.id())));
                    chunks = acc[0];
                }
            } else {
                chunks = union(chunks, state.find(me).map(r -> r.explored().extent(d.id())).orElse(null));
                for (UUID sharer : ledger.mapSharersFor(me, d.id())) {
                    chunks = union(chunks, state.find(sharer).map(r -> r.explored().extent(d.id())).orElse(null));
                }
            }
            if (chunks == null) {
                continue; // nothing visible there: the dimension is not listed
            }
            JsonObject e = new JsonObject();
            e.addProperty("id", d.id());
            e.addProperty("name", displayName(d.id()));
            e.addProperty("sky", d.id());
            e.addProperty("seaLevel", d.seaLevel());
            e.addProperty("roofed", d.roofed());
            JsonObject ext = new JsonObject();
            ext.addProperty("minBx", chunks[0] >> 7);
            ext.addProperty("minBz", chunks[1] >> 7);
            ext.addProperty("maxBx", chunks[2] >> 7);
            ext.addProperty("maxBz", chunks[3] >> 7);
            e.add("extent", ext);
            dims.add(e);
        }
        o.add("dimensions", dims);
        return o;
    }

    private static int @Nullable [] union(int @Nullable [] a, int @Nullable [] b) {
        if (b == null) {
            return a;
        }
        if (a == null) {
            return b.clone();
        }
        return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    static String displayName(String id) {
        switch (id) {
            case "minecraft:overworld":
                return "Overworld";
            case "minecraft:the_nether":
                return "The Nether";
            case "minecraft:the_end":
                return "The End";
            default:
                break;
        }
        String path = id.substring(id.indexOf(':') + 1).replace('/', ' ').replace('_', ' ');
        StringBuilder b = new StringBuilder(path.length());
        boolean up = true;
        for (char c : path.toCharArray()) {
            b.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return b.toString();
    }

    // --- tiles ------------------------------------------------------------------------------------------------

    static JsonObject tiles(MapsLinkWorld w, UUID me, Map<String, String> p) {
        String dimRaw = p.get("dim");
        OptionalInt lod = intParam(p, "lod", 0, LinkTiles.MAX_LOD);
        OptionalInt bx = intParam(p, "bx", -1_000_000, 1_000_000);
        OptionalInt bz = intParam(p, "bz", -1_000_000, 1_000_000);
        String since = p.get("since");
        String want = p.get("want");
        String dim = dimRaw == null ? null : Waypoint.cleanId(dimRaw, "");
        if (dim == null || dim.isEmpty() || lod.isEmpty() || bx.isEmpty() || bz.isEmpty()
                || (since == null) == (want == null) || !knownDimension(w, dim)
                || !LinkTiles.blockInWorld(lod.getAsInt(), bx.getAsInt(), bz.getAsInt())) {
            JsonObject o = new JsonObject();
            o.addProperty("v", V);
            o.addProperty("needParams", true);
            return o;
        }
        if (!w.capture()) {
            // nothing is captured for the app: every tile is "missing"
            return want != null ? emptyPage() : emptyIndex(w, since);
        }
        w.ensureTiles();
        Visibility v = Visibility.of(w.state(), me, dim, w.publicMap());
        if (since != null) {
            if (since.length() > 64) {
                since = "0";
            }
            return w.tiles().index(dim, lod.getAsInt(), bx.getAsInt(), bz.getAsInt(), since, v);
        }
        java.util.OptionalLong mask = LinkTiles.parseMask(want);
        if (mask.isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("v", V);
            o.addProperty("needParams", true);
            return o;
        }
        return w.tiles().page(me, dim, lod.getAsInt(), bx.getAsInt(), bz.getAsInt(), mask.getAsLong(), v,
                w.nowMillis());
    }

    static boolean knownDimension(MapsLinkWorld w, String dim) {
        for (MapsLinkWorld.Dim d : w.dimensions()) {
            if (d.id().equals(dim)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject emptyPage() {
        JsonObject o = new JsonObject();
        o.add("tiles", new JsonObject());
        o.addProperty("pending", LinkTiles.hex(0));
        o.addProperty("next", LinkTiles.hex(0));
        o.addProperty("retryMs", 0);
        return o;
    }

    private static JsonObject emptyIndex(MapsLinkWorld w, @Nullable String since) {
        JsonObject o = new JsonObject();
        o.addProperty("cursor", w.tiles().epoch() + ".0.0");
        JsonArray a = new JsonArray(LinkTiles.TILES);
        for (int i = 0; i < LinkTiles.TILES; i++) {
            a.add(com.google.gson.JsonNull.INSTANCE);
        }
        o.add("etags", a);
        o.addProperty("changed", LinkTiles.hex(0));
        return o;
    }

    static OptionalInt intParam(Map<String, String> p, String key, int min, int max) {
        String s = p.get(key);
        if (s == null || s.isEmpty() || s.length() > 11) {
            return OptionalInt.empty();
        }
        try {
            int v = Integer.parseInt(s);
            return v < min || v > max ? OptionalInt.empty() : OptionalInt.of(v);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    // --- palette ----------------------------------------------------------------------------------------------

    JsonObject palette(Map<String, String> p) {
        MapsPalette pal = palette.get();
        if (pal == null) {
            // still being built off the thread after a server start: the pending shape, never an error
            JsonObject o = new JsonObject();
            o.addProperty("v", "");
            o.addProperty("pending", true);
            o.addProperty("retryMs", LinkTiles.RETRY_MS);
            return o;
        }
        String held = p.get("v");
        if (held != null && held.equals(pal.etag())) {
            JsonObject o = new JsonObject();
            o.addProperty("v", pal.etag());
            o.addProperty("same", true);
            return o;
        }
        return pal.document();
    }

    // --- waypoints --------------------------------------------------------------------------------------------

    static JsonObject waypoints(MapsLinkWorld w, UUID me) {
        JsonObject o = head(w);
        o.addProperty("limit", w.waypointLimit());
        JsonArray arr = new JsonArray();
        PlayerRecord r = w.state().find(me).orElse(null);
        if (r != null) {
            for (Waypoint wp : r.waypoints()) {
                arr.add(MapsLinkJson.waypoint(wp));
            }
            List<DeathPoint> deaths = r.deaths();
            for (int i = 0; i < deaths.size(); i++) {
                arr.add(MapsLinkJson.death(i, deaths.get(i)));
            }
        }
        o.add("waypoints", arr);
        return o;
    }

    // --- layers -----------------------------------------------------------------------------------------------

    static JsonObject layers(MapsLinkWorld w, UUID me, Map<String, String> p) {
        JsonObject o = head(w);
        String dimRaw = p.get("dim");
        OptionalInt x0 = intParam(p, "x0", -30_000_000, 30_000_000);
        OptionalInt z0 = intParam(p, "z0", -30_000_000, 30_000_000);
        OptionalInt x1 = intParam(p, "x1", -30_000_000, 30_000_000);
        OptionalInt z1 = intParam(p, "z1", -30_000_000, 30_000_000);
        String dim = dimRaw == null ? null : Waypoint.cleanId(dimRaw, "");
        if (dim == null || dim.isEmpty() || x0.isEmpty() || z0.isEmpty() || x1.isEmpty() || z1.isEmpty()
                || !knownDimension(w, dim)) {
            o.addProperty("online", w.online(me) != null);
            o.add("layers", new JsonArray());
            o.addProperty("needParams", true);
            return o;
        }
        boolean[] online = {false};
        JsonArray layers = w.layers(me, dim, x0.getAsInt(), z0.getAsInt(), x1.getAsInt(), z1.getAsInt(), online);
        o.addProperty("online", online[0]);
        o.add("layers", layers);
        return o;
    }

    // --- position ---------------------------------------------------------------------------------------------

    static JsonObject position(MapsLinkWorld w, UUID me) {
        JsonObject o = head(w);
        MapsLinkWorld.Online self = w.online(me);
        if (self == null) {
            o.add("self", com.google.gson.JsonNull.INSTANCE);
        } else {
            JsonObject s = new JsonObject();
            s.addProperty("dim", self.dim());
            s.addProperty("x", round(self.x()));
            s.addProperty("y", round(self.y()));
            s.addProperty("z", round(self.z()));
            s.addProperty("yaw", Math.round(self.yaw()));
            o.add("self", s);
        }
        JsonArray shared = new JsonArray();
        if (self != null) {
            // positions flow only to an online viewer, as in game (009: "while both are online")
            for (UUID sharer : new ConsentLedger(w.state(), () -> 0L).positionSharersFor(me)) {
                MapsLinkWorld.Online on = w.online(sharer);
                if (on == null || !on.shareable()) {
                    continue; // offline, sneaking, invisible or spectating: nothing at all
                }
                JsonObject e = new JsonObject();
                e.addProperty("name", on.name());
                e.addProperty("dim", on.dim());
                if (on.dim().equals(self.dim())) {
                    e.addProperty("x", Math.floor(on.x()));
                    e.addProperty("y", Math.floor(on.y()));
                    e.addProperty("z", Math.floor(on.z()));
                }
                shared.add(e);
            }
        }
        o.add("shared", shared);
        return o;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // --- shares -----------------------------------------------------------------------------------------------

    static JsonObject shares(MapsLinkWorld w, UUID me) {
        JsonObject o = head(w);
        MapPlayerState state = w.state();
        JsonArray out = new JsonArray();
        JsonArray in = new JsonArray();
        JsonArray requests = new JsonArray();
        PlayerRecord mine = state.find(me).orElse(null);
        if (mine != null) {
            for (Grant g : mine.grants()) {
                out.add(MapsLinkJson.grant(MapsLinkJson.grantId(me, g.to(), g.scope()), g.scope(), w.nameOf(g.to()),
                        mine.waypoint(g.scope().waypointId()).map(Waypoint::name).orElse(null)));
            }
            for (ShareRequest q : mine.incoming()) {
                JsonObject e = new JsonObject();
                e.addProperty("requestId", MapsLinkJson.requestId(q.requestId()));
                e.addProperty("scope", q.scope().kind().name());
                if (q.scope().kind() == ShareScope.Kind.EXPLORED_MAP) {
                    MapsLinkJson.dim(e, q.scope());
                }
                e.addProperty("player", w.nameOf(q.from()));
                e.addProperty("at", q.day() * 86_400_000L);
                requests.add(e);
            }
        }
        state.forEach((sharer, r) -> {
            for (Grant g : r.grants()) {
                if (g.to().equals(me)) {
                    in.add(MapsLinkJson.grant(MapsLinkJson.grantId(sharer, me, g.scope()), g.scope(), w.nameOf(sharer),
                            r.waypoint(g.scope().waypointId()).map(Waypoint::name).orElse(null)));
                }
            }
        });
        o.add("outgoing", out);
        o.add("incoming", in);
        o.add("requests", requests);
        return o;
    }
}
