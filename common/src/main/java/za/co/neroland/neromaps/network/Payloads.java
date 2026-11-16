package za.co.neroland.neromaps.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * Every {@code neromaps:main} payload. Serverbound payloads are <b>intents</b>: the server validates each against the
 * sender (their own records only), the policy and a per-player rate limit. Clientbound payloads go only to the player
 * they describe and carry nothing that player may not see: their own waypoints and shares, names of players they
 * share with, positions only under a live mutual grant, layer items scoped by the providers, and terrain inside their
 * fog of war.
 */
public final class Payloads {

    public static final int MAX_WAYPOINTS = 4096;
    public static final int MAX_SHARE_LINES = 256;
    public static final int MAX_POSITIONS = 64;
    public static final int MAX_LAYERS = 256;
    public static final int MAX_LAYER_ITEMS = 2048;
    public static final int MAX_TILE_BYTES = 1 << 20;
    public static final int MAX_TILE_REQUEST = 32;

    private Payloads() {
    }

    // ============================================================================================ shared

    static void writeWaypoint(FriendlyByteBuf b, Waypoint w) {
        b.writeVarInt(w.id());
        Wire.string(b, w.set());
        Wire.string(b, w.name());
        b.writeInt(w.colour());
        Wire.string(b, w.icon());
        Wire.string(b, w.dimension());
        b.writeVarInt(w.x());
        b.writeVarInt(w.y());
        b.writeVarInt(w.z());
        b.writeByte((w.shared() ? 1 : 0) | (w.beam() ? 2 : 0) | (w.distanceLabel() ? 4 : 0));
    }

    static Waypoint readWaypoint(FriendlyByteBuf b) {
        int id = b.readVarInt();
        String set = Wire.string(b);
        String name = Wire.string(b);
        int colour = b.readInt();
        String icon = Wire.string(b);
        String dim = Wire.string(b);
        int x = b.readVarInt();
        int y = b.readVarInt();
        int z = b.readVarInt();
        int f = b.readByte();
        return new Waypoint(id, set, name, colour, icon, dim, x, y, z, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0);
    }

    static void death(FriendlyByteBuf b, DeathPoint d) {
        Wire.string(b, d.dimension());
        b.writeVarInt(d.x());
        b.writeVarInt(d.y());
        b.writeVarInt(d.z());
    }

    static DeathPoint death(FriendlyByteBuf b) {
        return new DeathPoint(Wire.string(b), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    static void writeScope(FriendlyByteBuf b, ShareScope s) {
        b.writeByte(s.kind().ordinal());
        Wire.string(b, s.arg());
    }

    static ShareScope readScope(FriendlyByteBuf b) {
        return new ShareScope(ShareScope.Kind.byOrdinal(b.readByte()), Wire.string(b));
    }

    // ============================================================================================ S2C

    /** Sent at login, on dimension change and after {@code /neromaps reload}. */
    public record Hello(String worldId, PolicySnapshot policy, int protocol) implements CustomPacketPayload {
        public static final int PROTOCOL = 1;
        public static final Type<Hello> TYPE = Wire.type("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.worldId);
                    b.writeVarInt(p.protocol);
                    b.writeVarInt(p.policy.revision());
                    b.writeVarInt(p.policy.flags());
                    b.writeVarInt(p.policy.waypointLimit());
                    b.writeVarInt(p.policy.exploreRadiusChunks());
                },
                b -> {
                    String world = Wire.string(b);
                    int protocol = b.readVarInt();
                    int rev = b.readVarInt();
                    int flags = b.readVarInt();
                    int limit = b.readVarInt();
                    int radius = b.readVarInt();
                    return new Hello(world, PolicySnapshot.of(rev, flags, limit, radius), protocol);
                });

        @Override
        public Type<Hello> type() {
            return TYPE;
        }
    }

    /** A waypoint someone shared with this player (the sharer's name is known to them: they accepted it). */
    public record SharedWaypoint(String sharerName, Waypoint waypoint) {
        static void write(FriendlyByteBuf b, SharedWaypoint s) {
            Wire.string(b, s.sharerName);
            writeWaypoint(b, s.waypoint);
        }

        static SharedWaypoint read(FriendlyByteBuf b) {
            return new SharedWaypoint(Wire.string(b), readWaypoint(b));
        }
    }

    /** The player's full waypoint view: their own, their death points and waypoints shared with them. */
    public record Waypoints(List<Waypoint> own, List<DeathPoint> deaths, List<SharedWaypoint> shared)
            implements CustomPacketPayload {
        public static final Type<Waypoints> TYPE = Wire.type("waypoints");
        public static final StreamCodec<FriendlyByteBuf, Waypoints> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.list(b, p.own, MAX_WAYPOINTS, Payloads::writeWaypoint);
                    Wire.list(b, p.deaths, DeathPoint.KEEP, Payloads::death);
                    Wire.list(b, p.shared, MAX_WAYPOINTS, SharedWaypoint::write);
                },
                b -> new Waypoints(Wire.list(b, MAX_WAYPOINTS, Payloads::readWaypoint),
                        Wire.list(b, DeathPoint.KEEP, Payloads::death),
                        Wire.list(b, MAX_WAYPOINTS, SharedWaypoint::read)));

        @Override
        public Type<Waypoints> type() {
            return TYPE;
        }
    }

    /** One share line: the other party's name and what is shared. {@code requestId} is set on pending lines only. */
    public record ShareLine(String name, ShareScope scope, long requestId) {
        static void write(FriendlyByteBuf b, ShareLine l) {
            Wire.string(b, l.name);
            writeScope(b, l.scope);
            b.writeLong(l.requestId);
        }

        static ShareLine read(FriendlyByteBuf b) {
            return new ShareLine(Wire.string(b), readScope(b), b.readLong());
        }
    }

    /** The player's consent state: what they share, what is shared with them, what waits, and their toggles. */
    public record Shares(List<ShareLine> outgoing, List<ShareLine> incoming, List<ShareLine> pending,
            boolean deathWaypoints, boolean acceptRequests) implements CustomPacketPayload {
        public static final Type<Shares> TYPE = Wire.type("shares");
        public static final StreamCodec<FriendlyByteBuf, Shares> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.list(b, p.outgoing, MAX_SHARE_LINES, ShareLine::write);
                    Wire.list(b, p.incoming, MAX_SHARE_LINES, ShareLine::write);
                    Wire.list(b, p.pending, MAX_SHARE_LINES, ShareLine::write);
                    b.writeBoolean(p.deathWaypoints);
                    b.writeBoolean(p.acceptRequests);
                },
                b -> new Shares(Wire.list(b, MAX_SHARE_LINES, ShareLine::read),
                        Wire.list(b, MAX_SHARE_LINES, ShareLine::read), Wire.list(b, MAX_SHARE_LINES, ShareLine::read),
                        b.readBoolean(), b.readBoolean()));

        @Override
        public Type<Shares> type() {
            return TYPE;
        }
    }

    /** Live positions under accepted grants (at most once a second). An entry without a position is "elsewhere". */
    public record Position(String name, String dimension, boolean known, int x, int z) {
        static void write(FriendlyByteBuf b, Position p) {
            Wire.string(b, p.name);
            Wire.string(b, p.dimension);
            b.writeBoolean(p.known);
            if (p.known) {
                b.writeVarInt(p.x);
                b.writeVarInt(p.z);
            }
        }

        static Position read(FriendlyByteBuf b) {
            String name = Wire.string(b);
            String dim = Wire.string(b);
            boolean known = b.readBoolean();
            return known ? new Position(name, dim, true, b.readVarInt(), b.readVarInt())
                    : new Position(name, dim, false, 0, 0);
        }
    }

    public record Positions(List<Position> positions) implements CustomPacketPayload {
        public static final Type<Positions> TYPE = Wire.type("positions");
        public static final StreamCodec<FriendlyByteBuf, Positions> STREAM_CODEC = StreamCodec.of(
                (b, p) -> Wire.list(b, p.positions, MAX_POSITIONS, Position::write),
                b -> new Positions(Wire.list(b, MAX_POSITIONS, Position::read)));

        @Override
        public Type<Positions> type() {
            return TYPE;
        }
    }

    /** A chat-worthy event: a lang key with string arguments; {@code requestId} adds accept/decline links. */
    public record Notice(String key, List<String> args, long requestId) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = Wire.type("notice");
        public static final StreamCodec<FriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.key);
                    Wire.strings(b, p.args, 8);
                    b.writeLong(p.requestId);
                },
                b -> new Notice(Wire.string(b), Wire.strings(b, 8), b.readLong()));

        public static Notice of(String key, String... args) {
            return new Notice(key, List.of(args), 0L);
        }

        @Override
        public Type<Notice> type() {
            return TYPE;
        }
    }

    /** The layers this server offers (one entry per registered layer of a present mod). */
    public record LayerInfo(String id, String titleKey, String icon, boolean defaultVisible, boolean defaultOnMinimap) {
        static void write(FriendlyByteBuf b, LayerInfo l) {
            Wire.string(b, l.id);
            Wire.string(b, l.titleKey);
            Wire.string(b, l.icon);
            b.writeByte((l.defaultVisible ? 1 : 0) | (l.defaultOnMinimap ? 2 : 0));
        }

        static LayerInfo read(FriendlyByteBuf b) {
            String id = Wire.string(b);
            String key = Wire.string(b);
            String icon = Wire.string(b);
            int f = b.readByte();
            return new LayerInfo(id, key, icon, (f & 1) != 0, (f & 2) != 0);
        }
    }

    public record LayerCatalogue(List<LayerInfo> layers) implements CustomPacketPayload {
        public static final Type<LayerCatalogue> TYPE = Wire.type("layer_catalogue");
        public static final StreamCodec<FriendlyByteBuf, LayerCatalogue> STREAM_CODEC = StreamCodec.of(
                (b, p) -> Wire.list(b, p.layers, MAX_LAYERS, LayerInfo::write),
                b -> new LayerCatalogue(Wire.list(b, MAX_LAYERS, LayerInfo::read)));

        @Override
        public Type<LayerCatalogue> type() {
            return TYPE;
        }
    }

    /**
     * One map item as the client draws it: a marker (point) or an area outline. {@code shape} 0 marker, 1 rect
     * {x0,z0,x1,z1}, 2 circle {cx,cz,r}, 3 chunk list {packed pairs}, 4 polygon {x,z,...}. Labels are already resolved
     * to a lang key (or literal) with arguments.
     */
    public record LayerItem(String layer, String id, int shape, int x, int y, int z, String icon, String label,
            boolean literal, List<String> args, int argb, int fill, int border, int[] coords, String badge) {
        static void write(FriendlyByteBuf b, LayerItem i) {
            Wire.string(b, i.layer);
            Wire.string(b, i.id);
            b.writeByte(i.shape);
            b.writeVarInt(i.x);
            b.writeVarInt(i.y);
            b.writeVarInt(i.z);
            Wire.string(b, i.icon);
            Wire.string(b, i.label);
            b.writeBoolean(i.literal);
            Wire.strings(b, i.args, 4);
            b.writeInt(i.argb);
            b.writeInt(i.fill);
            b.writeInt(i.border);
            int n = Math.min(i.coords.length, 2048);
            b.writeVarInt(n);
            for (int k = 0; k < n; k++) {
                b.writeVarInt(i.coords[k]);
            }
            Wire.string(b, i.badge);
        }

        static LayerItem read(FriendlyByteBuf b) {
            String layer = Wire.string(b);
            String id = Wire.string(b);
            int shape = Math.max(0, Math.min(4, b.readByte()));
            int x = b.readVarInt();
            int y = b.readVarInt();
            int z = b.readVarInt();
            String icon = Wire.string(b);
            String label = Wire.string(b);
            boolean literal = b.readBoolean();
            List<String> args = Wire.strings(b, 4);
            int argb = b.readInt();
            int fill = b.readInt();
            int border = b.readInt();
            int n = b.readVarInt();
            if (n < 0 || n > 2048) {
                throw new IllegalArgumentException("too many coordinates: " + n);
            }
            int[] coords = new int[n];
            for (int k = 0; k < n; k++) {
                coords[k] = b.readVarInt();
            }
            return new LayerItem(layer, id, shape, x, y, z, icon, label, literal, args, argb, fill, border, coords,
                    Wire.string(b));
        }

        /** The client's identity for an item: layer and id. */
        public String key() {
            return layer + "|" + id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof LayerItem i && i.shape == shape && i.x == x && i.y == y && i.z == z
                    && i.argb == argb && i.fill == fill && i.border == border && i.literal == literal
                    && i.layer.equals(layer) && i.id.equals(id) && i.icon.equals(icon) && i.label.equals(label)
                    && i.args.equals(args) && java.util.Arrays.equals(i.coords, coords) && i.badge.equals(badge);
        }

        @Override
        public int hashCode() {
            return (layer.hashCode() * 31 + id.hashCode()) * 31 + java.util.Arrays.hashCode(coords) + x * 7 + z;
        }

        @Override
        public String toString() {
            return "LayerItem[" + layer + "]";
        }
    }

    /** Changes to this player's layer items in one dimension; {@code reset} clears everything first. */
    public record LayerDelta(String dimension, boolean reset, List<LayerItem> upserts, List<String> removed)
            implements CustomPacketPayload {
        public static final Type<LayerDelta> TYPE = Wire.type("layer_delta");
        public static final StreamCodec<FriendlyByteBuf, LayerDelta> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.dimension);
                    b.writeBoolean(p.reset);
                    Wire.list(b, p.upserts, MAX_LAYER_ITEMS, LayerItem::write);
                    Wire.strings(b, p.removed, MAX_LAYER_ITEMS);
                },
                b -> new LayerDelta(Wire.string(b), b.readBoolean(), Wire.list(b, MAX_LAYER_ITEMS, LayerItem::read),
                        Wire.strings(b, MAX_LAYER_ITEMS)));

        @Override
        public Type<LayerDelta> type() {
            return TYPE;
        }
    }

    /** A badge on a dimension (no position): a provider's id, the dimension, an icon and a label. */
    public record DimensionBadge(String id, String dimension, String icon, String label, boolean literal) {
        static void write(FriendlyByteBuf b, DimensionBadge d) {
            Wire.string(b, d.id);
            Wire.string(b, d.dimension);
            Wire.string(b, d.icon);
            Wire.string(b, d.label);
            b.writeBoolean(d.literal);
        }

        static DimensionBadge read(FriendlyByteBuf b) {
            return new DimensionBadge(Wire.string(b), Wire.string(b), Wire.string(b), Wire.string(b), b.readBoolean());
        }
    }

    /** Every dimension badge this player may see (Nerospace's visited worlds, NeroEvents' storms), replacing the last. */
    public record DimensionBadges(List<DimensionBadge> badges) implements CustomPacketPayload {
        public static final Type<DimensionBadges> TYPE = Wire.type("dimension_badges");
        public static final StreamCodec<FriendlyByteBuf, DimensionBadges> STREAM_CODEC = StreamCodec.of(
                (b, p) -> Wire.list(b, p.badges, MAX_LAYERS, DimensionBadge::write),
                b -> new DimensionBadges(Wire.list(b, MAX_LAYERS, DimensionBadge::read)));

        @Override
        public Type<DimensionBadges> type() {
            return TYPE;
        }
    }

    /**
     * One server tile for a shared area, already masked to the viewer's fog of war. Empty {@code data} means "you have
     * the current copy" ({@code etag} matched). A tile the viewer may not see is never answered at all.
     */
    public record TileData(String dimension, int lod, int tx, int tz, long etag, byte[] data)
            implements CustomPacketPayload {
        public static final Type<TileData> TYPE = Wire.type("tile");
        public static final StreamCodec<FriendlyByteBuf, TileData> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.dimension);
                    b.writeByte(p.lod);
                    b.writeVarInt(p.tx);
                    b.writeVarInt(p.tz);
                    b.writeLong(p.etag);
                    Wire.bytes(b, p.data, MAX_TILE_BYTES);
                },
                b -> new TileData(Wire.string(b), Math.max(0, Math.min(4, b.readByte())), b.readVarInt(),
                        b.readVarInt(), b.readLong(), Wire.bytes(b, MAX_TILE_BYTES)));

        @Override
        public Type<TileData> type() {
            return TYPE;
        }
    }

    /** Server-driven perf control: toggle / reset / dump the client overlay, with server counter lines for ops. */
    public record Perf(int op, List<String> serverLines) implements CustomPacketPayload {
        public static final int TOGGLE = 0;
        public static final int RESET = 1;
        public static final int DUMP = 2;
        public static final Type<Perf> TYPE = Wire.type("perf");
        public static final StreamCodec<FriendlyByteBuf, Perf> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeByte(p.op);
                    Wire.strings(b, p.serverLines, 64);
                },
                b -> new Perf(Wire.ordinal(b, 3), Wire.strings(b, 64)));

        @Override
        public Type<Perf> type() {
            return TYPE;
        }
    }

    /**
     * {@code /neromaps export}: everything the server holds about the receiving player, as deflated JSON. Only ever
     * sent to that player; their client writes it to their own game folder, so no export file is left on the server.
     */
    public record Export(byte[] deflated) implements CustomPacketPayload {
        public static final int MAX_BYTES = 1 << 20;
        public static final Type<Export> TYPE = Wire.type("export");
        public static final StreamCodec<FriendlyByteBuf, Export> STREAM_CODEC = StreamCodec.of(
                (b, p) -> Wire.bytes(b, p.deflated, MAX_BYTES),
                b -> new Export(Wire.bytes(b, MAX_BYTES)));

        @Override
        public Type<Export> type() {
            return TYPE;
        }
    }

    // ============================================================================================ C2S

    /** Create, update or delete one of the sender's own waypoints, or delete one of their death points. */
    public record WaypointEdit(int op, Waypoint waypoint, int deathIndex) implements CustomPacketPayload {
        public static final int CREATE = 0;
        public static final int UPDATE = 1;
        public static final int DELETE = 2;
        public static final int DELETE_DEATH = 3;
        public static final Type<WaypointEdit> TYPE = Wire.type("waypoint_edit");
        public static final StreamCodec<FriendlyByteBuf, WaypointEdit> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeByte(p.op);
                    writeWaypoint(b, p.waypoint);
                    b.writeVarInt(p.deathIndex);
                },
                b -> new WaypointEdit(Wire.ordinal(b, 4), readWaypoint(b), b.readVarInt()));

        @Override
        public Type<WaypointEdit> type() {
            return TYPE;
        }
    }

    /** Offer (to a named player), accept / decline (by request id), or revoke (with a named player). */
    public record ShareAction(int op, String player, long requestId, ShareScope scope, boolean allKinds)
            implements CustomPacketPayload {
        public static final int OFFER = 0;
        public static final int ACCEPT = 1;
        public static final int DECLINE = 2;
        public static final int REVOKE = 3;
        public static final Type<ShareAction> TYPE = Wire.type("share_action");
        public static final StreamCodec<FriendlyByteBuf, ShareAction> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeByte(p.op);
                    Wire.string(b, p.player);
                    b.writeLong(p.requestId);
                    writeScope(b, p.scope);
                    b.writeBoolean(p.allKinds);
                },
                b -> new ShareAction(Wire.ordinal(b, 4), Wire.string(b), b.readLong(), readScope(b), b.readBoolean()));

        @Override
        public Type<ShareAction> type() {
            return TYPE;
        }
    }

    public record PrivacySet(boolean deathWaypoints, boolean acceptRequests) implements CustomPacketPayload {
        public static final Type<PrivacySet> TYPE = Wire.type("privacy");
        public static final StreamCodec<FriendlyByteBuf, PrivacySet> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBoolean(p.deathWaypoints);
                    b.writeBoolean(p.acceptRequests);
                },
                b -> new PrivacySet(b.readBoolean(), b.readBoolean()));

        @Override
        public Type<PrivacySet> type() {
            return TYPE;
        }
    }

    /** The area the client's world map is showing, so layer queries can cover it (clamped by the server). */
    public record LayerView(String dimension, int minX, int minZ, int maxX, int maxZ, boolean open)
            implements CustomPacketPayload {
        public static final Type<LayerView> TYPE = Wire.type("layer_view");
        public static final StreamCodec<FriendlyByteBuf, LayerView> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.dimension);
                    b.writeVarInt(p.minX);
                    b.writeVarInt(p.minZ);
                    b.writeVarInt(p.maxX);
                    b.writeVarInt(p.maxZ);
                    b.writeBoolean(p.open);
                },
                b -> new LayerView(Wire.string(b), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                        b.readBoolean()));

        @Override
        public Type<LayerView> type() {
            return TYPE;
        }
    }

    /** A request for server tiles of shared areas: tile keys with the etag the client holds (0 = none). */
    public record TileRequest(String dimension, int lod, long[] tiles, long[] etags) implements CustomPacketPayload {
        public static final Type<TileRequest> TYPE = Wire.type("tile_request");
        public static final StreamCodec<FriendlyByteBuf, TileRequest> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    Wire.string(b, p.dimension);
                    b.writeByte(p.lod);
                    int n = Math.min(MAX_TILE_REQUEST, Math.min(p.tiles.length, p.etags.length));
                    b.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        b.writeLong(p.tiles[i]);
                        b.writeLong(p.etags[i]);
                    }
                },
                b -> {
                    String dim = Wire.string(b);
                    int lod = Math.max(0, Math.min(4, b.readByte()));
                    int n = b.readVarInt();
                    if (n < 0 || n > MAX_TILE_REQUEST) {
                        throw new IllegalArgumentException("too many tiles: " + n);
                    }
                    long[] t = new long[n];
                    long[] e = new long[n];
                    for (int i = 0; i < n; i++) {
                        t[i] = b.readLong();
                        e[i] = b.readLong();
                    }
                    return new TileRequest(dim, lod, t, e);
                });

        @Override
        public Type<TileRequest> type() {
            return TYPE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TileRequest r && r.dimension.equals(dimension) && r.lod == lod
                    && java.util.Arrays.equals(r.tiles, tiles) && java.util.Arrays.equals(r.etags, etags);
        }

        @Override
        public int hashCode() {
            return dimension.hashCode() * 31 + java.util.Arrays.hashCode(tiles);
        }

        @Override
        public String toString() {
            return "TileRequest[" + tiles.length + "]";
        }
    }

    // --- Stage 5: the survival layer ---------------------------------------------------------------------------

    /** The label typed on the Cartography Desk's Label tab (applies only to the sender's open desk menu). */
    public record DeskLabel(String label) implements CustomPacketPayload {
        public static final Type<DeskLabel> TYPE = Wire.type("desk_label");
        public static final StreamCodec<FriendlyByteBuf, DeskLabel> STREAM_CODEC = StreamCodec.of(
                (b, p) -> Wire.string(b, p.label), b -> new DeskLabel(Wire.string(b)));

        @Override
        public Type<DeskLabel> type() {
            return TYPE;
        }
    }

    /** Opens the signpost editor on the client with the post's current public values. No owner exists to send. */
    public record SignpostOpen(long pos, String label, int glyph, int colour, boolean shown)
            implements CustomPacketPayload {
        public static final Type<SignpostOpen> TYPE = Wire.type("signpost_open");
        public static final StreamCodec<FriendlyByteBuf, SignpostOpen> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeLong(p.pos);
                    Wire.string(b, p.label);
                    b.writeVarInt(p.glyph);
                    b.writeVarInt(p.colour);
                    b.writeBoolean(p.shown);
                },
                b -> new SignpostOpen(b.readLong(), Wire.string(b), b.readVarInt(), b.readVarInt(), b.readBoolean()));

        @Override
        public Type<SignpostOpen> type() {
            return TYPE;
        }
    }

    /** A signpost edit: validated on the server (reach, block, filter, rate). */
    public record SignpostEdit(long pos, String label, int glyph, int colour, boolean shown)
            implements CustomPacketPayload {
        public static final Type<SignpostEdit> TYPE = Wire.type("signpost_edit");
        public static final StreamCodec<FriendlyByteBuf, SignpostEdit> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeLong(p.pos);
                    Wire.string(b, p.label);
                    b.writeVarInt(p.glyph);
                    b.writeVarInt(p.colour);
                    b.writeBoolean(p.shown);
                },
                b -> new SignpostEdit(b.readLong(), Wire.string(b), b.readVarInt(), b.readVarInt(), b.readBoolean()));

        @Override
        public Type<SignpostEdit> type() {
            return TYPE;
        }
    }
}
