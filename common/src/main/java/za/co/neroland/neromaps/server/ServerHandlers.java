package za.co.neroland.neromaps.server;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.network.RateLimiter;
import za.co.neroland.neromaps.server.layers.LayerService;
import za.co.neroland.neromaps.server.tiles.TileService;
import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.Grant;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.store.ShareRequest;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * Serverbound payload handlers and the shared actions behind the commands. Every action is validated against the
 * sending player (their own record only, never another's), the server policy and a per-player rate limit; a dropped
 * or refused action changes nothing. Server thread only.
 */
public final class ServerHandlers {

    /** Coordinates a waypoint may hold (the world border's maximum). */
    public static final int MAX_COORD = 30_000_000;
    public static final int MAX_Y = 20_000;

    static final RateLimiter WAYPOINTS = new RateLimiter(20, 250);
    static final RateLimiter OFFERS = new RateLimiter(10, 6_000);
    static final RateLimiter ANSWERS = new RateLimiter(20, 500);
    static final RateLimiter PRIVACY = new RateLimiter(5, 1_000);
    static final RateLimiter VIEWS = new RateLimiter(10, 200);
    /** About 4 tile requests a second, bursts of 8: wall maps ask once a second, the world map once a second. */
    static final RateLimiter TILES = new RateLimiter(8, 250);

    private ServerHandlers() {
    }

    static void reset() {
        for (RateLimiter r : List.of(WAYPOINTS, OFFERS, ANSWERS, PRIVACY, VIEWS, TILES)) {
            r.forgetAll();
        }
    }

    public static void forget(UUID id) {
        for (RateLimiter r : List.of(WAYPOINTS, OFFERS, ANSWERS, PRIVACY, VIEWS, TILES)) {
            r.forget(id);
        }
    }

    private static @Nullable MinecraftServer server(ServerPlayer p) {
        ServerLevel level = p.level();
        return level.getServer();
    }

    private static void tell(ServerPlayer p, String key, String... args) {
        MapNetwork.send(p, Payloads.Notice.of(key, args));
    }

    // --- waypoints -------------------------------------------------------------------------------------------

    public static void waypointEdit(Payloads.WaypointEdit msg, ServerPlayer p) {
        MinecraftServer s = server(p);
        if (s == null || !WAYPOINTS.tryAcquire(p.getUUID())) {
            return;
        }
        Waypoint w = msg.waypoint();
        switch (msg.op()) {
            case Payloads.WaypointEdit.CREATE -> create(s, p, w);
            case Payloads.WaypointEdit.UPDATE -> update(s, p, w);
            case Payloads.WaypointEdit.DELETE -> remove(s, p, w.id());
            case Payloads.WaypointEdit.DELETE_DEATH -> {
                if (PlayerStore.get(s).removeDeath(p.getUUID(), msg.deathIndex())) {
                    MapServer.syncWaypoints(s, p);
                    za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(p.getUUID());
                }
            }
            default -> { }
        }
    }

    /** Validates the dimension (a level this server has) and clamps the coordinates. Null when invalid. */
    static @Nullable Waypoint sanitise(MinecraftServer s, Waypoint w) {
        boolean known = false;
        for (ServerLevel level : s.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(w.dimension())) {
                known = true;
                break;
            }
        }
        if (!known) {
            return null;
        }
        return new Waypoint(w.id(), w.set(), w.name(), w.colour(), w.icon(), w.dimension(),
                clamp(w.x(), MAX_COORD), clamp(w.y(), MAX_Y), clamp(w.z(), MAX_COORD), false, w.beam(),
                w.distanceLabel());
    }

    private static int clamp(int v, int max) {
        return Math.max(-max, Math.min(max, v));
    }

    /** Creates a waypoint for {@code p}. Returns its id, or -1 (the player was told why). */
    public static int create(MinecraftServer s, ServerPlayer p, Waypoint raw) {
        Waypoint w = sanitise(s, raw);
        if (w == null) {
            tell(p, "neromaps.waypoint.bad_dimension");
            return -1;
        }
        int[] id = new int[1];
        MapPlayerState.Result r = PlayerStore.get(s).addWaypoint(p.getUUID(), w, NeroMapsConfig.waypointLimit(),
                NeroMapsConfig.nameDenyList(), id);
        if (r != MapPlayerState.Result.OK) {
            refuse(p, r);
            return -1;
        }
        MapServer.syncWaypoints(s, p);
        za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(p.getUUID());
        return id[0];
    }

    public static boolean update(MinecraftServer s, ServerPlayer p, Waypoint raw) {
        Waypoint w = sanitise(s, raw);
        if (w == null) {
            tell(p, "neromaps.waypoint.bad_dimension");
            return false;
        }
        MapPlayerState.Result r = PlayerStore.get(s).updateWaypoint(p.getUUID(), w, NeroMapsConfig.nameDenyList());
        if (r != MapPlayerState.Result.OK) {
            refuse(p, r);
            return false;
        }
        MapServer.syncWaypoints(s, p);
        refreshRecipients(s, p.getUUID(), w.id());
        za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(p.getUUID());
        return true;
    }

    public static boolean remove(MinecraftServer s, ServerPlayer p, int id) {
        List<UUID> recipients = recipients(s, p.getUUID(), id);
        MapPlayerState.Result r = PlayerStore.get(s).removeWaypoint(p.getUUID(), id);
        if (r != MapPlayerState.Result.OK) {
            refuse(p, r);
            return false;
        }
        MapServer.syncWaypoints(s, p);
        MapServer.syncShares(s, p);
        za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(p.getUUID());
        for (UUID to : recipients) {
            MapServer.refresh(s, to);
            za.co.neroland.neromaps.link.MapsLinkEvents.shares(to);
        }
        return true;
    }

    private static List<UUID> recipients(MinecraftServer s, UUID owner, int waypointId) {
        PlayerRecord r = PlayerStore.get(s).find(owner).orElse(null);
        if (r == null) {
            return List.of();
        }
        return r.grants().stream().filter(g -> g.scope().waypointId() == waypointId).map(Grant::to).toList();
    }

    private static void refreshRecipients(MinecraftServer s, UUID owner, int waypointId) {
        for (UUID to : recipients(s, owner, waypointId)) {
            ServerPlayer rp = s.getPlayerList().getPlayer(to);
            if (rp != null) {
                MapServer.syncWaypoints(s, rp);
            }
        }
    }

    private static void refuse(ServerPlayer p, MapPlayerState.Result r) {
        switch (r) {
            case LIMIT -> tell(p, "neromaps.waypoint.limit", Integer.toString(NeroMapsConfig.waypointLimit()));
            case NAME_EMPTY -> tell(p, "neromaps.waypoint.name_empty");
            case NAME_DENIED -> tell(p, "neromaps.waypoint.name_denied");
            case NOT_FOUND -> tell(p, "neromaps.waypoint.not_found");
            default -> { }
        }
    }

    // --- sharing ------------------------------------------------------------------------------

    public static void shareAction(Payloads.ShareAction msg, ServerPlayer p) {
        MinecraftServer s = server(p);
        if (s == null) {
            return;
        }
        switch (msg.op()) {
            case Payloads.ShareAction.OFFER -> {
                ServerPlayer target = s.getPlayerList().getPlayerByName(msg.player());
                offer(s, p, target, msg.scope());
            }
            case Payloads.ShareAction.ACCEPT -> answer(s, p, msg.requestId(), true);
            case Payloads.ShareAction.DECLINE -> answer(s, p, msg.requestId(), false);
            case Payloads.ShareAction.REVOKE -> revoke(s, p, msg.player(), msg.allKinds() ? null : msg.scope().kind());
            default -> { }
        }
    }

    /** Offers {@code scope} to an online player named by the sender. */
    public static boolean offer(MinecraftServer s, ServerPlayer p, @Nullable ServerPlayer target, ShareScope scope) {
        if (!OFFERS.tryAcquire(p.getUUID())) {
            tell(p, "neromaps.share.rate_limited");
            return false;
        }
        if (target == null) {
            tell(p, "neromaps.share.no_player");
            return false;
        }
        ConsentLedger ledger = MapServer.ledger(s);
        long[] id = new long[1];
        ConsentLedger.Offer o = ledger.offer(p.getUUID(), target.getUUID(), scope, MapServer.today(), id);
        switch (o) {
            case SELF -> tell(p, "neromaps.share.self");
            case ALREADY_SHARED -> tell(p, "neromaps.share.already");
            case NO_SUCH_WAYPOINT -> tell(p, "neromaps.waypoint.not_found");
            case SENT -> {
                tell(p, "neromaps.share.sent", target.getGameProfile().name());
                if (id[0] != 0) {
                    MapNetwork.send(target, new Payloads.Notice("neromaps.share.request",
                            List.of(p.getGameProfile().name(), "neromaps.share.kind." + kindKey(scope)), id[0]));
                    MapServer.syncShares(s, target);
                    za.co.neroland.neromaps.link.MapsLinkEvents.shareRequest(target.getUUID());
                }
            }
            default -> { }
        }
        return o == ConsentLedger.Offer.SENT;
    }

    public static String kindKey(ShareScope scope) {
        return switch (scope.kind()) {
            case WAYPOINT -> "waypoint";
            case EXPLORED_MAP -> "map";
            case LIVE_POSITION -> "position";
        };
    }

    /** The recipient accepts or declines one of their own pending requests. */
    public static boolean answer(MinecraftServer s, ServerPlayer p, long requestId, boolean accept) {
        if (!ANSWERS.tryAcquire(p.getUUID())) {
            return false;
        }
        ConsentLedger ledger = MapServer.ledger(s);
        ShareRequest q = accept ? ledger.accept(p.getUUID(), requestId) : ledger.decline(p.getUUID(), requestId);
        if (q == null) {
            tell(p, "neromaps.share.no_request");
            MapServer.syncShares(s, p);
            return false;
        }
        ServerPlayer sharer = s.getPlayerList().getPlayer(q.from());
        String who = MapServer.nameOf(s, q.from());
        if (accept) {
            tell(p, "neromaps.share.accepted_you", who);
            if (sharer != null) {
                tell(sharer, "neromaps.share.accepted_them", p.getGameProfile().name());
            }
            MapServer.syncWaypoints(s, p);
            LayerService.sharesChanged(p.getUUID());
        } else {
            tell(p, "neromaps.share.declined_you", who);
        }
        MapServer.syncShares(s, p);
        MapServer.refresh(s, q.from());
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(p.getUUID());
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(q.from());
        return accept;
    }

    /**
     * Ends shares between the sender and a counterparty (named, or by offline handle), in both directions. Immediate:
     * grants go now, live-position views stop on the next relay, cached shared tiles and layers are dropped.
     */
    public static int revoke(MinecraftServer s, ServerPlayer p, String who, ShareScope.@Nullable Kind kind) {
        if (!ANSWERS.tryAcquire(p.getUUID())) {
            return 0;
        }
        UUID other = resolveCounterparty(s, p.getUUID(), who);
        if (other == null) {
            tell(p, "neromaps.share.no_player");
            return 0;
        }
        List<ConsentLedger.Revoked> gone = MapServer.ledger(s).revoke(p.getUUID(), other, kind);
        if (gone.isEmpty()) {
            // nothing was shared: touch nobody (no resync, no dropped tile answers, no blanked positions)
            tell(p, "neromaps.share.revoked", "0");
            return 0;
        }
        afterRevoke(s, p.getUUID(), other);
        tell(p, "neromaps.share.revoked", Integer.toString(gone.size()));
        ServerPlayer op = s.getPlayerList().getPlayer(other);
        if (op != null && !gone.isEmpty()) {
            tell(op, "neromaps.share.revoked_them", p.getGameProfile().name());
        }
        return gone.size();
    }

    public static void afterRevoke(MinecraftServer s, UUID a, UUID b) {
        MapServer.refresh(s, a);
        MapServer.refresh(s, b);
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(a);
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(b);
        TileService.dropViewer(a);
        TileService.dropViewer(b);
        LayerService.sharesChanged(a);
        LayerService.sharesChanged(b);
        for (UUID id : List.of(a, b)) {
            ServerPlayer pl = s.getPlayerList().getPlayer(id);
            if (pl != null) {
                MapNetwork.send(pl, new Payloads.Positions(List.of()));
            }
        }
    }

    /**
     * Finds a counterparty of {@code me} by online name or offline handle, among players they share with. The handle
     * may be given with or without its leading {@code #} (a command word cannot start with one).
     */
    static @Nullable UUID resolveCounterparty(MinecraftServer s, UUID me, String raw) {
        ServerPlayer online = s.getPlayerList().getPlayerByName(raw);
        if (online != null) {
            return online.getUUID();
        }
        String who = raw.startsWith("#") ? raw : "#" + raw;
        MapPlayerState state = PlayerStore.get(s);
        PlayerRecord mine = state.find(me).orElse(null);
        if (mine != null) {
            for (Grant g : mine.grants()) {
                if (MapServer.handle(g.to()).equalsIgnoreCase(who)) {
                    return g.to();
                }
            }
            for (ShareRequest q : mine.incoming()) {
                if (MapServer.handle(q.from()).equalsIgnoreCase(who)) {
                    return q.from();
                }
            }
        }
        UUID[] found = new UUID[1];
        state.forEach((sharer, r) -> {
            if (found[0] == null && MapServer.handle(sharer).equalsIgnoreCase(who)) {
                for (Grant g : r.grants()) {
                    if (g.to().equals(me)) {
                        found[0] = sharer;
                        return;
                    }
                }
            }
        });
        return found[0];
    }

    // --- privacy ---------------------------------------------------------------------------------------------

    public static void privacy(Payloads.PrivacySet msg, ServerPlayer p) {
        MinecraftServer s = server(p);
        if (s == null || !PRIVACY.tryAcquire(p.getUUID())) {
            return;
        }
        setPrivacy(s, p, msg.deathWaypoints(), msg.acceptRequests());
    }

    public static void setPrivacy(MinecraftServer s, ServerPlayer p, boolean deathWaypoints, boolean acceptRequests) {
        MapPlayerState state = PlayerStore.get(s);
        state.setDeathWaypoints(p.getUUID(), deathWaypoints);
        state.setAcceptRequests(p.getUUID(), acceptRequests);
        MapServer.syncWaypoints(s, p);
        MapServer.syncShares(s, p);
        za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(p.getUUID());
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(p.getUUID());
    }

    // --- layers and tiles ------------------------------------------------------------------------------------

    public static void layerView(Payloads.LayerView msg, ServerPlayer p) {
        if (!VIEWS.tryAcquire(p.getUUID())) {
            return;
        }
        LayerService.view(p, msg);
    }

    public static void tileRequest(Payloads.TileRequest msg, ServerPlayer p) {
        if (!TILES.tryAcquire(p.getUUID())) {
            return;
        }
        TileService.request(p, msg);
    }
}
