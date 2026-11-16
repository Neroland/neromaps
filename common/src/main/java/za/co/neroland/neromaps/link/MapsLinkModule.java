package za.co.neroland.neromaps.link;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.server.tiles.LinkTiles;

/**
 * NeroMaps' plug into Neroland Core's link API: module {@code neromaps}, schema 1 (decision 011; the contract in the
 * companion app's maps module). Registered last from common init and failure-tolerant: a Core without the link API, or
 * any error here, leaves the rest of NeroMaps untouched. The {@code tiles} route is a bulk route on the bridge (its
 * default {@code bulkSnapshotRoutes} lists {@code neromaps/tiles}), so tile traffic draws from its own per-device
 * budget; NeroMaps declares nothing for that and also caps tile bytes per player itself.
 */
public final class MapsLinkModule {

    public static final String MODULE_ID = NeroMapsCommon.MOD_ID;
    /** Bump on any change to an existing field's shape; additive changes keep 1. */
    public static final int SCHEMA_VERSION = 1;
    public static final List<String> SECTIONS =
            List.of("dimensions", "tiles", "palette", "waypoints", "layers", "position", "shares");
    public static final List<String> ACTIONS = List.of("waypoint_add", "waypoint_edit", "waypoint_delete",
            "share_offer", "share_accept", "share_decline", "share_revoke");

    private static volatile @Nullable MinecraftServer server;
    private static volatile @Nullable MapsPalette palette;
    private static @Nullable MapsLinkActions actions;

    private MapsLinkModule() {
    }

    /** Registers the read and write surfaces with Core. Never throws. */
    public static void init() {
        try {
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION, SECTIONS, ACTIONS);
            NeroLinkRegistry.registerSnapshotProvider(new MapsLinkSnapshots(MapsLinkModule::world, () -> palette), info);
            MapsLinkActions a = new MapsLinkActions(MapsLinkModule::world);
            actions = a;
            NeroLinkRegistry.registerActionHandler(a, info);
        } catch (RuntimeException | LinkageError e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] link module not registered ({})", e.getClass().getName());
        }
    }

    /** From the server tick: remembers the server and builds the palette once per server, off the thread. */
    public static void tick(MinecraftServer s) {
        if (server != s) {
            server = s;
            palette = null;
            CompletableFuture.runAsync(() -> {
                try {
                    palette = MapsPalette.load(s);
                } catch (RuntimeException | LinkageError e) {
                    NeroMapsCommon.LOGGER.debug("[NeroMaps] link palette not built ({})", e.getClass().getName());
                }
            });
        }
    }

    public static void stopping() {
        server = null;
        palette = null;
        MapsLinkActions a = actions;
        if (a != null) {
            a.reset();
        }
    }

    /** Erasure: forget the player's in-memory link state. */
    public static void erased(UUID player) {
        MapsLinkActions a = actions;
        if (a != null) {
            a.forget(player);
        }
        LinkTiles.LIVE.forget(player);
    }

    private static @Nullable MapsLinkWorld world() {
        MinecraftServer s = server;
        return s == null ? null : new LiveMapsWorld(s);
    }

    private static String modVersion() {
        try {
            String v = Services.PLATFORM.getModVersion();
            return v == null || v.isBlank() ? "unknown" : v;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }
}
