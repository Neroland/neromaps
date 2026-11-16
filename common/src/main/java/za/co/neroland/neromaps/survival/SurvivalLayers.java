package za.co.neroland.neromaps.survival;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapViewer;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.block.MapBlockProperties;

/**
 * NeroMaps' own public layers through Core's map API, so they reach the minimap, the world map and the link module
 * exactly like a sibling's: {@code neromaps:signposts} (signposts set to show on maps; proximity 128, the widest Core
 * allows) and {@code neromaps:trail_markers} (proximity 64). Neither block has an owner, so proximity from the
 * viewer's own server-side position is the scope (the API's rule for ownerless records). Labels are the signpost's
 * own public text; nothing names who placed or wrote anything.
 */
public final class SurvivalLayers implements MapLayerProvider {

    public static final Identifier SIGNPOSTS = Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "signposts");
    public static final Identifier TRAIL_MARKERS = Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID,
            "trail_markers");
    private static final Identifier SIGNPOST_ICON = Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "signpost");
    private static final Identifier TRAIL_ICON = Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID,
            "trail_marker");
    private static final int SIGNPOST_RADIUS = MapLayerInfo.MAX_PROXIMITY;
    private static final int TRAIL_RADIUS = MapLayerInfo.BUILT_PROXIMITY;

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.proximityBuilt(SIGNPOSTS, "gui.neromaps.settings.layers.layer.signposts", SIGNPOST_ICON)
                    .withProximityRadius(SIGNPOST_RADIUS).withDefaults(true, true),
            MapLayerInfo.proximityBuilt(TRAIL_MARKERS, "gui.neromaps.settings.layers.layer.trail_markers", TRAIL_ICON)
                    .withDefaults(true, true));

    private static boolean registered;

    /** Common init step 5. */
    public static void register() {
        if (!registered) {
            registered = true;
            MapLayerRegistry.register(new SurvivalLayers());
        }
    }

    @Override
    public String modId() {
        return NeroMapsCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        String dim = dimension.identifier().toString();
        for (PlacedIndex.Entry e : PlacedIndex.entries(dim, PlacedIndex.Kind.SIGNPOST)) {
            if (sink.isFull(SIGNPOSTS)) {
                break;
            }
            int x = e.pos().getX();
            int z = e.pos().getZ();
            if (!e.shown() || !bounds.contains(x, z) || !viewer.within(dimension, x, z, SIGNPOST_RADIUS)) {
                continue;
            }
            MapMarker.Builder b = MapMarker.builder("signpost/" + e.pos().asLong(), SIGNPOSTS, dimension, e.pos())
                    .icon(SIGNPOST_ICON).argb(0xFF000000 | MapBlockProperties.MARKER_COLOURS[e.colour()]);
            b.label(e.label().isEmpty() ? MapLabel.translatable("block.neromaps.signpost")
                    : MapLabel.literal(e.label()));
            sink.marker(b.build());
        }
        for (PlacedIndex.Entry e : PlacedIndex.entries(dim, PlacedIndex.Kind.TRAIL_MARKER)) {
            if (sink.isFull(TRAIL_MARKERS)) {
                break;
            }
            int x = e.pos().getX();
            int z = e.pos().getZ();
            if (!bounds.contains(x, z) || !viewer.within(dimension, x, z, TRAIL_RADIUS)) {
                continue;
            }
            sink.marker(MapMarker.builder("trail/" + e.pos().asLong(), TRAIL_MARKERS, dimension, e.pos())
                    .icon(TRAIL_ICON).argb(0xFF000000 | MapBlockProperties.MARKER_COLOURS[e.colour()]).build());
        }
    }
}
