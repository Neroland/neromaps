package za.co.neroland.neromaps.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.client.hud.PerfOverlay;
import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.network.ClientSink;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * Everything the client knows from the server, in memory only and cleared on disconnect: the hello (world id and
 * policy), the player's waypoints and shares, live positions under accepted grants, layer items, and shared terrain
 * tiles (never written to the client's disk cache). Also the temporary waypoints, which never leave the client.
 * Client thread only.
 */
public final class ClientState implements ClientSink {

    public static final ClientState INSTANCE = new ClientState();
    public static final int MAX_SHARED_TILES = 96;

    private boolean serverHasMod;
    private String worldId = "default";
    private PolicySnapshot policy = PolicySnapshot.CLIENT_ONLY;
    private List<Waypoint> own = List.of();
    private List<DeathPoint> deaths = List.of();
    private List<Payloads.SharedWaypoint> shared = List.of();
    private final List<Waypoint> temporary = new ArrayList<>();
    private int nextTemp = -1;
    private Payloads.@Nullable Shares shares;
    private List<Payloads.Position> positions = List.of();
    private final LinkedHashMap<String, Payloads.LayerInfo> catalogue = new LinkedHashMap<>();
    private String layerDimension = "";
    private final LinkedHashMap<String, Payloads.LayerItem> items = new LinkedHashMap<>();
    private List<Payloads.DimensionBadge> badges = List.of();
    private final LinkedHashMap<Long, Tile> sharedTiles = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<Long, Long> sharedEtags = new HashMap<>();
    private String sharedTilesDim = "";
    private int version;
    private @Nullable Object connection;

    private ClientState() {
    }

    /** Installs this as the clientbound sink (client init). */
    public static void install() {
        ClientSink.Holder.install(INSTANCE);
    }

    // --- accessors ---------------------------------------------------------------------------------------------

    public boolean serverHasMod() {
        return serverHasMod;
    }

    public String worldId() {
        return worldId;
    }

    public PolicySnapshot policy() {
        return policy;
    }

    public boolean allows(PolicySnapshot.Feature f) {
        return policy.allows(f);
    }

    public List<Waypoint> own() {
        return own;
    }

    public List<DeathPoint> deaths() {
        return deaths;
    }

    public List<Payloads.SharedWaypoint> shared() {
        return shared;
    }

    public List<Waypoint> temporary() {
        return Collections.unmodifiableList(temporary);
    }

    public Payloads.@Nullable Shares shares() {
        return shares;
    }

    public List<Payloads.Position> positions() {
        return positions;
    }

    public Map<String, Payloads.LayerInfo> catalogue() {
        return Collections.unmodifiableMap(catalogue);
    }

    public Map<String, Payloads.LayerItem> items(String dimension) {
        return dimension.equals(layerDimension) ? Collections.unmodifiableMap(items) : Map.of();
    }

    /** Dimension badges the player may see (no positions). */
    public List<Payloads.DimensionBadge> badges() {
        return badges;
    }

    /** Bumped on every change, so the minimap rebuilds its marker list only then. */
    public int version() {
        return version;
    }

    /** True when someone shares their explored map of {@code dimension} with this player. */
    public boolean hasSharedMap(String dimension) {
        Payloads.Shares s = shares;
        if (s == null) {
            return false;
        }
        for (Payloads.ShareLine l : s.incoming()) {
            if (l.scope().coversDimension(dimension)) {
                return true;
            }
        }
        return false;
    }

    public @Nullable Tile sharedTile(String dimension, int lod, int tx, int tz) {
        if (!dimension.equals(sharedTilesDim)) {
            return null;
        }
        return sharedTiles.get(TileKey.tile(lod, tx, tz));
    }

    public long sharedEtag(String dimension, int lod, int tx, int tz) {
        if (!dimension.equals(sharedTilesDim)) {
            return 0L;
        }
        Long e = sharedEtags.get(TileKey.tile(lod, tx, tz));
        return e == null ? 0L : e;
    }

    // --- temporary waypoints (client memory only, never sent) --------------------------------------------------

    public Waypoint addTemporary(Waypoint w) {
        Waypoint t = new Waypoint(nextTemp--, w.set(), w.name(), w.colour(), w.icon(), w.dimension(), w.x(), w.y(),
                w.z(), false, w.beam(), w.distanceLabel());
        temporary.add(t);
        version++;
        return t;
    }

    public void updateTemporary(Waypoint w) {
        for (int i = 0; i < temporary.size(); i++) {
            if (temporary.get(i).id() == w.id()) {
                temporary.set(i, w);
                version++;
            }
        }
    }

    public void removeTemporary(int id) {
        if (temporary.removeIf(w -> w.id() == id)) {
            version++;
        }
    }

    // --- lifecycle ---------------------------------------------------------------------------------------------

    /** Client tick: forgets everything when the connection changes (logout, server switch). */
    public void tick(Minecraft mc) {
        Object c = mc.getConnection();
        if (c != connection) {
            connection = c;
            clear();
        }
        WallTiles.tick();
    }

    private void clear() {
        serverHasMod = false;
        worldId = "default";
        policy = PolicySnapshot.CLIENT_ONLY;
        own = List.of();
        deaths = List.of();
        shared = List.of();
        temporary.clear();
        shares = null;
        positions = List.of();
        catalogue.clear();
        items.clear();
        layerDimension = "";
        badges = List.of();
        sharedTiles.clear();
        sharedEtags.clear();
        WallTiles.clear();
        version++;
    }

    // --- sink --------------------------------------------------------------------------------------------------

    @Override
    public void hello(Payloads.Hello p) {
        serverHasMod = true;
        String id = za.co.neroland.neromaps.cache.TileDiskCache.safe(p.worldId());
        boolean changed = !id.equals(worldId);
        worldId = id;
        policy = p.policy();
        version++;
        if (changed) {
            ClientMap.reopenStore();
        }
    }

    @Override
    public void waypoints(Payloads.Waypoints p) {
        own = p.own();
        deaths = p.deaths();
        shared = p.shared();
        version++;
    }

    @Override
    public void shares(Payloads.Shares p) {
        boolean hadMap = shares != null && !shares.incoming().isEmpty();
        shares = p;
        if (hadMap && p.incoming().stream().noneMatch(l -> l.scope().kind() == ShareScope.Kind.EXPLORED_MAP)) {
            sharedTiles.clear(); // a revoked share: its terrain goes now
            sharedEtags.clear();
        }
        version++;
    }

    @Override
    public void positions(Payloads.Positions p) {
        positions = p.positions();
        version++;
    }

    @Override
    public void notice(Payloads.Notice p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Object[] args = new Object[p.args().size()];
        for (int i = 0; i < args.length; i++) {
            String a = p.args().get(i);
            args[i] = a.startsWith("neromaps.") ? Component.translatable(a) : Component.literal(a);
        }
        MutableComponent msg = Component.translatable(p.key(), args);
        if (p.requestId() != 0) {
            msg.append(" ").append(button("neromaps.share.button.accept", "/neromaps share accept " + p.requestId(),
                    ChatFormatting.GREEN));
            msg.append(" ").append(button("neromaps.share.button.decline", "/neromaps share decline " + p.requestId(),
                    ChatFormatting.RED));
        }
        PlayerMessages.chat(mc.player, msg);
    }

    /** Writes the server's export of this player's data to {@code <game>/neromaps/exports/} on this machine. */
    @Override
    public void export(Payloads.Export p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        String file = "neromaps-export-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
        try {
            byte[] json = za.co.neroland.neromaps.store.PlayerExport.inflate(p.deflated());
            java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("neromaps").resolve("exports");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(file), json);
            PlayerMessages.chat(mc.player, Component.translatable("neromaps.export.saved", "neromaps/exports/" + file));
        } catch (java.io.IOException | RuntimeException e) {
            PlayerMessages.chat(mc.player, Component.translatable("neromaps.export.failed"));
        }
    }

    private static MutableComponent button(String key, String command, ChatFormatting colour) {
        //? if >=1.21.5 {
        ClickEvent click = new ClickEvent.RunCommand(command);
        //?} else {
        /*ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .setStyle(Style.EMPTY.withColor(colour).withClickEvent(click));
    }

    @Override
    public void layerCatalogue(Payloads.LayerCatalogue p) {
        catalogue.clear();
        for (Payloads.LayerInfo l : p.layers()) {
            catalogue.put(l.id(), l);
        }
        version++;
    }

    @Override
    public void layerDelta(Payloads.LayerDelta p) {
        if (p.reset() || !p.dimension().equals(layerDimension)) {
            items.clear();
            layerDimension = p.dimension();
        }
        for (String k : p.removed()) {
            items.remove(k);
        }
        for (Payloads.LayerItem i : p.upserts()) {
            if (items.size() < Payloads.MAX_LAYER_ITEMS || items.containsKey(i.key())) {
                items.put(i.key(), i);
            }
        }
        version++;
    }

    @Override
    public void dimensionBadges(Payloads.DimensionBadges p) {
        badges = p.badges();
        version++;
    }

    @Override
    public void signpostOpen(Payloads.SignpostOpen p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ClientMap.setScreen(mc, new za.co.neroland.neromaps.client.screen.SignpostEditScreen(p));
        }
    }

    @Override
    public void tile(Payloads.TileData p) {
        WallTiles.accept(p);
        if (!p.dimension().equals(sharedTilesDim)) {
            sharedTiles.clear();
            sharedEtags.clear();
            sharedTilesDim = p.dimension();
        }
        long key = TileKey.tile(p.lod(), p.tx(), p.tz());
        if (p.data().length == 0) {
            return; // unchanged
        }
        try {
            Tile t = TileCodec.decode(p.data());
            sharedTiles.put(key, t);
            sharedEtags.put(key, p.etag());
            while (sharedTiles.size() > MAX_SHARED_TILES) {
                Long eldest = sharedTiles.keySet().iterator().next();
                sharedTiles.remove(eldest);
                sharedEtags.remove(eldest);
            }
        } catch (TileCorruptException e) {
            // a malformed tile is dropped; nothing else happens
        }
    }

    @Override
    public void perf(Payloads.Perf p) {
        Minecraft mc = Minecraft.getInstance();
        PerfOverlay.serverLines = List.copyOf(p.serverLines());
        switch (p.op()) {
            case Payloads.Perf.TOGGLE -> {
                PerfOverlay.enabled = !PerfOverlay.enabled;
                if (mc.player != null) {
                    PlayerMessages.chat(mc.player, Component.translatable(PerfOverlay.enabled
                            ? "gui.neromaps.perf.on" : "gui.neromaps.perf.off"));
                }
            }
            case Payloads.Perf.RESET -> {
                PerfCounters.reset();
                if (mc.player != null) {
                    PlayerMessages.chat(mc.player, Component.translatable("gui.neromaps.perf.reset"));
                }
            }
            default -> {
                if (mc.player != null) {
                    for (String line : PerfOverlay.lines()) {
                        PlayerMessages.chat(mc.player, Component.literal(line));
                    }
                    for (String line : p.serverLines()) {
                        PlayerMessages.chat(mc.player, Component.literal("[server] " + line));
                    }
                }
            }
        }
    }

    // --- marker list for the minimap (rebuilt on change, read every frame without allocating) -----------------

    public static final int MARKERS = 256;
    public final double[] markerX = new double[MARKERS];
    public final double[] markerZ = new double[MARKERS];
    public final int[] markerColour = new int[MARKERS];
    public int markerCount;
    private int markerVersion = -1;
    private String markerDim = "";
    private int markerSettings = -1;

    /** Rebuilds the minimap markers for {@code dimension} if anything changed. */
    public void refreshMarkers(String dimension, za.co.neroland.neromaps.settings.MinimapSettings s) {
        if (markerVersion == version && dimension.equals(markerDim) && markerSettings == s.version) {
            return;
        }
        markerVersion = version;
        markerDim = dimension;
        markerSettings = s.version;
        int n = 0;
        if (s.waypointsOnMinimap) {
            for (Waypoint w : own) {
                n = add(n, w, dimension, 0xFF000000 | w.colour());
            }
            for (Waypoint w : temporary) {
                n = add(n, w, dimension, 0xFF000000 | w.colour());
            }
            for (Payloads.SharedWaypoint sw : shared) {
                n = add(n, sw.waypoint(), dimension, 0xFF000000 | sw.waypoint().colour());
            }
        }
        if (s.deathPointsShown) {
            for (DeathPoint d : deaths) {
                if (d.dimension().equals(dimension) && n < MARKERS) {
                    markerX[n] = d.x() + 0.5;
                    markerZ[n] = d.z() + 0.5;
                    markerColour[n++] = 0xFFD55E00;
                }
            }
        }
        for (Payloads.Position p : positions) {
            if (p.known() && p.dimension().equals(dimension) && n < MARKERS) {
                markerX[n] = p.x() + 0.5;
                markerZ[n] = p.z() + 0.5;
                markerColour[n++] = 0xFF56B4E9;
            }
        }
        if (dimension.equals(layerDimension)) {
            for (Payloads.LayerItem i : items.values()) {
                if (n >= MARKERS) {
                    break;
                }
                Payloads.LayerInfo info = catalogue.get(i.layer());
                if (i.shape() != 0 || info == null || !s.layerOnMinimap(i.layer(), info.defaultOnMinimap())) {
                    continue;
                }
                markerX[n] = i.x() + 0.5;
                markerZ[n] = i.z() + 0.5;
                markerColour[n++] = i.argb() == 0 ? 0xFFF2F5F8 : 0xFF000000 | i.argb();
            }
        }
        for (int k = 0; k < n; k++) {
            markerColour[k] = za.co.neroland.neromaps.colour.MapPalette.marker(markerColour[k], s.markerPalette);
        }
        markerCount = n;
    }

    private int add(int n, Waypoint w, String dimension, int argb) {
        if (n < MARKERS && w.dimension().equals(dimension)) {
            markerX[n] = w.x() + 0.5;
            markerZ[n] = w.z() + 0.5;
            markerColour[n] = argb;
            return n + 1;
        }
        return n;
    }
}
