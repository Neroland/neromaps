package za.co.neroland.neromaps.link;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;

import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapViewer;

import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.server.MapServer;
import za.co.neroland.neromaps.server.ServerHandlers;
import za.co.neroland.neromaps.server.layers.LayerService;
import za.co.neroland.neromaps.server.tiles.CaptureService;
import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerRecord;
import za.co.neroland.neromaps.store.PlayerStore;

/** {@link MapsLinkWorld} over the running server. Server thread only. */
final class LiveMapsWorld implements MapsLinkWorld {

    private final MinecraftServer server;

    LiveMapsWorld(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public MapPlayerState state() {
        return PlayerStore.get(server);
    }

    @Override
    public LinkTiles tiles() {
        return LinkTiles.LIVE;
    }

    @Override
    public boolean publicMap() {
        return MapServer.policy().publicMap();
    }

    @Override
    public boolean capture() {
        return MapServer.policy().serverCapture();
    }

    @Override
    public int waypointLimit() {
        return NeroMapsConfig.waypointLimit();
    }

    @Override
    public String denyList() {
        return NeroMapsConfig.nameDenyList();
    }

    @Override
    public List<Dim> dimensions() {
        List<Dim> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.add(new Dim(level.dimension().identifier().toString(), level.getSeaLevel(),
                    level.dimensionType().hasCeiling()));
        }
        return out;
    }

    @Override
    public @Nullable Online online(UUID player) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p == null) {
            return null;
        }
        return new Online(MapServer.nameOf(server, player), MapServer.dimension(p), p.getX(), p.getY(), p.getZ(),
                p.getYRot(), MapServer.shareable(p));
    }

    @Override
    public @Nullable UUID onlineByName(String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        return p == null ? null : p.getUUID();
    }

    @Override
    public String nameOf(UUID player) {
        return MapServer.nameOf(server, player);
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long today() {
        return MapServer.today();
    }

    @Override
    public void ensureTiles() {
        CaptureService.noteDemand();
        CaptureService.ensureRunning(server);
    }

    @Override
    public void changed(Change change, UUID... players) {
        switch (change) {
            case WAYPOINTS -> {
                for (int i = 0; i < players.length; i++) {
                    MapServer.refresh(server, players[i]);
                    if (i == 0) {
                        MapsLinkEvents.waypoints(players[i]);
                    } else {
                        MapsLinkEvents.shares(players[i]); // a waypoint shared with them changed
                    }
                }
            }
            case SHARES -> {
                for (UUID id : players) {
                    MapServer.refresh(server, id);
                    LayerService.sharesChanged(id);
                    MapsLinkEvents.shares(id);
                }
            }
            case SHARE_REQUEST -> {
                for (UUID id : players) {
                    MapServer.refresh(server, id);
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    PlayerRecord r = state().find(id).orElse(null);
                    if (p != null && r != null) {
                        MapNetwork.send(p, Payloads.Notice.of("neromaps.share.pending",
                                Integer.toString(r.incoming().size())));
                    }
                    MapsLinkEvents.shareRequest(id);
                }
            }
            case REVOKED -> {
                if (players.length == 2) {
                    ServerHandlers.afterRevoke(server, players[0], players[1]);
                }
                for (UUID id : players) {
                    MapsLinkEvents.shares(id);
                }
            }
        }
    }

    // --- layers -----------------------------------------------------------------------------------------------

    @Override
    public JsonArray layers(UUID viewerId, String dim, int x0, int z0, int x1, int z1, boolean[] online) {
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(dim)) {
                level = l;
                break;
            }
        }
        if (level == null) {
            return new JsonArray();
        }
        ServerPlayer p = server.getPlayerList().getPlayer(viewerId);
        online[0] = p != null;
        // as that requester: proximity layers answer only for an online viewer in range (Core enforces it)
        MapViewer viewer = p != null ? MapViewer.of(p) : MapViewer.offline(server, viewerId);
        return LinkLayers.build(viewer, level.dimension(), new MapBounds(x0, z0, x1, z1),
                Language.getInstance()::getOrDefault);
    }
}
