package za.co.neroland.neromaps.server.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapMarker;

import za.co.neroland.neromaps.network.Payloads;

/**
 * Conversion of Core map items to the wire form, and the per-viewer delta (decision 001, consumer side). Pure:
 * no server, no player. Detail lines are not sent in 0.1.0 (the label is).
 */
public final class LayerItems {

    private LayerItems() {
    }

    public static Payloads.LayerItem of(MapMarker m, @Nullable Identifier layerIcon) {
        Identifier icon = m.icon() != null ? m.icon() : layerIcon;
        MapLabel l = m.label();
        return new Payloads.LayerItem(m.layer().toString(), m.id(), 0, m.x(), m.y(), m.z(),
                icon == null ? "" : icon.toString(), l == null ? "" : l.text(), l != null && l.literal(),
                l == null ? List.of() : l.args(), m.argb(), 0, 0, new int[0], badge(m.details()));
    }

    /**
     * A standing badge from a detail line: {@code MapMarker} has no badge field, so a provider that shows the viewer's
     * standing (NeroFactions' outposts) sends it as a detail lang key ending {@code .standing.<hostile|neutral|
     * friendly>}; it maps to the atlas badges {@code neromaps:badge_*}. Empty when there is none.
     */
    public static String badge(List<MapLabel> details) {
        for (MapLabel d : details) {
            if (d.literal()) {
                continue;
            }
            String t = d.text();
            int i = t.lastIndexOf(".standing.");
            if (i >= 0) {
                String kind = t.substring(i + ".standing.".length());
                if (kind.equals("hostile") || kind.equals("neutral") || kind.equals("friendly")) {
                    return "neromaps:badge_" + kind;
                }
            }
            // NeroEvents war zones: which side holds the zone, carried by a glyph as well as the detail text so the
            // side is never shown by colour alone. Each side has its own atlas badge (distinct glyphs, not just hues).
            int w = t.lastIndexOf(".war.side_");
            if (w >= 0) {
                return switch (t.substring(w + ".war.side_".length())) {
                    case "a" -> "neromaps:badge_side_a";
                    case "b" -> "neromaps:badge_side_b";
                    case "none" -> "neromaps:badge_side_none";
                    default -> "neromaps:generic";
                };
            }
        }
        return "";
    }

    /**
     * Consumer-side default overrides for layers whose provider default does not suit the minimap: device markers
     * crowd it, so they default to the world map only. The player can still turn them on.
     */
    public static final java.util.Set<String> MINIMAP_OFF_BY_DEFAULT = java.util.Set.of(
            "nerosecurity:installation_devices");

    public static Payloads.LayerItem of(MapArea a) {
        MapBounds b = a.bounds();
        MapLabel l = a.style().label();
        int shape;
        int[] coords;
        if (a instanceof MapArea.Rect r) {
            shape = 1;
            coords = new int[] {r.minX(), r.minZ(), r.maxX(), r.maxZ()};
        } else if (a instanceof MapArea.Circle c) {
            shape = 2;
            coords = new int[] {c.centreX(), c.centreZ(), c.radius()};
        } else if (a instanceof MapArea.Chunks ch) {
            shape = 3;
            long[] packed = ch.chunks();
            coords = new int[packed.length * 2];
            for (int i = 0; i < packed.length; i++) {
                coords[i * 2] = MapArea.Chunks.chunkX(packed[i]);
                coords[i * 2 + 1] = MapArea.Chunks.chunkZ(packed[i]);
            }
        } else if (a instanceof MapArea.Polygon p) {
            shape = 4;
            coords = p.xz();
        } else {
            shape = 1;
            coords = new int[] {b.minX(), b.minZ(), b.maxX(), b.maxZ()};
        }
        return new Payloads.LayerItem(a.layer().toString(), a.id(), shape, b.centreX(), 0, b.centreZ(), "",
                l == null ? "" : l.text(), l != null && l.literal(), l == null ? List.of() : l.args(), 0,
                a.style().fillArgb(), a.style().borderArgb(), coords, "");
    }

    /** The changes from {@code sent} to {@code now}: upserts (new or changed) and removed keys. */
    public record Delta(List<Payloads.LayerItem> upserts, List<String> removed) {
        public boolean isEmpty() {
            return upserts.isEmpty() && removed.isEmpty();
        }
    }

    /**
     * Computes the delta and updates {@code sent} to match {@code now}. Keys of layers in {@code keepLayers} that are
     * missing from {@code now} are kept as they were (a provider skipped this refresh for being over budget).
     */
    public static Delta diff(Map<String, Payloads.LayerItem> sent, Map<String, Payloads.LayerItem> now,
            java.util.Set<String> keepLayers) {
        List<Payloads.LayerItem> up = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, Payloads.LayerItem> e : now.entrySet()) {
            Payloads.LayerItem old = sent.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) {
                up.add(e.getValue());
            }
        }
        Map<String, Payloads.LayerItem> next = new HashMap<>(now);
        for (Map.Entry<String, Payloads.LayerItem> e : sent.entrySet()) {
            if (!now.containsKey(e.getKey())) {
                if (keepLayers.contains(e.getValue().layer())) {
                    next.put(e.getKey(), e.getValue());
                } else {
                    removed.add(e.getKey());
                }
            }
        }
        sent.clear();
        sent.putAll(next);
        return new Delta(up, removed);
    }
}
