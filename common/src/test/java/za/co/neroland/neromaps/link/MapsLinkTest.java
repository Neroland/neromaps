package za.co.neroland.neromaps.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neromaps.server.tiles.LinkTiles;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.tile.Fixtures;
import za.co.neroland.neromaps.tile.PerfLog;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;

/** The {@code neromaps} link module against its contract: scoping, fog, sizes, cursor, actions and events. */
class MapsLinkTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    static final String OW = FakeWorld.OW;

    FakeWorld w;
    MapsLinkSnapshots snap;
    MapsLinkActions act;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        MapsPalette pal = MapsPalette.build(Map.of("minecraft:stone", 0x7d7d7d), List.of());
        snap = new MapsLinkSnapshots(() -> w, () -> pal);
        act = new MapsLinkActions(() -> w);
    }

    static void exploreTile(MapPlayerState s, UUID p, int tx, int tz, int chunkColumns) {
        for (int cz = 0; cz < 16; cz++) {
            for (int cx = 0; cx < chunkColumns; cx++) {
                s.markExplored(p, OW, tx * 16 + cx, tz * 16 + cz, 4096);
            }
        }
    }

    Map<String, String> q(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonObject index(UUID who, String since) {
        JsonObject o = snap.snapshot(who, "tiles", q("dim", OW, "lod", "0", "bx", "0", "bz", "0", "since", since));
        if (o.has("retryMs")) { // the warm-up shape: let the worker learn the disk etags, then ask again
            w.runWorker();
            o = snap.snapshot(who, "tiles", q("dim", OW, "lod", "0", "bx", "0", "bz", "0", "since", since));
        }
        return o;
    }

    JsonObject page(UUID who, long want) {
        return snap.snapshot(who, "tiles", q("dim", OW, "lod", "0", "bx", "0", "bz", "0", "want", LinkTiles.hex(want)));
    }

    static boolean isNull(JsonArray a, int i) {
        return a.get(i).isJsonNull();
    }

    /** What the bridge sends: its envelope; and what the relay carries: that envelope as a JSON string in a frame. */
    static int[] wireSizes(JsonObject data) {
        JsonObject env = new JsonObject();
        env.addProperty("ok", true);
        env.add("data", data);
        String body = env.toString();
        JsonObject frame = new JsonObject();
        frame.addProperty("t", "res");
        frame.addProperty("id", "0123456789abcdef0123456789abcdef");
        frame.addProperty("status", 200);
        frame.addProperty("body", body);
        return new int[] {body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                new Gson().toJson(frame).getBytes(java.nio.charset.StandardCharsets.UTF_8).length};
    }

    /** Writes a response body (as the bridge sends it) for the relay-simulator framing check. */
    static void dump(String name, JsonObject data) {
        JsonObject env = new JsonObject();
        env.addProperty("ok", true);
        env.add("data", data);
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("build", "neromaps-perf", "link");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(name), env.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            // the in-test size assertions still hold
        }
    }

    // --- fog -----------------------------------------------------------------------------------------------------

    @Test
    void aTileOutsideTheFogIsIndistinguishableFromAMissingOne() {
        w.put(0, 0, 0, Fixtures.plains());
        w.put(0, 1, 0, Fixtures.mountains()); // exists, never explored by A: hidden
        exploreTile(w.state, A, 0, 0, 16);
        exploreTile(w.state, A, 2, 0, 16); // explored, never captured: missing
        JsonObject idx = index(A, "0");
        JsonArray et = idx.getAsJsonArray("etags");
        assertFalse(isNull(et, 0));
        assertTrue(isNull(et, 1), "hidden");
        assertTrue(isNull(et, 2), "missing");
        assertEquals(et.get(1), et.get(2));
        int before = w.submitted;
        JsonObject hidden = page(A, 1L << 1);
        JsonObject missing = page(A, 1L << 2);
        assertEquals(hidden.toString(), missing.toString(), "same answer for hidden and missing");
        assertEquals(0, hidden.getAsJsonObject("tiles").size());
        assertEquals(LinkTiles.hex(0), hidden.get("pending").getAsString());
        assertEquals(LinkTiles.hex(0), hidden.get("next").getAsString());
        assertEquals(before, w.submitted, "neither queues worker work");
        // B explored nothing: every entry is null, including A's tile
        JsonArray eb = index(B, "0").getAsJsonArray("etags");
        for (int i = 0; i < 64; i++) {
            assertTrue(isNull(eb, i));
        }
        assertEquals(0, page(B, -1L).getAsJsonObject("tiles").size());
        assertEquals(before, w.submitted);
    }

    @Test
    void aPartlyVisibleTileIsMaskedAndItsEtagDiffers() throws Exception {
        Tile t = Fixtures.plains();
        w.put(0, 0, 0, t);
        exploreTile(w.state, A, 0, 0, 8); // the west half of the tile's chunks
        JsonObject idx = index(A, "0");
        long content = TileCodec.contentHash(TileCodec.encode(t));
        String etag = idx.getAsJsonArray("etags").get(0).getAsString();
        assertNotEquals(LinkTiles.hex(content), etag);
        assertTrue((LinkTiles.parseMask(page(A, 1L).get("pending").getAsString()).getAsLong() & 1L) != 0);
        w.runWorker();
        JsonObject p = page(A, 1L);
        JsonObject e = p.getAsJsonObject("tiles").getAsJsonObject("0");
        assertEquals(etag, e.get("e").getAsString());
        Tile got = TileCodec.decode(Base64.getDecoder().decode(e.get("b").getAsString()));
        for (int z = 0; z < Tile.SIZE; z += 7) {
            for (int x = 128; x < Tile.SIZE; x += 5) {
                assertEquals(0, got.flags[Tile.index(x, z)] & Tile.F_PRESENT, "east half hidden");
            }
        }
        assertNotEquals(0, got.flags[Tile.index(3, 3)] & Tile.F_PRESENT, "west half kept");
    }

    // --- consent --------------------------------------------------------------------------------------------------

    @Test
    void aSecondPlayerReadsNothingOfTheFirstsWithoutConsent() {
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, 500.5, 64, 500.5, 90f, true));
        w.online.put(B, new MapsLinkWorld.Online("Bob", OW, 10, 64, 10, 0f, true));
        w.online.put(C, new MapsLinkWorld.Online("Cara", OW, 20, 64, 20, 0f, true));
        w.put(0, 0, 0, Fixtures.plains());
        exploreTile(w.state, A, 0, 0, 16);
        w.state.addWaypoint(A, new Waypoint(0, "default", "Secret Base", 0xE69F00, "neromaps:glyph/home", OW, 1234,
                70, -4321, false, true, true), 100, "", null);
        String bWaypoints = snap.snapshot(B, "waypoints", Map.of()).toString();
        assertFalse(bWaypoints.contains("Secret") || bWaypoints.contains("1234"));
        JsonObject pos = snap.snapshot(B, "position", Map.of());
        assertEquals(0, pos.getAsJsonArray("shared").size());
        assertFalse(pos.toString().contains("500"));
        assertTrue(isNull(index(B, "0").getAsJsonArray("etags"), 0));
        assertEquals(0, snap.snapshot(B, "dimensions", Map.of()).getAsJsonArray("dimensions").size());
        assertEquals(0, snap.snapshot(B, "shares", Map.of()).getAsJsonArray("incoming").size());

        // A offers map + live position to Bob through the app; Bob accepts both through the app
        JsonObject offer = new JsonObject();
        offer.addProperty("player", "Bob");
        offer.addProperty("scope", "EXPLORED_MAP");
        assertTrue(act.execute(A, "share_offer", offer).ok());
        offer.addProperty("scope", "LIVE_POSITION");
        assertTrue(act.execute(A, "share_offer", offer).ok());
        JsonArray requests = snap.snapshot(B, "shares", Map.of()).getAsJsonArray("requests");
        assertEquals(2, requests.size());
        assertEquals("Alice", requests.get(0).getAsJsonObject().get("player").getAsString());
        // Cara cannot answer Bob's requests (forged acceptance)
        for (JsonElement r : requests) {
            JsonObject body = new JsonObject();
            body.addProperty("request", r.getAsJsonObject().get("requestId").getAsString());
            LinkActionResult forged = act.execute(C, "share_accept", body);
            assertFalse(forged.ok());
            assertEquals("unknown_request", forged.message());
        }
        assertFalse(act.execute(B, "share_accept", obj("request", "r12345")).ok(), "invented id");
        for (JsonElement r : requests) {
            JsonObject body = new JsonObject();
            body.addProperty("request", r.getAsJsonObject().get("requestId").getAsString());
            assertTrue(act.execute(B, "share_accept", body).ok());
        }
        assertFalse(isNull(index(B, "0").getAsJsonArray("etags"), 0), "shared map now visible");
        assertEquals(1, snap.snapshot(B, "dimensions", Map.of()).getAsJsonArray("dimensions").size());
        JsonArray shared = snap.snapshot(B, "position", Map.of()).getAsJsonArray("shared");
        assertEquals(1, shared.size());
        assertEquals(500.0, shared.get(0).getAsJsonObject().get("x").getAsDouble());
        // Cara still sees nothing
        assertEquals(0, snap.snapshot(C, "position", Map.of()).getAsJsonArray("shared").size());
        assertTrue(isNull(index(C, "0").getAsJsonArray("etags"), 0));
        // sneaking hides the sharer; another dimension shows the dimension only
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, 500.5, 64, 500.5, 90f, false));
        assertEquals(0, snap.snapshot(B, "position", Map.of()).getAsJsonArray("shared").size());
        w.online.put(A, new MapsLinkWorld.Online("Alice", "minecraft:the_nether", 62.5, 64, 62.5, 90f, true));
        JsonObject other = snap.snapshot(B, "position", Map.of()).getAsJsonArray("shared").get(0).getAsJsonObject();
        assertFalse(other.has("x"));
        // offline sharer: nothing
        w.online.remove(A);
        assertEquals(0, snap.snapshot(B, "position", Map.of()).getAsJsonArray("shared").size());
        // Bob leaves the map share (incoming revoke): the tile is hidden on the next read
        JsonArray incoming = snap.snapshot(B, "shares", Map.of()).getAsJsonArray("incoming");
        String mapGrant = null;
        for (JsonElement g : incoming) {
            if ("EXPLORED_MAP".equals(g.getAsJsonObject().get("scope").getAsString())) {
                mapGrant = g.getAsJsonObject().get("id").getAsString();
            }
        }
        assertNotNull(mapGrant);
        assertFalse(act.execute(C, "share_revoke", obj("grantId", mapGrant, "direction", "incoming")).ok(),
                "a third party cannot resolve the grant");
        assertTrue(act.execute(B, "share_revoke", obj("grantId", mapGrant, "direction", "incoming")).ok());
        assertTrue(isNull(index(B, "0").getAsJsonArray("etags"), 0), "revoked: hidden again");
        assertEquals(0, page(B, 1L).getAsJsonObject("tiles").size());
    }

    static JsonObject obj(String... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            o.addProperty(kv[i], kv[i + 1]);
        }
        return o;
    }

    // --- size and cursor ------------------------------------------------------------------------------------------

    @Test
    void noResponseExceeds256KiBAndTheCursorResumes() throws Exception {
        Tile[] kinds = {Fixtures.plains(), Fixtures.ocean(), Fixtures.mountains()};
        for (int i = 0; i < 64; i++) {
            Tile t = i % 9 == 4 ? Fixtures.random(i, 64, 8, 0.97) : kinds[i % 3];
            w.put(0, i & 7, i >> 3, t);
            exploreTile(w.state, A, i & 7, i >> 3, 16);
        }
        JsonObject idx = index(A, "0");
        assertEquals("ffffffffffffffff", idx.get("changed").getAsString());
        String cursor = idx.get("cursor").getAsString();
        long want = -1L;
        int got = 0;
        int pages = 0;
        int maxBody = 0;
        int maxFrame = 0;
        int rounds = 0;
        while (want != 0 && rounds++ < 100) {
            JsonObject p = page(A, want);
            int[] sz = wireSizes(p);
            if (sz[0] > maxBody) {
                dump("largest-page.json", p);
            }
            maxBody = Math.max(maxBody, sz[0]);
            maxFrame = Math.max(maxFrame, sz[1]);
            assertTrue(sz[0] <= LinkTiles.PAGE_BYTES, "page body " + sz[0]);
            pages++;
            long received = 0;
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("tiles").entrySet()) {
                int i = Integer.parseInt(e.getKey());
                Tile t = TileCodec.decode(Base64.getDecoder().decode(e.getValue().getAsJsonObject().get("b").getAsString()));
                assertNotNull(t);
                received |= 1L << i;
                got++;
            }
            long next = LinkTiles.parseMask(p.get("next").getAsString()).getAsLong();
            long pending = LinkTiles.parseMask(p.get("pending").getAsString()).getAsLong();
            want = (want & ~received) & (next | pending);
            w.runWorker();
        }
        assertEquals(64 - (int) w.tiles.oversize.get(), got, "every tile arrives (oversize ones are never offered)");
        assertTrue(pages >= 2, "64 tiles take more than one page");
        assertTrue(maxFrame < 300 * 1024, "relay frame " + maxFrame);
        PerfLog.line(String.format("M7 64-tile block: %d pages, max body %d B, max relay frame %d B, "
                + "%.1f us/call server thread, %d oversize", pages, maxBody, maxFrame,
                w.tiles.serverNanos.get() / 1000.0 / Math.max(1, w.tiles.indexCalls.get() + w.tiles.pageCalls.get()),
                w.tiles.oversize.get()));

        // nothing changed: the cursor resumes with an empty change mask
        JsonObject again = index(A, cursor);
        assertEquals(LinkTiles.hex(0), again.get("changed").getAsString());
        assertEquals(cursor, again.get("cursor").getAsString());
        // one tile changes: only it is flagged, and the next cursor is clean again
        w.put(0, 5, 0, Fixtures.ocean());
        JsonObject one = index(A, cursor);
        assertEquals(LinkTiles.hex(1L << 5), one.get("changed").getAsString());
        assertEquals(LinkTiles.hex(0), index(A, one.get("cursor").getAsString()).get("changed").getAsString());
        // a stale epoch (server restart) or a fog change marks every visible tile
        String stale = "zz" + cursor.substring(cursor.indexOf('.'));
        assertEquals("ffffffffffffffff", index(A, stale).get("changed").getAsString());
        w.state.markExplored(A, OW, 200, 200, 4096); // outside the block: fog part unchanged
        assertEquals(LinkTiles.hex(0), index(A, one.get("cursor").getAsString()).get("changed").getAsString());
    }

    @Test
    void perPlayerByteBudgetAnswersPendingWithRetry() {
        for (int i = 0; i < 64; i++) {
            w.put(0, i & 7, i >> 3, i % 2 == 0 ? Fixtures.plains() : Fixtures.mountains());
            exploreTile(w.state, A, i & 7, i >> 3, 16);
        }
        index(A, "0");
        page(A, -1L);
        w.runWorker();
        for (int r = 0; r < 400 && w.tiles.bytesServed.get() < LinkTiles.VIEWER_BYTES_PER_MINUTE; r++) {
            assertTrue(page(A, -1L).getAsJsonObject("tiles").size() > 0);
        }
        assertTrue(w.tiles.bytesServed.get() >= LinkTiles.VIEWER_BYTES_PER_MINUTE);
        JsonObject capped = page(A, -1L);
        assertEquals(0, capped.getAsJsonObject("tiles").size());
        assertEquals("ffffffffffffffff", capped.get("pending").getAsString());
        assertTrue(capped.get("retryMs").getAsInt() > 0);
        // another player's budget is their own
        exploreTile(w.state, B, 0, 0, 16);
        assertEquals(1, page(B, 1L).getAsJsonObject("tiles").size());
        // a minute later A is served again
        w.now += 60_000L;
        assertTrue(page(A, -1L).getAsJsonObject("tiles").size() > 0);
    }

    // --- actions -------------------------------------------------------------------------------------------------

    @Test
    void actionsAreValidatedScopedAndRateLimited() {
        JsonObject add = new JsonObject();
        add.addProperty("name", "Home");
        add.addProperty("dim", OW);
        add.addProperty("x", 10);
        add.addProperty("y", 64);
        add.addProperty("z", -10);
        add.addProperty("colour", 2);
        add.addProperty("glyph", "home");
        add.addProperty("beam", true);
        LinkActionResult ok = act.execute(A, "waypoint_add", add);
        assertTrue(ok.ok());
        JsonObject wp = ok.state().getAsJsonObject("waypoint");
        assertEquals("w1", wp.get("id").getAsString());
        assertEquals(2, wp.get("colour").getAsInt());
        assertEquals("home", wp.get("glyph").getAsString());

        JsonObject bad = add.deepCopy();
        bad.addProperty("dim", "minecraft:nowhere");
        assertEquals("bad_dimension", act.execute(A, "waypoint_add", bad).message());
        bad = add.deepCopy();
        bad.addProperty("name", "rude word");
        assertEquals("bad_name", act.execute(A, "waypoint_add", bad).message());
        bad = add.deepCopy();
        bad.addProperty("colour", 9);
        assertEquals("bad_colour", act.execute(A, "waypoint_add", bad).message());
        // B cannot edit or delete A's waypoint
        JsonObject edit = add.deepCopy();
        edit.addProperty("id", "w1");
        assertEquals("not_found", act.execute(B, "waypoint_edit", edit).message());
        assertEquals("not_found", act.execute(B, "waypoint_delete", obj("id", "w1")).message());
        assertTrue(w.state.find(A).orElseThrow().waypoint(1).isPresent());
        // coordinates are clamped like in game
        JsonObject far = add.deepCopy();
        far.addProperty("x", 999_999_999);
        LinkActionResult clamped = act.execute(A, "waypoint_add", far);
        assertEquals(30_000_000, clamped.state().getAsJsonObject("waypoint").get("x").getAsInt());
        // the 21st action within a minute is refused
        int refused = 0;
        for (int i = 0; i < 25; i++) {
            LinkActionResult r = act.execute(C, "waypoint_delete", obj("id", "w999"));
            if ("rate".equals(r.message())) {
                refused++;
            }
        }
        assertEquals(25 - MapsLinkActions.PER_MINUTE, refused);
        // death points are deletable by index
        w.state.addDeath(A, new za.co.neroland.neromaps.store.DeathPoint(OW, 1, 2, 3), true);
        assertTrue(snap.snapshot(A, "waypoints", Map.of()).toString().contains("\"kind\":\"death\""));
        assertTrue(act.execute(A, "waypoint_delete", obj("id", "d0")).ok());
    }

    // --- shapes --------------------------------------------------------------------------------------------------

    @Test
    void everySectionAnswersASmallDocumentWithEmptyParams() {
        for (String s : MapsLinkModule.SECTIONS) {
            JsonObject o = snap.snapshot(A, s, Map.of());
            assertNotNull(o, s);
            assertTrue(o.toString().length() < (s.equals("palette") ? 200_000 : 2048), s);
        }
        assertTrue(snap.snapshot(A, "tiles", Map.of()).get("needParams").getAsBoolean());
        String etag = snap.snapshot(A, "palette", Map.of()).get("v").getAsString();
        assertTrue(snap.snapshot(A, "palette", Map.of("v", etag)).get("same").getAsBoolean());
        // bad params never throw
        assertTrue(snap.snapshot(A, "tiles", q("dim", OW, "lod", "9", "bx", "0", "bz", "0", "since", "0"))
                .has("needParams"));
        assertTrue(snap.snapshot(A, "tiles", q("dim", OW, "lod", "0", "bx", "0", "bz", "0", "want", "xyz"))
                .has("needParams"));
        JsonObject d = snap.snapshot(A, "dimensions", Map.of());
        assertTrue(d.has("recordEpoch") && d.has("policy"));
    }

    @Test
    void erasureChangesTheRecordEpoch() {
        exploreTile(w.state, A, 0, 0, 1);
        String before = snap.snapshot(A, "dimensions", Map.of()).get("recordEpoch").getAsString();
        assertNotEquals("0", before);
        w.state.erase(A);
        assertEquals("0", snap.snapshot(A, "dimensions", Map.of()).get("recordEpoch").getAsString());
        exploreTile(w.state, A, 0, 0, 1);
        assertNotEquals(before, snap.snapshot(A, "dimensions", Map.of()).get("recordEpoch").getAsString());
    }

    // --- events --------------------------------------------------------------------------------------------------

    @Test
    void eventsCarryNoNamesOrCoordinates() {
        List<LinkEvent> out = new ArrayList<>();
        MapsLinkEvents.reset();
        MapsLinkEvents.publishTo(out::add);
        try {
            MapsLinkEvents.shareRequest(B);
            MapsLinkEvents.death(A);
            MapsLinkEvents.waypoints(A);
            MapsLinkEvents.second(w.state, w.tiles, false, w.now);
            assertEquals(1, out.stream().filter(e -> e.playerId().equals(A)).count(), "one deaths event, coalesced");
            assertEquals(2, out.size());
            for (LinkEvent e : out) {
                assertEquals(MapsLinkModule.MODULE_ID, e.moduleId());
                String text = e.payload().toString();
                assertFalse(text.matches(".*\\d{2,}.*"), "no numbers (coordinates) in " + text);
                assertFalse(text.contains(A.toString()) || text.contains(B.toString()));
            }
            LinkEvent share = out.stream().filter(e -> e.topic().equals("shares")).findFirst().orElseThrow();
            assertEquals(B, share.playerId());
            assertTrue(share.payload().getAsJsonObject("_notify").get("deepLink").getAsString()
                    .startsWith("nerolink://server/{server}/neromaps"));
            // coarse tile events go only to recent readers, only for blocks they can see
            out.clear();
            w.put(0, 0, 0, Fixtures.plains());
            exploreTile(w.state, A, 0, 0, 16);
            w.tiles.recentViewers(w.now); // prune
            index(A, "0");
            page(A, 1L);
            w.put(0, 1, 0, Fixtures.plains());
            w.put(0, 40, 40, Fixtures.plains()); // a block nobody saw
            for (int i = 0; i < MapsLinkEvents.TILES_INTERVAL_SECONDS; i++) {
                MapsLinkEvents.second(w.state, w.tiles, false, w.now);
            }
            List<LinkEvent> tiles = out.stream().filter(e -> e.topic().equals("tiles")).toList();
            assertEquals(1, tiles.size());
            assertEquals(A, tiles.get(0).playerId());
            assertEquals("[[0,0]]", tiles.get(0).payload().getAsJsonArray("blocks").toString());
        } finally {
            MapsLinkEvents.publishTo(e -> { });
            MapsLinkEvents.reset();
        }
    }

    @Test
    void indexAnswersThePendingShapeWhileTheDiskEtagsAreLearned() {
        w.put(0, 0, 0, Fixtures.plains());
        exploreTile(w.state, A, 0, 0, 16);
        java.util.ArrayDeque<java.util.function.Consumer<LinkTiles.Source>> later = new java.util.ArrayDeque<>();
        LinkTiles fresh = new LinkTiles((dim, job) -> later.add(job));
        za.co.neroland.neromaps.fog.Visibility v = za.co.neroland.neromaps.fog.Visibility.of(w.state, A, OW, false);

        // warming: no exception (no bridge 500), the pending shape with a retry hint
        JsonObject warm = fresh.index(OW, 0, 0, 0, "0", v);
        assertEquals("", warm.get("cursor").getAsString(), "the app keeps the cursor it holds");
        assertEquals(LinkTiles.RETRY_MS, warm.get("retryMs").getAsInt());
        JsonArray etags = warm.getAsJsonArray("etags");
        assertEquals(LinkTiles.TILES, etags.size());
        // the visible tile is a placeholder, never null: a null would make the app delete the tile it holds
        assertEquals(LinkTiles.WARMING_ETAG, etags.get(0).getAsString());
        assertNotEquals(16, LinkTiles.WARMING_ETAG.length());
        assertEquals(1L, LinkTiles.parseMask(warm.get("pending").getAsString()).getAsLong());
        // hidden tiles stay null, exactly as after warm-up
        for (int i = 1; i < LinkTiles.TILES; i++) {
            assertTrue(isNull(etags, i), "tile " + i);
        }
        assertFalse(fresh.learned(OW));
        assertEquals(1, later.size(), "learning is queued once, on the worker");
        fresh.index(OW, 0, 0, 0, "0", v);
        assertEquals(1, later.size(), "asking again while warming queues nothing more");

        // the worker learns; the next answer is the ordinary index
        later.poll().accept(w.source);
        assertTrue(fresh.learned(OW));
        JsonObject ready = fresh.index(OW, 0, 0, 0, "0", v);
        assertFalse(ready.has("retryMs"));
        assertFalse(ready.get("cursor").getAsString().isEmpty());
        String e = ready.getAsJsonArray("etags").get(0).getAsString();
        assertEquals(16, e.length());
        assertNotEquals(LinkTiles.WARMING_ETAG, e);
    }

    @Test
    void paletteAnswersPendingWhileItIsBuilt() {
        MapsLinkSnapshots building = new MapsLinkSnapshots(() -> w, () -> null);
        JsonObject o = building.snapshot(A, "palette", Map.of());
        assertTrue(o.get("pending").getAsBoolean());
        assertEquals(LinkTiles.RETRY_MS, o.get("retryMs").getAsInt());
        assertFalse(o.has("blocks"), "the app keeps the table it holds");
        assertFalse(o.has("same"));
        assertTrue(building.snapshot(A, "palette", Map.of("v", "abc")).get("pending").getAsBoolean());
    }
}
