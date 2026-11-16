package za.co.neroland.neromaps.link;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

import za.co.neroland.neromaps.server.layers.LayerService;

/**
 * The link {@code layers} section body: every registered provider is asked through Core's guarded
 * {@link MapLayerRegistry#query} <b>as the requesting player</b> (their own {@link MapViewer}, online or offline), so
 * scoping is the providers' and Core's, exactly as in game. NeroMaps adds the caps: at most {@link #MAX_ITEMS} markers
 * plus areas and about {@link #MAX_LAYER_BYTES} of entries per answer, and at most {@link #MAX_CHUNK_SQUARES} squares
 * per chunk-set area. Pure apart from the registry, so the section is testable without a server.
 */
final class LinkLayers {

    /** Layer items per response (as in game). */
    static final int MAX_ITEMS = LayerService.MAX_ITEMS;
    /** Byte budget of the entries in one {@code layers} answer (the bridge page is 256 KiB). */
    static final int MAX_LAYER_BYTES = 240 * 1024;
    /** Squares sent for one chunk-set area. */
    static final int MAX_CHUNK_SQUARES = 256;

    private LinkLayers() {
    }

    /**
     * The layers {@code viewer} may see in {@code dimension} within {@code bounds} (clamped to
     * {@link MapBounds#MAX_SPAN}). {@code translate} resolves lang keys (the server's language table live).
     */
    static JsonArray build(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds,
            UnaryOperator<String> translate) {
        MapBounds clamped = bounds.clampSpan();
        Map<String, JsonObject> byLayer = new LinkedHashMap<>();
        Map<String, MapLayerInfo> infos = new LinkedHashMap<>();
        for (MapLayerInfo info : MapLayerRegistry.layers()) {
            infos.put(info.id().toString(), info);
        }
        int items = 0;
        int bytes = 0;
        for (MapLayerProvider provider : MapLayerRegistry.providers()) {
            MapQueryResult r = MapLayerRegistry.query(provider.modId(), viewer, dimension, clamped);
            for (MapLayerInfo info : infos.values()) {
                if (info.modId().equals(provider.modId())) {
                    layer(byLayer, info, provider.modId(), translate);
                }
            }
            if (r.failed()) {
                continue;
            }
            for (MapMarker m : r.markers()) {
                if (items >= MAX_ITEMS || bytes >= MAX_LAYER_BYTES) {
                    break;
                }
                JsonObject l = byLayer.get(m.layer().toString());
                if (l == null) {
                    continue;
                }
                JsonObject e = new JsonObject();
                e.addProperty("id", m.id());
                e.addProperty("x", m.x());
                e.addProperty("y", m.y());
                e.addProperty("z", m.z());
                Identifier icon = m.icon();
                MapLayerInfo info = infos.get(m.layer().toString());
                if (icon == null && info != null) {
                    icon = info.icon();
                }
                e.addProperty("icon", icon == null ? "neromaps:generic" : icon.toString());
                e.addProperty("colour", colour(m.argb()));
                e.addProperty("label", text(m.label(), translate));
                l.getAsJsonArray("markers").add(e);
                items++;
                bytes += e.toString().length();
            }
            for (MapArea a : r.areas()) {
                if (items >= MAX_ITEMS || bytes >= MAX_LAYER_BYTES) {
                    break;
                }
                JsonObject l = byLayer.get(a.layer().toString());
                if (l == null) {
                    continue;
                }
                MapLayerInfo info = infos.get(a.layer().toString());
                boolean hazard = info != null && info.proximityRadius() == MapLayerInfo.HAZARD_PROXIMITY;
                for (JsonObject e : areas(a, hazard, translate)) {
                    l.getAsJsonArray("areas").add(e);
                    items++;
                    bytes += e.toString().length();
                    if (items >= MAX_ITEMS || bytes >= MAX_LAYER_BYTES) {
                        break;
                    }
                }
            }
        }
        JsonArray out = new JsonArray();
        byLayer.values().forEach(out::add);
        return out;
    }

    private static void layer(Map<String, JsonObject> byLayer, MapLayerInfo info, String mod,
            UnaryOperator<String> translate) {
        String id = info.id().toString();
        if (byLayer.containsKey(id)) {
            return;
        }
        JsonObject l = new JsonObject();
        l.addProperty("id", id);
        l.addProperty("mod", mod);
        l.addProperty("name", translate.apply(info.titleKey()));
        l.addProperty("scope", scope(info.scope()));
        l.addProperty("locked", false);
        l.add("markers", new JsonArray());
        l.add("areas", new JsonArray());
        byLayer.put(id, l);
    }

    static String scope(MapScope s) {
        return switch (s) {
            case OWNER -> "own";
            case MEMBERS -> "team";
            case PROXIMITY -> "proximity";
            case DISCOVERED -> "own";
            default -> "public";
        };
    }

    private static String colour(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    private static String text(@Nullable MapLabel l, UnaryOperator<String> translate) {
        if (l == null) {
            return "";
        }
        if (l.literal()) {
            return l.text();
        }
        String pattern = translate.apply(l.text());
        try {
            return l.args().isEmpty() ? pattern : String.format(java.util.Locale.ROOT, pattern, l.args().toArray());
        } catch (java.util.IllegalFormatException e) {
            return pattern;
        }
    }

    private static List<JsonObject> areas(MapArea a, boolean hazard, UnaryOperator<String> translate) {
        List<JsonObject> out = new ArrayList<>();
        String label = text(a.style().label(), translate);
        String colour = colour(a.style().fillArgb() != 0 ? a.style().fillArgb() : a.style().borderArgb());
        if (a instanceof MapArea.Rect r) {
            out.add(area(r.id(), new int[] {r.minX(), r.minZ(), r.maxX(), r.minZ(), r.maxX(), r.maxZ(), r.minX(),
                    r.maxZ()}, colour, hazard, label));
        } else if (a instanceof MapArea.Circle c) {
            int[] pts = new int[32];
            for (int i = 0; i < 16; i++) {
                double t = Math.PI * 2 * i / 16;
                pts[i * 2] = (int) Math.round(c.centreX() + Math.cos(t) * c.radius());
                pts[i * 2 + 1] = (int) Math.round(c.centreZ() + Math.sin(t) * c.radius());
            }
            out.add(area(c.id(), pts, colour, hazard, label));
        } else if (a instanceof MapArea.Polygon p) {
            out.add(area(p.id(), p.xz(), colour, hazard, label));
        } else if (a instanceof MapArea.Chunks ch) {
            long[] chunks = ch.chunks();
            for (int i = 0; i < chunks.length && i < MAX_CHUNK_SQUARES; i++) {
                int x = MapArea.Chunks.chunkX(chunks[i]) << 4;
                int z = MapArea.Chunks.chunkZ(chunks[i]) << 4;
                out.add(area(ch.id() + "#" + i, new int[] {x, z, x + 16, z, x + 16, z + 16, x, z + 16}, colour,
                        hazard, label));
            }
        }
        return out;
    }

    private static JsonObject area(String id, int[] xz, String colour, boolean hazard, String label) {
        JsonObject e = new JsonObject();
        e.addProperty("id", id);
        JsonArray pts = new JsonArray();
        for (int i = 0; i + 1 < xz.length; i += 2) {
            JsonArray pt = new JsonArray(2);
            pt.add(xz[i]);
            pt.add(xz[i + 1]);
            pts.add(pt);
        }
        e.add("points", pts);
        e.addProperty("colour", colour);
        e.addProperty("hazard", hazard);
        e.addProperty("label", label);
        return e;
    }
}
