package za.co.neroland.neromaps.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

class PayloadCodecTest {

    static <T> T trip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "the decoder consumes exactly what the encoder wrote");
        return back;
    }

    static final Waypoint W = new Waypoint(7, "trip", "Home \u00A7rbase", 0x123456, "neromaps:glyph/home",
            "minecraft:the_nether", -30_000, 120, 45, true, false, true);

    @Test
    void everyPayloadRoundTrips() {
        PolicySnapshot pol = new PolicySnapshot(3, false, true, false, true, false, true, true, false, true, 77, true,
                12, false, true);
        assertEquals(new Payloads.Hello("abc", pol, 1), trip(Payloads.Hello.STREAM_CODEC,
                new Payloads.Hello("abc", pol, 1)));
        Payloads.Waypoints wps = new Payloads.Waypoints(List.of(W), List.of(new DeathPoint("minecraft:overworld", 1, 2, 3)),
                List.of(new Payloads.SharedWaypoint("Alex", W)));
        assertEquals(wps, trip(Payloads.Waypoints.STREAM_CODEC, wps));
        assertEquals("Home base", W.name()); // filtered at construction
        Payloads.Shares sh = new Payloads.Shares(List.of(new Payloads.ShareLine("Alex", ShareScope.map("*"), 0)),
                List.of(new Payloads.ShareLine("#00ff00aa", ShareScope.waypoint(3), 0)),
                List.of(new Payloads.ShareLine("Sam", ShareScope.livePosition(), 99L)), false, true);
        assertEquals(sh, trip(Payloads.Shares.STREAM_CODEC, sh));
        Payloads.Positions pos = new Payloads.Positions(List.of(new Payloads.Position("Alex", "minecraft:overworld", true,
                5, -5), new Payloads.Position("Sam", "minecraft:the_end", false, 0, 0)));
        assertEquals(pos, trip(Payloads.Positions.STREAM_CODEC, pos));
        Payloads.Notice n = new Payloads.Notice("neromaps.share.request", List.of("Alex", "neromaps.share.kind.map"), 5L);
        assertEquals(n, trip(Payloads.Notice.STREAM_CODEC, n));
        Payloads.LayerCatalogue cat = new Payloads.LayerCatalogue(List.of(new Payloads.LayerInfo("nerospace:stations",
                "map.nerospace.stations", "nerospace:station", true, false)));
        assertEquals(cat, trip(Payloads.LayerCatalogue.STREAM_CODEC, cat));
        Payloads.LayerItem m = new Payloads.LayerItem("nerospace:pads", "pad/1", 0, 1, 2, 3, "nerospace:pad",
                "map.pad", false, List.of("A"), 0xFF00FF00, 0, 0, new int[0], "neromaps:badge_friendly");
        Payloads.LayerItem a = new Payloads.LayerItem("nerocolonies:border", "c1", 3, 8, 0, 8, "", "Colony", true,
                List.of(), 0, 0x3000FF00, 0xFF00FF00, new int[] {0, 0, 1, 0, 1, 1}, "");
        Payloads.LayerDelta d = new Payloads.LayerDelta("minecraft:overworld", true, List.of(m, a), List.of("x|y"));
        assertEquals(d, trip(Payloads.LayerDelta.STREAM_CODEC, d));
        Payloads.DimensionBadges db = new Payloads.DimensionBadges(List.of(new Payloads.DimensionBadge(
                "nerospace:visited/luna", "nerospace:luna", "neromaps:star_chart", "map.nerospace.visited", false)));
        assertEquals(db, trip(Payloads.DimensionBadges.STREAM_CODEC, db));
        Payloads.TileData td = trip(Payloads.TileData.STREAM_CODEC, new Payloads.TileData("minecraft:overworld", 2, -3,
                4, 99L, new byte[] {1, 2, 3}));
        assertEquals(2, td.lod());
        assertEquals(-3, td.tx());
        assertArrayEquals(new byte[] {1, 2, 3}, td.data());
        Payloads.Perf perf = new Payloads.Perf(Payloads.Perf.DUMP, List.of("a", "b"));
        assertEquals(perf, trip(Payloads.Perf.STREAM_CODEC, perf));
        Payloads.WaypointEdit we = new Payloads.WaypointEdit(Payloads.WaypointEdit.UPDATE, W, 2);
        assertEquals(we, trip(Payloads.WaypointEdit.STREAM_CODEC, we));
        Payloads.ShareAction sa = new Payloads.ShareAction(Payloads.ShareAction.REVOKE, "Alex", 12L,
                ShareScope.waypoint(4), true);
        assertEquals(sa, trip(Payloads.ShareAction.STREAM_CODEC, sa));
        Payloads.PrivacySet ps = new Payloads.PrivacySet(false, true);
        assertEquals(ps, trip(Payloads.PrivacySet.STREAM_CODEC, ps));
        Payloads.LayerView lv = new Payloads.LayerView("minecraft:overworld", -100, -200, 300, 400, true);
        assertEquals(lv, trip(Payloads.LayerView.STREAM_CODEC, lv));
        Payloads.TileRequest tr = new Payloads.TileRequest("minecraft:overworld", 1, new long[] {1L, 2L},
                new long[] {0L, 9L});
        assertEquals(tr, trip(Payloads.TileRequest.STREAM_CODEC, tr));
    }

    @Test
    void policyFlagsRoundTrip() {
        for (int f = 0; f < 4096; f += 37) {
            PolicySnapshot p = PolicySnapshot.of(1, f, 10, 9);
            assertEquals(f, p.flags());
        }
    }

    @Test
    void hostileInputIsBounded() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Wire.string(buf, "minecraft:overworld");
        buf.writeByte(0);
        buf.writeVarInt(1_000_000); // claims a million tiles
        assertThrows(IllegalArgumentException.class, () -> Payloads.TileRequest.STREAM_CODEC.decode(buf));
        FriendlyByteBuf b2 = new FriendlyByteBuf(Unpooled.buffer());
        b2.writeVarInt(Payloads.MAX_WAYPOINTS + 1);
        assertThrows(IllegalArgumentException.class, () -> Payloads.Waypoints.STREAM_CODEC.decode(b2));
        FriendlyByteBuf b3 = new FriendlyByteBuf(Unpooled.buffer());
        b3.writeByte(99); // out-of-range op
        Payloads.writeWaypoint(b3, W);
        b3.writeVarInt(0);
        assertEquals(0, Payloads.WaypointEdit.STREAM_CODEC.decode(b3).op()); // clamped to CREATE, validated later
        FriendlyByteBuf b4 = new FriendlyByteBuf(Unpooled.buffer());
        Wire.string(b4, "d");
        b4.writeByte(0);
        b4.writeVarInt(0);
        b4.writeVarInt(0);
        b4.writeLong(0);
        b4.writeVarInt(Integer.MAX_VALUE); // a huge byte array
        assertThrows(IllegalArgumentException.class, () -> Payloads.TileData.STREAM_CODEC.decode(b4));
    }

    @Test
    void rateLimiterRefills() {
        AtomicLong now = new AtomicLong();
        RateLimiter r = new RateLimiter(3, 100, now::get);
        UUID p = UUID.randomUUID();
        assertTrue(r.tryAcquire(p));
        assertTrue(r.tryAcquire(p));
        assertTrue(r.tryAcquire(p));
        assertFalse(r.tryAcquire(p));
        now.addAndGet(100_000_000L);
        assertTrue(r.tryAcquire(p));
        assertFalse(r.tryAcquire(p));
        assertTrue(r.tryAcquire(UUID.randomUUID()), "buckets are per player");
        r.forget(p);
        assertEquals(1, r.tracked());
    }
}
