package za.co.neroland.neromaps.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.server.layers.LayerService;
import za.co.neroland.neromaps.server.tiles.CaptureService;
import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.Grant;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.store.ShareRequest;
import za.co.neroland.neromaps.store.ShareScope;

/**
 * The server half of NeroMaps, driven by one server-tick hook per loader. It tracks sessions in memory (login, logout,
 * dimension change and death are noticed from the tick, so no loader events are needed for them), records exploration
 * once a second, relays live positions under accepted grants at most once a second, and runs the capture and layer
 * services. Nothing here writes a position to disk: sessions and positions live in memory and end with the session.
 */
public final class MapServer {

    /** Memory-only per-player session state. */
    static final class Session {
        String dimension = "";
        boolean dead;
        int exploredCx = Integer.MIN_VALUE;
        int exploredCz;
        String exploredDim = "";
        int lastPositionsHash;
    }

    private static @Nullable MinecraftServer server;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int ticks;
    private static int policyRevision = 1;
    private static long purgeDay = -1;
    private static @Nullable String worldId;

    private MapServer() {
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    public static PolicySnapshot policy() {
        return PolicySnapshot.fromConfig(policyRevision);
    }

    public static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    public static ConsentLedger ledger(MinecraftServer s) {
        return new ConsentLedger(PlayerStore.get(s), new java.security.SecureRandom()::nextLong);
    }

    // --- lifecycle -------------------------------------------------------------------------------------------

    /** Server tick (end of tick), from each loader. */
    public static void tick(MinecraftServer s) {
        if (s != server) {
            reset();
            server = s;
        }
        ticks++;
        za.co.neroland.neromaps.link.MapsLinkModule.tick(s);
        try {
            List<ServerPlayer> players = s.getPlayerList().getPlayers();
            for (ServerPlayer p : players) {
                Session ss = SESSIONS.get(p.getUUID());
                if (ss == null) {
                    continue;
                }
                boolean dead = p.isDeadOrDying();
                if (dead && !ss.dead) {
                    onDeath(s, p);
                }
                ss.dead = dead;
            }
            CaptureService.tick(s);
            za.co.neroland.neromaps.server.tiles.TileService.drain(s);
            if (ticks % 20 == 0) {
                second(s, players);
                za.co.neroland.neromaps.survival.PlacedIndex.second(s);
            }
        } catch (RuntimeException e) {
            // never break the server tick; report the class only (no player data in the message)
            NeroMapsCommon.LOGGER.warn("[NeroMaps] server tick step failed ({})", e.getClass().getName());
            NeroMapsCommon.LOGGER.debug("[NeroMaps] server tick failure", e);
        }
    }

    /** Server stopping: flush capture, forget sessions. */
    public static void stopping() {
        CaptureService.stop();
        za.co.neroland.neromaps.link.MapsLinkModule.stopping();
        reset();
    }

    private static void reset() {
        SESSIONS.clear();
        ServerHandlers.reset();
        LayerService.reset();
        za.co.neroland.neromaps.server.tiles.TileService.reset();
        za.co.neroland.neromaps.survival.PlacedIndex.reset();
        za.co.neroland.neromaps.server.tiles.LinkTiles.LIVE.reset();
        za.co.neroland.neromaps.link.MapsLinkEvents.reset();
        server = null;
        worldId = null;
        ticks = 0;
    }

    private static void second(MinecraftServer s, List<ServerPlayer> players) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            online.add(id);
            Session ss = SESSIONS.get(id);
            String dim = dimension(p);
            if (ss == null) {
                ss = new Session();
                ss.dimension = dim;
                ss.dead = p.isDeadOrDying();
                SESSIONS.put(id, ss);
                join(s, p);
            } else if (!ss.dimension.equals(dim)) {
                ss.dimension = dim;
                hello(s, p);
                LayerService.dimensionChanged(p);
            }
        }
        List<UUID> gone = new ArrayList<>();
        for (UUID id : SESSIONS.keySet()) {
            if (!online.contains(id)) {
                gone.add(id);
            }
        }
        for (UUID id : gone) {
            leave(s, id);
        }
        explore(s, players);
        positions(s, players);
        LayerService.second(s, players);
        za.co.neroland.neromaps.link.MapsLinkEvents.second(PlayerStore.get(s),
                za.co.neroland.neromaps.server.tiles.LinkTiles.LIVE, policy().publicMap(), System.currentTimeMillis());
        long day = today();
        if (day != purgeDay) {
            purgeDay = day;
            ledger(s).purgeExpired(day);
        }
    }

    private static void join(MinecraftServer s, ServerPlayer p) {
        hello(s, p);
        syncWaypoints(s, p);
        syncShares(s, p);
        LayerService.joined(p);
        if (policy().publicMap()) {
            MapNetwork.send(p, Payloads.Notice.of("neromaps.policy.public_map_notice"));
        }
        PlayerRecord r = PlayerStore.get(s).find(p.getUUID()).orElse(null);
        if (r != null && !r.incoming().isEmpty()) {
            MapNetwork.send(p, Payloads.Notice.of("neromaps.share.pending", Integer.toString(r.incoming().size())));
        }
    }

    private static void leave(MinecraftServer s, UUID id) {
        SESSIONS.remove(id);
        ServerHandlers.forget(id);
        LayerService.left(id);
        za.co.neroland.neromaps.server.tiles.TileService.forget(id);
        za.co.neroland.neromaps.survival.SurvivalHandlers.forget(id);
    }

    public static void hello(MinecraftServer s, ServerPlayer p) {
        if (worldId == null) {
            worldId = WorldId.get(s);
        }
        MapNetwork.send(p, new Payloads.Hello(worldId, policy(), Payloads.Hello.PROTOCOL));
    }

    /** After {@code /neromaps reload}: a new policy revision to everyone. */
    public static void policyReloaded(MinecraftServer s) {
        policyRevision++;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            hello(s, p);
        }
    }

    // --- deaths ----------------------------------------------------------------------------------------------

    private static void onDeath(MinecraftServer s, ServerPlayer p) {
        MapPlayerState state = PlayerStore.get(s);
        DeathPoint d = new DeathPoint(dimension(p), p.getBlockX(), p.getBlockY(), p.getBlockZ());
        if (state.addDeath(p.getUUID(), d, NeroMapsConfig.deathWaypoints())) {
            syncWaypoints(s, p);
            za.co.neroland.neromaps.link.MapsLinkEvents.death(p.getUUID());
            MapNetwork.send(p, Payloads.Notice.of("neromaps.waypoint.death_marked"));
        }
    }

    // --- exploration --------------------------------------------------------------------------

    private static void explore(MinecraftServer s, List<ServerPlayer> players) {
        MapPlayerState state = PlayerStore.get(s);
        int radius = Math.min(NeroMapsConfig.exploreRadiusChunks(), Math.max(2, s.getPlayerList().getViewDistance()));
        int maxRegions = NeroMapsConfig.maxExploredRegions();
        for (ServerPlayer p : players) {
            if (p.isSpectator()) {
                continue;
            }
            Session ss = SESSIONS.get(p.getUUID());
            if (ss == null) {
                continue;
            }
            int cx = p.getBlockX() >> 4;
            int cz = p.getBlockZ() >> 4;
            String dim = dimension(p);
            if (cx == ss.exploredCx && cz == ss.exploredCz && dim.equals(ss.exploredDim)) {
                continue; // nothing new to explore from the same chunk
            }
            ss.exploredCx = cx;
            ss.exploredCz = cz;
            ss.exploredDim = dim;
            ServerLevel level = level(p);
            int r2 = radius * radius + radius;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dz * dz > r2) {
                        continue;
                    }
                    if (state.markExplored(p.getUUID(), dim, cx + dx, cz + dz, maxRegions)) {
                        CaptureService.explored(level, cx + dx, cz + dz);
                    }
                }
            }
        }
    }

    // --- live positions -----------------------------------------------------------------------

    /** True when {@code p} may currently be shown to others at all (never sneaking, invisible or spectating). */
    public static boolean shareable(ServerPlayer p) {
        return !p.isShiftKeyDown() && !p.isInvisible() && !p.isSpectator();
    }

    private static void positions(MinecraftServer s, List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        MapPlayerState state = PlayerStore.get(s);
        // one pass: recipient -> sharers with an accepted live-position grant
        Map<UUID, List<UUID>> bySharer = new HashMap<>();
        state.forEach((sharer, r) -> {
            for (Grant g : r.grants()) {
                if (g.scope().kind() == ShareScope.Kind.LIVE_POSITION) {
                    bySharer.computeIfAbsent(g.to(), k -> new ArrayList<>(2)).add(sharer);
                }
            }
        });
        for (ServerPlayer viewer : players) {
            Session ss = SESSIONS.get(viewer.getUUID());
            if (ss == null) {
                continue;
            }
            List<UUID> sharers = bySharer.getOrDefault(viewer.getUUID(), List.of());
            List<Payloads.Position> out = new ArrayList<>(sharers.size());
            String vdim = dimension(viewer);
            for (UUID sharerId : sharers) {
                ServerPlayer sharer = s.getPlayerList().getPlayer(sharerId);
                if (sharer == null || !shareable(sharer)) {
                    continue; // offline, sneaking, invisible or spectating: nothing is sent
                }
                String sdim = dimension(sharer);
                if (sdim.equals(vdim)) {
                    out.add(new Payloads.Position(nameOf(s, sharerId), sdim, true, sharer.getBlockX(),
                            sharer.getBlockZ()));
                } else {
                    out.add(new Payloads.Position(nameOf(s, sharerId), sdim, false, 0, 0));
                }
                if (out.size() >= Payloads.MAX_POSITIONS) {
                    break;
                }
            }
            int hash = out.hashCode();
            if (hash != ss.lastPositionsHash || !out.isEmpty()) {
                ss.lastPositionsHash = hash;
                MapNetwork.send(viewer, new Payloads.Positions(out));
            }
        }
    }

    // --- sync helpers ----------------------------------------------------------------------------------------

    public static void syncWaypoints(MinecraftServer s, ServerPlayer p) {
        MapPlayerState state = PlayerStore.get(s);
        PlayerRecord r = state.find(p.getUUID()).orElse(null);
        List<Payloads.SharedWaypoint> shared = new ArrayList<>();
        for (ConsentLedger.SharedWaypoint sw : ledger(s).waypointsSharedWith(p.getUUID())) {
            shared.add(new Payloads.SharedWaypoint(nameOf(s, sw.sharer()), sw.waypoint()));
        }
        MapNetwork.send(p, new Payloads.Waypoints(r == null ? List.of() : r.waypoints(),
                r == null ? List.of() : r.deaths(), shared));
    }

    public static void syncShares(MinecraftServer s, ServerPlayer p) {
        MapPlayerState state = PlayerStore.get(s);
        UUID me = p.getUUID();
        PlayerRecord r = state.find(me).orElse(null);
        List<Payloads.ShareLine> out = new ArrayList<>();
        List<Payloads.ShareLine> in = new ArrayList<>();
        List<Payloads.ShareLine> pending = new ArrayList<>();
        if (r != null) {
            for (Grant g : r.grants()) {
                out.add(new Payloads.ShareLine(nameOf(s, g.to()), g.scope(), 0L));
            }
            for (ShareRequest q : r.incoming()) {
                pending.add(new Payloads.ShareLine(nameOf(s, q.from()), q.scope(), q.requestId()));
            }
        }
        state.forEach((sharer, rec) -> {
            for (Grant g : rec.grants()) {
                if (g.to().equals(me)) {
                    in.add(new Payloads.ShareLine(nameOf(s, sharer), g.scope(), 0L));
                }
            }
        });
        MapNetwork.send(p, new Payloads.Shares(out, in, pending, r == null || r.deathWaypoints(),
                r == null || r.acceptRequests()));
    }

    /** Re-sends waypoints and shares to a player if online (after another player's action touched them). */
    public static void refresh(MinecraftServer s, UUID player) {
        ServerPlayer p = s.getPlayerList().getPlayer(player);
        if (p != null) {
            syncWaypoints(s, p);
            syncShares(s, p);
        }
    }

    /**
     * A display name for a counterparty: their name while online, otherwise a short stable handle that is not their
     * UUID. Only ever sent to a player who shares with them.
     */
    public static String nameOf(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) {
            return p.getGameProfile().name();
        }
        return handle(id);
    }

    /** The offline handle for a counterparty: {@code #} and eight hex digits of a hash, never the UUID itself. */
    public static String handle(UUID id) {
        long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
        h ^= h >>> 29;
        return "#" + String.format("%08x", (int) h);
    }

    public static String dimension(ServerPlayer p) {
        ServerLevel level = level(p);
        return level.dimension().identifier().toString();
    }

    public static ServerLevel level(ServerPlayer p) {
        ServerLevel level = p.level();
        return level;
    }

    /**
     * Same tick as the erasure: ends live-position views involving {@code erased}, resyncs every touched player (their
     * shares with the erased player are gone), resyncs the erased player if online (empty waypoints, shares, no
     * positions), drops their in-flight tile answers, layer state, rate windows and link state, and tells the app
     * (the {@code dimensions} record epoch changes, so it wipes its cache for this server).
     */
    public static void erased(UUID erased, List<UUID> touched) {
        za.co.neroland.neromaps.link.MapsLinkEvents.forget(erased);
        za.co.neroland.neromaps.link.MapsLinkModule.erased(erased);
        za.co.neroland.neromaps.link.MapsLinkEvents.waypoints(erased);
        za.co.neroland.neromaps.link.MapsLinkEvents.shares(erased);
        MinecraftServer s = server;
        if (s == null) {
            return;
        }
        ServerHandlers.forget(erased);
        Session own = SESSIONS.get(erased);
        if (own != null) {
            own.lastPositionsHash = 0;
            ServerPlayer ep = s.getPlayerList().getPlayer(erased);
            if (ep != null) {
                syncWaypoints(s, ep);
                syncShares(s, ep);
                MapNetwork.send(ep, new Payloads.Positions(List.of()));
            }
        }
        for (UUID id : touched) {
            za.co.neroland.neromaps.link.MapsLinkEvents.shares(id);
            za.co.neroland.neromaps.server.tiles.TileService.dropViewer(id);
            LayerService.sharesChanged(id);
            refresh(s, id);
            Session ss = SESSIONS.get(id);
            if (ss != null) {
                ss.lastPositionsHash = 0;
                ServerPlayer p = s.getPlayerList().getPlayer(id);
                if (p != null) {
                    MapNetwork.send(p, new Payloads.Positions(List.of()));
                }
            }
        }
        if (s.getPlayerList().getPlayer(erased) != null) {
            LayerService.sharesChanged(erased); // still playing: re-query layers from nothing
        } else {
            LayerService.left(erased);
        }
        za.co.neroland.neromaps.server.tiles.TileService.forget(erased);
    }

    /** Server counter lines for {@code /neromaps perf} (global numbers only). */
    public static List<String> perfLines() {
        List<String> lines = new ArrayList<>();
        lines.add("sessions " + SESSIONS.size());
        lines.addAll(CaptureService.perfLines());
        lines.addAll(LayerService.perfLines());
        lines.addAll(za.co.neroland.neromaps.server.tiles.LinkTiles.LIVE.perfLines());
        return lines;
    }
}
