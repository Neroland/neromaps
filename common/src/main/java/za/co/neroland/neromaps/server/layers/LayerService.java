package za.co.neroland.neromaps.server.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapDimensionBadge;
import za.co.neroland.nerolandcore.map.MapEvents;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapViewer;

import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.server.MapServer;

/**
 * NeroMaps as the consumer of Core's map API (decision 001 and its consumer obligations). For each online viewer it
 * queries every registered provider through {@link MapLayerRegistry#query} (the guarded path) for that viewer, their
 * dimension and the bounds they look at, on a bounded schedule and when a {@link MapEvents} hint marks them dirty, and
 * sends that viewer only the changes since the last refresh.
 *
 * <ul>
 *   <li>Each query is timed ({@code elapsedNanos}); a provider over {@link #PROVIDER_BUDGET_NANOS} is skipped for the
 *       next {@link #SKIP_REFRESHES} refreshes (its earlier items are kept meanwhile).</li>
 *   <li>Total items per viewer per refresh are capped at {@link #MAX_ITEMS} across providers.</li>
 *   <li>Items a re-query no longer returns are removed from the viewer's client (membership removed, erasure).</li>
 *   <li>Events are coalesced per layer and dimension and only mark viewers dirty; an event, its bounds or its timing
 *       never reaches a client.</li>
 *   <li>A layer whose mod is absent does not exist: the catalogue lists registered layers only.</li>
 * </ul>
 */
public final class LayerService {

    public static final long PROVIDER_BUDGET_NANOS = 2_000_000L;
    public static final int SKIP_REFRESHES = 10;
    public static final int MAX_ITEMS = 1024;
    public static final int VIEWERS_PER_SECOND = 16;
    public static final int REFRESH_SECONDS = 5;
    public static final int MINIMAP_RADIUS = 512;

    private static final class Viewer {
        String dimension = "";
        final Map<String, Payloads.LayerItem> sent = new HashMap<>();
        List<Payloads.DimensionBadge> badges = List.of();
        MapBounds view;
        String viewDimension = "";
        boolean viewOpen;
        boolean dirty = true;
        boolean reset = true;
        int age;
    }

    private static final LinkedHashMap<UUID, Viewer> VIEWERS = new LinkedHashMap<>();
    private static final Map<String, Integer> SKIP = new HashMap<>();
    private static final ConcurrentLinkedQueue<MapEvents.LayerChanged> EVENTS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();
    private static long queries;
    private static long queryNanos;
    private static long slowSkips;
    private static long itemsSent;
    private static long capped;

    private LayerService() {
    }

    /** Subscribes to Core's map events once (common init). The listener only queues the hint. */
    public static void init() {
        if (LISTENING.compareAndSet(false, true)) {
            MapEvents.onLayerChanged(e -> {
                if (EVENTS.size() < 4096) {
                    EVENTS.add(e);
                }
            });
        }
    }

    public static void reset() {
        VIEWERS.clear();
        SKIP.clear();
        EVENTS.clear();
    }

    // --- session hooks (server thread) ------------------------------------------------------------------------

    public static void joined(ServerPlayer p) {
        Viewer v = new Viewer();
        v.dimension = MapServer.dimension(p);
        VIEWERS.put(p.getUUID(), v);
        List<Payloads.LayerInfo> infos = new ArrayList<>();
        for (MapLayerInfo info : MapLayerRegistry.layers()) {
            String id = info.id().toString();
            infos.add(new Payloads.LayerInfo(id, info.titleKey(), info.icon().toString(), info.defaultVisible(),
                    info.defaultOnMinimap() && !LayerItems.MINIMAP_OFF_BY_DEFAULT.contains(id)));
        }
        MapNetwork.send(p, new Payloads.LayerCatalogue(infos));
    }

    public static void left(UUID id) {
        VIEWERS.remove(id);
    }

    public static void dimensionChanged(ServerPlayer p) {
        Viewer v = VIEWERS.get(p.getUUID());
        if (v != null) {
            v.dimension = MapServer.dimension(p);
            v.sent.clear();
            v.reset = true;
            v.dirty = true;
            v.viewOpen = false;
        }
    }

    /** A share involving this player changed: their entitlements may differ, so re-query soon. */
    public static void sharesChanged(UUID id) {
        Viewer v = VIEWERS.get(id);
        if (v != null) {
            v.dirty = true;
        }
    }

    /** The client's world map view (clamped to Core's span limit; only used while open and in their dimension). */
    public static void view(ServerPlayer p, Payloads.LayerView msg) {
        Viewer v = VIEWERS.get(p.getUUID());
        if (v == null) {
            return;
        }
        v.viewOpen = msg.open();
        v.viewDimension = msg.dimension();
        v.view = new MapBounds(msg.minX(), msg.minZ(), msg.maxX(), msg.maxZ()).clampSpan();
        v.dirty = true;
    }

    // --- schedule ---------------------------------------------------------------------------------------------

    public static void second(MinecraftServer s, List<ServerPlayer> players) {
        // coalesce events: each (layer, dimension, audience) marks viewers dirty once
        Set<String> seen = new HashSet<>();
        MapEvents.LayerChanged e;
        while ((e = EVENTS.poll()) != null) {
            String dim = e.dimension().identifier().toString();
            String k = e.layer() + "|" + dim + "|" + e.audience();
            if (!seen.add(k)) {
                continue;
            }
            if (e.audience() != null) {
                Viewer v = VIEWERS.get(e.audience());
                if (v != null && v.dimension.equals(dim)) {
                    v.dirty = true;
                }
            } else {
                for (Viewer v : VIEWERS.values()) {
                    if (v.dimension.equals(dim)) {
                        v.dirty = true;
                    }
                }
            }
        }
        if (MapLayerRegistry.providers().isEmpty()) {
            return;
        }
        SKIP.replaceAll((k, n) -> Math.max(0, n - 1));
        int budget = VIEWERS_PER_SECOND;
        for (ServerPlayer p : players) {
            Viewer v = VIEWERS.get(p.getUUID());
            if (v == null) {
                continue;
            }
            v.age++;
            if (budget > 0 && (v.dirty || v.age >= REFRESH_SECONDS)) {
                budget--;
                refresh(p, v);
            }
        }
    }

    private static void refresh(ServerPlayer p, Viewer v) {
        v.age = 0;
        v.dirty = false;
        ServerLevel level = p.level();
        ResourceKey<Level> dim = level.dimension();
        String dimId = dim.identifier().toString();
        MapBounds bounds = v.viewOpen && dimId.equals(v.viewDimension) && v.view != null ? v.view
                : MapBounds.around(p.getBlockX(), p.getBlockZ(), MINIMAP_RADIUS);
        MapViewer viewer = MapViewer.of(p);
        Map<String, Payloads.LayerItem> now = new LinkedHashMap<>();
        Set<String> keep = new HashSet<>();
        int total = 0;
        for (MapLayerProvider provider : MapLayerRegistry.providers()) {
            String mod = provider.modId();
            if (SKIP.getOrDefault(mod, 0) > 0) {
                for (MapLayerInfo info : provider.layers()) {
                    keep.add(info.id().toString());
                }
                continue;
            }
            MapQueryResult r = MapLayerRegistry.query(mod, viewer, dim, bounds);
            queries++;
            queryNanos += r.elapsedNanos();
            if (r.elapsedNanos() > PROVIDER_BUDGET_NANOS) {
                SKIP.put(mod, SKIP_REFRESHES);
                slowSkips++;
            }
            if (r.failed()) {
                continue; // partial output was discarded; its items are removed below (privacy over continuity)
            }
            Map<String, net.minecraft.resources.Identifier> icons = new HashMap<>();
            for (MapLayerInfo info : provider.layers()) {
                icons.put(info.id().toString(), info.icon());
            }
            for (MapMarker m : r.markers()) {
                if (total >= MAX_ITEMS) {
                    capped++;
                    break;
                }
                Payloads.LayerItem item = LayerItems.of(m, icons.get(m.layer().toString()));
                now.put(item.key(), item);
                total++;
            }
            for (MapArea a : r.areas()) {
                if (total >= MAX_ITEMS) {
                    capped++;
                    break;
                }
                Payloads.LayerItem item = LayerItems.of(a);
                now.put(item.key(), item);
                total++;
            }
        }
        badges(p, viewer, v);
        boolean reset = v.reset;
        if (reset) {
            v.sent.clear();
        }
        LayerItems.Delta d = LayerItems.diff(v.sent, now, keep);
        if (reset || !d.isEmpty()) {
            v.reset = false;
            itemsSent += d.upserts().size();
            MapNetwork.send(p, new Payloads.LayerDelta(dimId, reset, d.upserts(), d.removed()));
        }
    }

    /** Dimension badges (no position) from every provider, guarded by the registry; sent only when they change. */
    private static void badges(ServerPlayer p, MapViewer viewer, Viewer v) {
        List<Payloads.DimensionBadge> out = new ArrayList<>();
        for (MapLayerProvider provider : MapLayerRegistry.providers()) {
            if (SKIP.getOrDefault(provider.modId(), 0) > 0) {
                continue;
            }
            for (MapDimensionBadge b : MapLayerRegistry.dimensionBadges(provider.modId(), viewer)) {
                MapLabel l = b.label();
                out.add(new Payloads.DimensionBadge(b.id().toString(), b.dimension().identifier().toString(),
                        b.icon().toString(), l == null ? "" : l.text(), l != null && l.literal()));
            }
        }
        if (!out.equals(v.badges)) {
            v.badges = out;
            MapNetwork.send(p, new Payloads.DimensionBadges(out));
        }
    }

    public static List<String> perfLines() {
        List<String> l = new ArrayList<>();
        l.add("layer viewers " + VIEWERS.size() + ", providers " + MapLayerRegistry.providers().size()
                + ", queries " + queries + (queries == 0 ? "" : String.format(" (%.1f us avg)",
                        queryNanos / 1000.0 / queries)) + ", slow skips " + slowSkips + ", items sent " + itemsSent
                + ", capped " + capped);
        return l;
    }
}
