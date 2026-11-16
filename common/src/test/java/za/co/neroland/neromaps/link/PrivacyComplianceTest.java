package za.co.neroland.neromaps.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.data.NeroMapsData;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.server.tiles.CapturePerf;
import za.co.neroland.neromaps.server.tiles.ServerTileStore;
import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.PlayerExport;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.tile.Tile;

/**
 * Erasure (Core's conformance harness), export, retention wiring, "no position on disk" over a simulated world
 * folder, and the log sweep (sources and a runtime capture).
 */
class PrivacyComplianceTest {

    static final UUID A = UUID.fromString("5f1c2a9e-3b7d-4e21-9a0c-6d8e4f2b1a37");
    static final UUID B = UUID.fromString("8c3e7b10-2d4f-4a6b-8e9c-1f0a2b3c4d5e");
    static final UUID C = UUID.fromString("0a1b2c3d-4e5f-4a7b-8c9d-0e1f2a3b4c5d");
    static final String OW = FakeWorld.OW;
    /** A session position nobody chose to store: it must never reach a file. */
    static final int PX = 1_234_567;
    static final int PZ = -7_654_321;

    static Waypoint wp(String name, int x, int z) {
        return new Waypoint(0, "default", name, 0xE69F00, "neromaps:glyph/dot", OW, x, 70, z, false, true, true);
    }

    static MapPlayerState seeded() {
        MapPlayerState s = new MapPlayerState();
        s.addWaypoint(A, wp("Alpha Vault", 1111, 2222), 100, "", null);
        s.addWaypoint(B, wp("Bravo Hideout", 3333, 4444), 100, "", null);
        s.markExplored(A, OW, 5, 5, 4096);
        s.markExplored(B, OW, 9000, 9000, 4096);
        ConsentLedger l = new ConsentLedger(s, new java.util.Random(7)::nextLong);
        long[] r = new long[1];
        l.offer(B, A, ShareScope.map(ShareScope.ALL), 1, r);
        l.accept(A, r[0]); // B shares B's map with A (grant on B's record)
        l.offer(A, C, ShareScope.livePosition(), 1, r); // pending on C's record, from A
        return s;
    }

    // --- erasure --------------------------------------------------------------------------------------------------

    @Test
    void coreErasureConformancePasses() {
        MapPlayerState s = seeded();
        NeroMapsData.init();
        PlayerStore.init();
        List<UUID> touched = new ArrayList<>();
        PlayerStore.onErased((who, t) -> touched.addAll(t));
        PlayerStore.detachedForTest(s);
        try {
            assertTrue(NeroMapsData.isRegistered());
            assertTrue(PlayerDataErasure.registeredCount() >= 1);
            ErasureConformance.create()
                    .probe("neromaps:players", s::retains)
                    .verify(null, A);
            assertFalse(s.retains(A));
            assertTrue(touched.contains(B) && touched.contains(C), "others' consent lists were cleaned");
            assertTrue(s.find(B).isPresent(), "B keeps their own record");
            assertTrue(s.find(B).orElseThrow().grants().isEmpty());
        } finally {
            PlayerStore.detachedForTest(null);
        }
    }

    /** Core's retention sweep erases each stale player through the same hook: the registered composite eraser. */
    @Test
    void retentionGoesThroughTheSameRegisteredEraser() {
        MapPlayerState s = seeded();
        NeroMapsData.init();
        PlayerStore.init();
        PlayerStore.detachedForTest(s);
        try {
            PlayerDataErasure.erase(null, B); // what RetentionSchedule's sweep calls per inactive player
            assertFalse(s.retains(B));
            assertTrue(s.find(A).orElseThrow().waypoints().size() == 1);
        } finally {
            PlayerStore.detachedForTest(null);
        }
    }

    // --- export ---------------------------------------------------------------------------------------------------

    @Test
    void exportHoldsEverythingAboutOnePlayerAndNothingAboutAnyoneElse() {
        MapPlayerState s = seeded();
        JsonObject ea = PlayerExport.build(s, A);
        String text = ea.toString();
        assertTrue(text.contains("Alpha Vault"));
        assertTrue(text.contains("1111"));
        assertTrue(text.contains("offersYouMade") && text.contains(C.toString()), "A's pending offer to C");
        assertTrue(text.contains("sharedWithYou"));
        assertTrue(text.contains("EXPLORED_MAP"));
        assertFalse(text.contains("Bravo"), "nothing from B's record but the share B gave A");
        assertFalse(text.contains("3333") || text.contains("4444"));
        String bRegion = Long.toString(za.co.neroland.neromaps.store.ExploredMap.regionKey(9000 >> 5, 9000 >> 5));
        assertFalse(text.contains(bRegion), "B's explored map is not in A's export");
        String round = new String(PlayerExport.inflate(PlayerExport.deflate(text)), StandardCharsets.UTF_8);
        assertEquals(text, round);
        String eb = PlayerExport.build(s, B).toString();
        assertTrue(eb.contains("Bravo Hideout"));
        assertFalse(eb.contains("Alpha"));
    }

    // --- no position on disk --------------------------------------------------------------------------------------

    @Test
    void noFileHoldsAPlayerPositionBeforeOrAfterASession(@TempDir Path world) throws Exception {
        FakeWorld w = new FakeWorld();
        MapPlayerState s = w.state;
        s.addWaypoint(A, wp("Chosen Spot", 1111, 2222), 100, "", null); // the player's own, deliberate content
        scan(world, "before");

        // a session: A stands at (PX, 71, PZ), explores around it, reads the app's position, shares it with B
        w.online.put(A, new MapsLinkWorld.Online("Alice", OW, PX + 0.5, 71, PZ + 0.5, 0f, true));
        w.online.put(B, new MapsLinkWorld.Online("Bob", OW, 0, 64, 0, 0f, true));
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                s.markExplored(A, OW, (PX >> 4) + dx, (PZ >> 4) + dz, 4096);
            }
        }
        long[] r = new long[1];
        new ConsentLedger(s, () -> 99L).offer(A, B, ShareScope.livePosition(), 1, r);
        new ConsentLedger(s, () -> 0L).accept(B, r[0]);
        MapsLinkSnapshots snap = new MapsLinkSnapshots(() -> w, () -> null);
        JsonObject self = snap.snapshot(A, "position", Map.of());
        assertTrue(self.toString().contains(Integer.toString(PX)), "the app does see the live position");
        assertTrue(snap.snapshot(B, "position", Map.of()).toString().contains(Integer.toString(PX)));

        // everything NeroMaps writes on a server: the player store and the terrain tiles of the visited chunks
        save(world, s);
        TileDiskCache disk = new TileDiskCache(world.resolve("data/neromaps/tiles"), 1L << 30);
        ServerTileStore store = new ServerTileStore(disk, world.resolve("data/neromaps/tiles/minecraft/overworld"),
                64L << 20, new CapturePerf(), k -> { });
        store.setListener(w.tiles.listener(OW, store.dir()));
        ChunkColumns cols = new ChunkColumns();
        String[] blocks = new String[256];
        String[] biomes = new String[256];
        for (int i = 0; i < 256; i++) {
            cols.flags[i] = (byte) Tile.F_PRESENT;
            cols.height[i] = 71;
            blocks[i] = "minecraft:grass_block";
            biomes[i] = "minecraft:plains";
        }
        store.apply(PX >> 4, PZ >> 4, cols, blocks, biomes);
        store.flush();
        scan(world, "during");

        // after: erasure, then the store is saved again
        s.erase(A);
        save(world, s);
        scan(world, "after");
        assertNoIdentity(world, A);
    }

    static void save(Path world, MapPlayerState s) throws IOException {
        Tag t = MapPlayerState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        CompoundTag root = new CompoundTag();
        root.put("data", t);
        Path f = world.resolve("data/neromaps_players.dat");
        Files.createDirectories(f.getParent());
        try (var out = Files.newOutputStream(f)) {
            NbtIo.writeCompressed(root, out);
        }
    }

    /** Every file under the world, gunzipped where it is gzip, as bytes. */
    static List<byte[]> files(Path world) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (Stream<Path> st = Files.walk(world)) {
            for (Path p : st.filter(Files::isRegularFile).toList()) {
                byte[] b = Files.readAllBytes(p);
                if (b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) {
                    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
                        b = in.readAllBytes();
                    }
                }
                out.add(b);
                if (p.toString().endsWith(".nmt")) {
                    try {
                        // tiles are deflated: check their decoded columns too
                        Tile t = za.co.neroland.neromaps.tile.TileCodec.decode(b);
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        for (short h : t.height) {
                            bo.write(h >> 8);
                            bo.write(h);
                        }
                        out.add(bo.toByteArray());
                    } catch (Exception e) {
                        throw new AssertionError("unreadable tile", e);
                    }
                }
            }
        }
        return out;
    }

    static void scan(Path world, String when) throws IOException {
        List<byte[]> needles = new ArrayList<>();
        for (int v : new int[] {PX, PZ}) {
            needles.add(ByteBuffer.allocate(4).putInt(v).array());
            needles.add(ByteBuffer.allocate(4).putInt(Integer.reverseBytes(v)).array());
            needles.add(ByteBuffer.allocate(8).putDouble(v + 0.5).array());
            needles.add(ByteBuffer.allocate(8).putDouble(v).array());
            needles.add(ByteBuffer.allocate(4).putFloat(v + 0.5f).array());
            needles.add(Integer.toString(Math.abs(v)).getBytes(StandardCharsets.UTF_8));
        }
        for (byte[] f : files(world)) {
            for (byte[] n : needles) {
                assertFalse(indexOf(f, n) >= 0, "a player position is on disk " + when);
            }
        }
    }

    static void assertNoIdentity(Path world, UUID who) throws IOException {
        List<byte[]> needles = new ArrayList<>();
        needles.add(who.toString().getBytes(StandardCharsets.UTF_8));
        needles.add(ByteBuffer.allocate(16).putLong(who.getMostSignificantBits())
                .putLong(who.getLeastSignificantBits()).array());
        needles.add("Chosen Spot".getBytes(StandardCharsets.UTF_8));
        for (byte[] f : files(world)) {
            for (byte[] n : needles) {
                assertFalse(indexOf(f, n) >= 0, "erased player still on disk");
            }
        }
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // --- logs -----------------------------------------------------------------------------------------------------

    static Path repoJava() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            Path j = p.resolve("common/src/main/java/za/co/neroland/neromaps");
            if (Files.isDirectory(j)) {
                return j;
            }
        }
        throw new IllegalStateException("repository root not found");
    }

    /** No INFO/WARN/ERROR log call in the sources passes an identity, a coordinate, a name or a throwable. */
    @Test
    void sourceLogSweepFindsNoPersonalData() throws IOException {
        Pattern call = Pattern.compile("LOGGER\\.(info|warn|error)\\s*\\((.*?)\\);", Pattern.DOTALL);
        Pattern deny = Pattern.compile("getUUID|getGameProfile|getScoreboardName|getDisplayName|\\buuid\\b|playerId"
                + "|\\bplayer\\b|getX\\(|getY\\(|getZ\\(|getBlockX|getBlockZ|blockPosition|position\\(|waypoint"
                + "|nameOf|handle\\(|,\\s*(e|ex|t|err)\\s*$", Pattern.CASE_INSENSITIVE);
        List<String> bad = new ArrayList<>();
        int calls = 0;
        try (Stream<Path> st = Files.walk(repoJava())) {
            for (Path p : st.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = call.matcher(src);
                while (m.find()) {
                    calls++;
                    String args = m.group(2).replace("store.name()", "STORE");
                    int firstComma = args.indexOf("\",");
                    String tail = firstComma < 0 ? "" : args.substring(firstComma + 2).trim();
                    if (deny.matcher(tail).find()) {
                        bad.add(p.getFileName() + ": " + m.group(0).replaceAll("\\s+", " "));
                    }
                }
            }
        }
        assertTrue(calls > 5, "the sweep found the log calls");
        assertTrue(bad.isEmpty(), "log calls with personal data: " + bad);
    }

    /** Runs the paths that touch player data with known identities and checks every INFO+ line logged. */
    @Test
    void runtimeLogCaptureHoldsNoIdentityCoordinateOrName() {
        List<String> lines = new ArrayList<>();
        AbstractAppender app = new AbstractAppender("neromaps-sweep", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.INFO)) {
                    String t = event.getMessage().getFormattedMessage();
                    if (event.getThrown() != null) {
                        t += " " + event.getThrown();
                    }
                    lines.add(t);
                }
            }
        };
        app.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(app, Level.ALL, null);
        ctx.updateLoggers();
        try {
            FakeWorld w = new FakeWorld();
            w.online.put(A, new MapsLinkWorld.Online("Alice", OW, PX, 70, PZ, 0f, true));
            w.online.put(B, new MapsLinkWorld.Online("Bob", OW, 3, 70, 3, 0f, true));
            MapsLinkSnapshots snap = new MapsLinkSnapshots(() -> w, () -> null);
            MapsLinkActions act = new MapsLinkActions(() -> w);
            JsonObject add = new JsonObject();
            add.addProperty("name", "Zebra Cave");
            add.addProperty("dim", OW);
            add.addProperty("x", PX);
            add.addProperty("y", 70);
            add.addProperty("z", PZ);
            act.execute(A, "waypoint_add", add);
            JsonObject offer = new JsonObject();
            offer.addProperty("player", "Bob");
            offer.addProperty("scope", "LIVE_POSITION");
            act.execute(A, "share_offer", offer);
            act.execute(B, "share_accept", MapsLinkTest.obj("request", "rbogus"));
            for (String sec : MapsLinkModule.SECTIONS) {
                try {
                    snap.snapshot(A, sec, Map.of());
                    snap.snapshot(B, sec, Map.of("dim", OW, "lod", "0", "bx", "0", "bz", "0", "since", "0"));
                } catch (RuntimeException e) {
                    // warming / no palette: not a log matter
                }
            }
            NeroMapsData.init();
            PlayerStore.init();
            PlayerStore.detachedForTest(w.state);
            PlayerDataErasure.erase(null, A);
            PlayerStore.detachedForTest(null);
            PlayerExport.build(w.state, B);
        } finally {
            ctx.getConfiguration().getRootLogger().removeAppender("neromaps-sweep");
            ctx.updateLoggers();
            app.stop();
        }
        for (String l : lines) {
            for (String needle : List.of(A.toString(), B.toString(), "Alice", "Bob", "Zebra Cave",
                    Integer.toString(PX), Integer.toString(Math.abs(PZ)))) {
                assertFalse(l.contains(needle), "log line carries personal data: " + l);
            }
        }
    }

    /** Telemetry: no PII flag, no user, and the scrubber runs on every outgoing string. */
    @Test
    void telemetryCarriesNoPersonalData() throws IOException {
        String src = Files.readString(repoJava().resolve("telemetry/NeroMapsTelemetry.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("setSendDefaultPii(false)"));
        assertTrue(src.contains("setUser(null)"), "the user is cleared on every event");
        assertFalse(src.replace("setUser(null)", "").contains("setUser("));
        assertFalse(src.contains("getUUID") || src.contains("getGameProfile"));
        for (String f : List.of("link", "store", "server")) {
            try (Stream<Path> st = Files.walk(repoJava().resolve(f))) {
                for (Path p : st.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String s = Files.readString(p, StandardCharsets.UTF_8);
                    assertFalse(s.contains("NeroMapsTelemetry.breadcrumb(") && s.contains("getUUID"),
                            "breadcrumbs next to identities in " + p.getFileName());
                }
            }
        }
    }
}
