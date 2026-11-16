package za.co.neroland.neromaps.server.layers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapMarker;

import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.tile.PerfLog;

class LayerItemsTest {

    static final ResourceKey<Level> OW = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    static final Identifier PADS = Identifier.fromNamespaceAndPath("nerospace", "pads");
    static final Identifier BORDER = Identifier.fromNamespaceAndPath("nerocolonies", "border");

    static Payloads.LayerItem marker(String id, int x) {
        return LayerItems.of(MapMarker.builder(id, PADS, OW, x, 64, 0).label(MapLabel.translatable("map.pad", "A"))
                .argb(0xFF00FF00).build(), Identifier.fromNamespaceAndPath("nerospace", "pad"));
    }

    @Test
    void convertsMarkersAndAreas() {
        Payloads.LayerItem m = marker("p1", 10);
        assertEquals("nerospace:pads", m.layer());
        assertEquals("nerospace:pad", m.icon(), "a marker without its own icon uses the layer's");
        assertEquals(List.of("A"), m.args());
        MapArea chunks = new MapArea.Chunks("c1", BORDER, OW, MapAreaStyle.of(0x30FF0000, 0xFFFF0000),
                new long[] {MapArea.Chunks.pack(2, -3), MapArea.Chunks.pack(4, 5)});
        Payloads.LayerItem a = LayerItems.of(chunks);
        assertEquals(3, a.shape());
        assertEquals(4, a.coords().length);
        assertEquals(2, a.coords()[0]);
        assertEquals(-3, a.coords()[1]);
        Payloads.LayerItem r = LayerItems.of(new MapArea.Rect("r", BORDER, OW, MapAreaStyle.of(1, 2), 10, 20, 0, 0));
        assertEquals(1, r.shape());
        assertEquals(0, r.coords()[0], "normalised");
    }

    @Test
    void standingDetailBecomesABadgeAndDeviceLayerStartsOffTheMinimap() {
        Identifier outposts = Identifier.fromNamespaceAndPath("nerofactions", "outposts");
        Payloads.LayerItem o = LayerItems.of(MapMarker.builder("o1", outposts, OW, 0, 64, 0)
                .detail(MapLabel.translatable("map.nerofactions.standing.hostile")).argb(0xFFD55E00).build(), null);
        assertEquals("neromaps:badge_hostile", o.badge());
        assertEquals("", marker("p", 1).badge());
        assertEquals("", LayerItems.badge(List.of(MapLabel.translatable("map.x.standing.weird"))));
        assertTrue(LayerItems.MINIMAP_OFF_BY_DEFAULT.contains("nerosecurity:installation_devices"));
    }

    @Test
    void warZoneSideIsCarriedByAGlyphNotColourAlone() {
        assertEquals("neromaps:badge_side_a",
                LayerItems.badge(List.of(MapLabel.translatable("map.neroevents.war.side_a"))));
        assertEquals("neromaps:badge_side_b",
                LayerItems.badge(List.of(MapLabel.translatable("map.neroevents.war.side_b"))));
        assertEquals("neromaps:badge_side_none",
                LayerItems.badge(List.of(MapLabel.translatable("map.neroevents.war.side_none"))));
        assertEquals("neromaps:generic",
                LayerItems.badge(List.of(MapLabel.translatable("map.neroevents.war.side_c"))));
    }

    @Test
    void deltaSendsOnlyChangesAndRemovesWhatIsGone() {
        Map<String, Payloads.LayerItem> sent = new HashMap<>();
        Map<String, Payloads.LayerItem> now = new LinkedHashMap<>();
        for (int i = 0; i < 64; i++) {
            Payloads.LayerItem it = marker("p" + i, i);
            now.put(it.key(), it);
        }
        LayerItems.Delta d1 = LayerItems.diff(sent, now, Set.of());
        assertEquals(64, d1.upserts().size());
        LayerItems.Delta d2 = LayerItems.diff(sent, new LinkedHashMap<>(now), Set.of());
        assertTrue(d2.isEmpty(), "no change, nothing sent");
        Map<String, Payloads.LayerItem> next = new LinkedHashMap<>(now);
        Payloads.LayerItem moved = marker("p3", 999);
        next.put(moved.key(), moved);
        next.remove(marker("p5", 5).key());
        LayerItems.Delta d3 = LayerItems.diff(sent, next, Set.of());
        assertEquals(1, d3.upserts().size());
        assertEquals(List.of(marker("p5", 5).key()), d3.removed());
        // a provider skipped for being over budget keeps its items
        LayerItems.Delta d4 = LayerItems.diff(sent, new LinkedHashMap<>(), Set.of("nerospace:pads"));
        assertTrue(d4.removed().isEmpty());
        assertEquals(63, sent.size());
        LayerItems.Delta d5 = LayerItems.diff(sent, new LinkedHashMap<>(), Set.of());
        assertEquals(63, d5.removed().size(), "membership removed: everything goes");
        // M8: bytes of a delta with 64 and 512 markers
        for (int n : new int[] {64, 512}) {
            List<Payloads.LayerItem> items = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(marker("p" + i, i));
            }
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            Payloads.LayerDelta.STREAM_CODEC.encode(new net.minecraft.network.FriendlyByteBuf(buf),
                    new Payloads.LayerDelta("minecraft:overworld", false, items, List.of()));
            PerfLog.line("M8 layer delta " + n + " markers: " + buf.readableBytes() + " B");
        }
    }
}
