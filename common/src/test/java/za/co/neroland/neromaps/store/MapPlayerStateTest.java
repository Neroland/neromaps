package za.co.neroland.neromaps.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.tile.PerfLog;

class MapPlayerStateTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    static Waypoint wp(String name, int x) {
        return new Waypoint(0, Waypoint.DEFAULT_SET, name, 0xE69F00, Waypoint.DEFAULT_ICON, "minecraft:overworld", x,
                64, -x, false, true, true);
    }

    static MapPlayerState roundTrip(MapPlayerState s) {
        Tag t = MapPlayerState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        return MapPlayerState.CODEC.parse(NbtOps.INSTANCE, t).getOrThrow();
    }

    static int bytes(MapPlayerState s) throws Exception {
        Tag t = MapPlayerState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        NbtIo.writeCompressed((CompoundTag) t, bo);
        return bo.size();
    }

    @Test
    void roundTripKeepsEverythingAndTheSchemaVersion() {
        MapPlayerState s = new MapPlayerState();
        int[] id = new int[1];
        assertEquals(MapPlayerState.Result.OK, s.addWaypoint(A, wp("Home", 10), 256, "", id));
        assertEquals(1, id[0]);
        s.addDeath(A, new DeathPoint("minecraft:the_nether", 1, 2, 3), true);
        s.markExplored(A, "minecraft:overworld", 5, -7, 4096);
        s.markExplored(A, "minecraft:overworld", -40, 90, 4096);
        long[] req = new long[1];
        new ConsentLedger(s, () -> 42L).offer(A, B, ShareScope.map(ShareScope.ALL), 100, req);
        new ConsentLedger(s, () -> 43L).accept(B, req[0]);
        s.setAcceptRequests(C, false);
        MapPlayerState r = roundTrip(s);
        assertEquals(MapPlayerState.SAVE_VERSION, r.loadedVersion());
        PlayerRecord a = r.find(A).orElseThrow();
        assertEquals("Home", a.waypoints().get(0).name());
        assertEquals(1, a.deaths().size());
        assertTrue(a.explored().isExplored("minecraft:overworld", 5, -7));
        assertTrue(a.explored().isExplored("minecraft:overworld", -40, 90));
        assertFalse(a.explored().isExplored("minecraft:overworld", 6, -7));
        assertEquals(1, a.grants().size());
        assertEquals(B, a.grants().get(0).to());
        assertFalse(r.find(C).orElseThrow().acceptRequests());
        // the next id continues after the highest one loaded
        int[] next = new int[1];
        r.addWaypoint(A, wp("Two", 1), 256, "", next);
        assertEquals(2, next[0]);
    }

    @Test
    void waypointLimitsNamesAndDeaths() {
        MapPlayerState s = new MapPlayerState();
        for (int i = 0; i < 3; i++) {
            assertEquals(MapPlayerState.Result.OK, s.addWaypoint(A, wp("W" + i, i), 3, "", null));
        }
        assertEquals(MapPlayerState.Result.LIMIT, s.addWaypoint(A, wp("W4", 4), 3, "", null));
        assertEquals(MapPlayerState.Result.NAME_EMPTY, s.addWaypoint(B, wp("\u00A7", 4), 3, "", null));
        assertEquals(MapPlayerState.Result.NAME_DENIED, s.addWaypoint(B, wp("rude", 4), 3, "rude", null));
        Waypoint first = s.find(A).orElseThrow().waypoints().get(0);
        Waypoint renamed = new Waypoint(first.id(), "trip", "Renamed", 1, "x", first.dimension(), 0, 0, 0, false,
                false, false);
        assertEquals(MapPlayerState.Result.OK, s.updateWaypoint(A, renamed, ""));
        assertEquals("Renamed", s.find(A).orElseThrow().waypoint(first.id()).orElseThrow().name());
        assertEquals(MapPlayerState.Result.NOT_FOUND, s.updateWaypoint(B, renamed, ""));
        assertEquals(MapPlayerState.Result.OK, s.removeWaypoint(A, first.id()));
        assertEquals(MapPlayerState.Result.NOT_FOUND, s.removeWaypoint(A, first.id()));
        for (int i = 0; i < 5; i++) {
            s.addDeath(A, new DeathPoint("minecraft:overworld", i, 0, 0), true);
        }
        List<DeathPoint> d = s.find(A).orElseThrow().deaths();
        assertEquals(DeathPoint.KEEP, d.size());
        assertEquals(4, d.get(0).x()); // newest first
        assertFalse(s.addDeath(A, new DeathPoint("minecraft:overworld", 9, 0, 0), false)); // policy off
        s.setDeathWaypoints(A, false);
        assertTrue(s.find(A).orElseThrow().deaths().isEmpty());
        assertFalse(s.addDeath(A, new DeathPoint("minecraft:overworld", 9, 0, 0), true)); // player off
    }

    @Test
    void erasureRemovesTheRecordAndEveryReferenceInOneCall() {
        MapPlayerState s = new MapPlayerState();
        s.addWaypoint(A, wp("Home", 1), 256, "", null);
        s.addWaypoint(B, wp("Base", 2), 256, "", null);
        ConsentLedger l = new ConsentLedger(s, new java.util.Random(1)::nextLong);
        long[] r1 = new long[1];
        l.offer(B, A, ShareScope.livePosition(), 1, r1);
        l.accept(A, r1[0]); // grant B -> A on B's record
        long[] r2 = new long[1];
        l.offer(A, C, ShareScope.map("*"), 1, r2); // pending on C's record, from A
        assertTrue(s.retains(A));
        List<UUID> touched = s.erase(A);
        assertFalse(s.retains(A));
        assertTrue(touched.contains(B));
        assertTrue(touched.contains(C));
        assertTrue(s.find(B).orElseThrow().grants().isEmpty());
        assertTrue(s.find(C).isEmpty() || s.find(C).orElseThrow().incoming().isEmpty());
        assertTrue(s.find(B).isPresent());
        s.erase(UUID.randomUUID()); // unknown player tolerated
    }

    @Test
    void exportHoldsOnlyTheCallersRecord() {
        MapPlayerState s = new MapPlayerState();
        s.addWaypoint(A, wp("Secret A", 1), 256, "", null);
        s.addWaypoint(B, wp("Secret B", 2), 256, "", null);
        JsonObject ea = s.export(A);
        String text = ea.toString();
        assertTrue(text.contains("Secret A"));
        assertFalse(text.contains("Secret B"));
        assertFalse(text.contains(B.toString()));
        assertFalse(s.export(C).toString().contains("Secret"));
    }

    @Test
    void storeSizes() throws Exception {
        MapPlayerState typical = new MapPlayerState();
        for (int i = 0; i < 40; i++) {
            typical.addWaypoint(A, wp("Waypoint number " + i, i * 100), 256, "", null);
        }
        for (int cx = -40; cx < 40; cx++) {
            for (int cz = -40; cz < 40; cz++) {
                typical.markExplored(A, "minecraft:overworld", cx, cz, 4096);
            }
        }
        MapPlayerState capped = new MapPlayerState();
        for (int i = 0; i < 256; i++) {
            capped.addWaypoint(A, wp("A long waypoint name number " + i, i), 256, "", null);
        }
        // 4096 regions fully explored in one dimension: the per-dimension cap
        for (int rx = 0; rx < 64; rx++) {
            for (int rz = 0; rz < 64; rz++) {
                for (int k = 0; k < 1024; k += 7) {
                    capped.markExplored(A, "minecraft:overworld", rx * 32 + (k & 31), rz * 32 + (k >> 5), 4096);
                }
            }
        }
        assertEquals(4096, capped.find(A).orElseThrow().explored().regionCount("minecraft:overworld"));
        assertFalse(capped.markExplored(A, "minecraft:overworld", 100_000, 100_000, 4096)); // past the cap
        int t = bytes(typical);
        int c = bytes(capped);
        PerfLog.line("store bytes (gzip NBT): typical record (40 waypoints, 6400 chunks) " + t
                + " B; capped record (256 waypoints, 4096 regions) " + c + " B");
        assertTrue(c < 2 * 1024 * 1024, "a capped record stays under 2 MiB on disk: " + c);
        MapPlayerState back = roundTrip(capped);
        assertEquals(4096, back.find(A).orElseThrow().explored().regionCount("minecraft:overworld"));
    }
}
