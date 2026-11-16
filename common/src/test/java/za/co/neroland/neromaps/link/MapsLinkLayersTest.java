package za.co.neroland.neromaps.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * The link {@code layers} section end to end (snapshot, live conversion, Core's guarded registry): it is asked as the
 * requesting player, a stranger gets nothing of anyone else's, proximity follows the requester's own server-side
 * position (never the requested bounds) and the answer stays inside its caps.
 */
class MapsLinkLayersTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    /** The only viewer the bulk provider answers for (so the cap test does not touch the others). */
    static final UUID HOARDER = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    static final String OW = FakeWorld.OW;
    static final String NS = "neromapslinktest";
    static final String BULK_NS = "neromapslinkbulk";
    static final Identifier BASES = Identifier.fromNamespaceAndPath(NS, "bases");
    static final Identifier STORMS = Identifier.fromNamespaceAndPath(NS, "storms");

    /** One owned record per player plus an unowned hazard at the origin: a well-behaved sibling provider. */
    static final class OwnedProvider implements MapLayerProvider {
        final Map<UUID, int[]> bases = Map.of(A, new int[] {100, 64, 100}, B, new int[] {-100, 64, -100});

        @Override
        public String modId() {
            return NS;
        }

        @Override
        public List<MapLayerInfo> layers() {
            return List.of(MapLayerInfo.of(BASES, "layer.neromapslinktest.bases", BASES, MapScope.OWNER),
                    MapLayerInfo.proximityHazard(STORMS, "layer.neromapslinktest.storms", STORMS));
        }

        @Override
        public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
            if (!dimension.equals(FakeWorld.key(OW))) {
                return;
            }
            int[] mine = bases.get(viewer.id());
            if (mine != null) {
                sink.marker(MapMarker.builder("base_" + (viewer.id().equals(A) ? "a" : "b"), BASES, dimension,
                        mine[0], mine[1], mine[2]).label(MapLabel.literal("My base")).build());
            }
            MapArea.Rect storm = new MapArea.Rect("storm_1", STORMS, dimension, MapAreaStyle.of(0x80D55E00, 0),
                    -20, -20, 20, 20);
            if (viewer.within(dimension, storm.bounds(), MapLayerInfo.HAZARD_PROXIMITY)) {
                sink.area(storm);
            }
        }
    }

    /** Floods three layers to their per-layer cap plus huge chunk areas, for {@link #HOARDER} only. */
    static final class BulkProvider implements MapLayerProvider {
        @Override
        public String modId() {
            return BULK_NS;
        }

        @Override
        public List<MapLayerInfo> layers() {
            return List.of(info("one"), info("two"), info("three"), info("land"));
        }

        static MapLayerInfo info(String path) {
            Identifier id = Identifier.fromNamespaceAndPath(BULK_NS, path);
            return MapLayerInfo.of(id, "layer." + BULK_NS + "." + path, id, MapScope.OWNER)
                    .withCap(MapLayerInfo.HARD_CAP);
        }

        @Override
        public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
            if (!viewer.id().equals(HOARDER)) {
                return;
            }
            for (String path : List.of("one", "two", "three")) {
                Identifier layer = Identifier.fromNamespaceAndPath(BULK_NS, path);
                for (int i = 0; !sink.isFull(layer); i++) {
                    sink.marker(MapMarker.builder(path + "_" + i, layer, dimension, i % 400, 64, i / 400)
                            .label(MapLabel.literal("x".repeat(MapLabel.MAX_LITERAL))).build());
                }
            }
            Identifier land = Identifier.fromNamespaceAndPath(BULK_NS, "land");
            for (int a = 0; a < 4 && !sink.isFull(land); a++) {
                long[] chunks = new long[MapArea.Chunks.MAX_CHUNKS];
                for (int i = 0; i < chunks.length; i++) {
                    chunks[i] = MapArea.Chunks.pack(a * 32 + (i & 31), i >> 5);
                }
                sink.area(new MapArea.Chunks("land_" + a, land, dimension, MapAreaStyle.of(0x8000FF00, 0), chunks));
            }
        }
    }

    @BeforeAll
    static void register() {
        // the registry is global and has no unregister; both providers are scope-correct and answer
        // nothing to the viewers other tests use
        if (MapLayerRegistry.provider(NS).isEmpty()) {
            MapLayerRegistry.register(new OwnedProvider());
        }
        if (MapLayerRegistry.provider(BULK_NS).isEmpty()) {
            MapLayerRegistry.register(new BulkProvider());
        }
    }

    FakeWorld w;
    MapsLinkSnapshots snap;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        MapsPalette pal = MapsPalette.build(Map.of("minecraft:stone", 0x7d7d7d), List.of());
        snap = new MapsLinkSnapshots(() -> w, () -> pal);
    }

    JsonObject layers(UUID who, int x0, int z0, int x1, int z1) {
        Map<String, String> q = new HashMap<>();
        q.put("dim", OW);
        q.put("x0", Integer.toString(x0));
        q.put("z0", Integer.toString(z0));
        q.put("x1", Integer.toString(x1));
        q.put("z1", Integer.toString(z1));
        return snap.snapshot(who, "layers", q);
    }

    static JsonObject layer(JsonObject section, Identifier id) {
        for (JsonElement e : section.getAsJsonArray("layers")) {
            JsonObject l = e.getAsJsonObject();
            if (l.get("id").getAsString().equals(id.toString())) {
                return l;
            }
        }
        return null;
    }

    static int items(JsonObject section, String namespace) {
        int n = 0;
        for (JsonElement e : section.getAsJsonArray("layers")) {
            JsonObject l = e.getAsJsonObject();
            if (namespace == null || l.get("mod").getAsString().equals(namespace)) {
                n += l.getAsJsonArray("markers").size() + l.getAsJsonArray("areas").size();
            }
        }
        return n;
    }

    @Test
    void eachRequesterGetsOnlyTheirOwnEntries() {
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, 0, 64, 0, 0f, true));
        w.online.put(B, new MapsLinkWorld.Online("Bob", OW, 5, 64, 5, 0f, true));

        JsonObject a = layers(A, -512, -512, 512, 512);
        assertTrue(a.get("online").getAsBoolean());
        JsonArray aBases = layer(a, BASES).getAsJsonArray("markers");
        assertEquals(1, aBases.size());
        assertEquals("base_a", aBases.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("own", layer(a, BASES).get("scope").getAsString());
        assertEquals(1, layer(a, STORMS).getAsJsonArray("areas").size(), "a hazard near the requester is shown");
        assertTrue(layer(a, STORMS).getAsJsonArray("areas").get(0).getAsJsonObject().get("hazard").getAsBoolean());

        JsonObject b = layers(B, -512, -512, 512, 512);
        JsonArray bBases = layer(b, BASES).getAsJsonArray("markers");
        assertEquals(1, bBases.size());
        assertEquals("base_b", bBases.get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(b.toString().contains("base_a"), "B never sees A's base");
        assertFalse(a.toString().contains("base_b"), "A never sees B's base");
    }

    @Test
    void aStrangerGetsNothing() {
        // online, far from the hazard, owning nothing: asks for a box around everyone's bases and the storm
        w.online.put(STRANGER, new MapsLinkWorld.Online("Sam", OW, 6000, 64, 6000, 0f, true));
        JsonObject s = layers(STRANGER, -512, -512, 512, 512);
        assertEquals(0, items(s, null), "markers and areas: " + s);
        assertFalse(s.toString().contains("base_"));
        assertFalse(s.toString().contains("storm_1"), "bounds never widen proximity: only the server position counts");

        // offline: no position at all, so no proximity layer answers either
        w.online.clear();
        JsonObject off = layers(STRANGER, -512, -512, 512, 512);
        assertFalse(off.get("online").getAsBoolean());
        assertEquals(0, items(off, null));

        // the owner offline keeps their own (owner scope) but loses the proximity hazard
        JsonObject aOff = layers(A, -512, -512, 512, 512);
        assertFalse(aOff.get("online").getAsBoolean());
        assertEquals(1, layer(aOff, BASES).getAsJsonArray("markers").size());
        assertEquals(0, layer(aOff, STORMS).getAsJsonArray("areas").size());
    }

    @Test
    void anUnknownDimensionOrMissingBoundsAnswersNothing() {
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, 0, 64, 0, 0f, true));
        Map<String, String> q = new HashMap<>(Map.of("dim", "nowhere:void", "x0", "0", "z0", "0", "x1", "1", "z1",
                "1"));
        JsonObject o = snap.snapshot(A, "layers", q);
        assertTrue(o.get("needParams").getAsBoolean());
        assertEquals(0, o.getAsJsonArray("layers").size());
        assertTrue(snap.snapshot(A, "layers", Map.of("dim", OW)).get("needParams").getAsBoolean());
        // the nether is known but this provider has nothing there
        JsonObject nether = snap.snapshot(A, "layers", Map.of("dim", "minecraft:the_nether", "x0", "-99", "z0", "-99",
                "x1", "99", "z1", "99"));
        assertEquals(0, items(nether, null));
    }

    @Test
    void theAnswerIsCapped() {
        w.online.put(HOARDER, new MapsLinkWorld.Online("Hoarder", OW, 0, 64, 0, 0f, true));
        // a span wider than Core's MAX_SPAN is clamped, never an error
        JsonObject h = layers(HOARDER, -30_000_000, -30_000_000, 30_000_000, 30_000_000);
        assertNotNull(layer(h, Identifier.fromNamespaceAndPath(BULK_NS, "one")));
        int n = items(h, null);
        assertTrue(n > 0 && n <= LinkLayers.MAX_ITEMS, "items " + n);
        int body = h.toString().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(body < LinkLayers.MAX_LAYER_BYTES + 4096, "bytes " + body);
        int[] sizes = MapsLinkTest.wireSizes(h);
        assertTrue(sizes[0] <= 262_144, "the bridge body stays inside one 256 KiB page: " + sizes[0]);
        for (JsonElement e : h.getAsJsonArray("layers")) {
            int squares = 0;
            for (JsonElement a : e.getAsJsonObject().getAsJsonArray("areas")) {
                if (a.getAsJsonObject().get("id").getAsString().startsWith("land_0#")) {
                    squares++;
                }
            }
            assertTrue(squares <= LinkLayers.MAX_CHUNK_SQUARES);
        }
        // and the hoard never reaches anyone else
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, 0, 64, 0, 0f, true));
        assertEquals(0, items(layers(A, -512, -512, 512, 512), BULK_NS));
    }
}
