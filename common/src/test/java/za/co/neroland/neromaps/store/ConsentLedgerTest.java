package za.co.neroland.neromaps.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConsentLedgerTest {

    static final UUID A = MapPlayerStateTest.A;
    static final UUID B = MapPlayerStateTest.B;
    static final UUID C = MapPlayerStateTest.C;

    @Test
    void offerAcceptRevokeLifecycle() {
        MapPlayerState s = new MapPlayerState();
        ConsentLedger l = new ConsentLedger(s, new Random(7)::nextLong);
        long[] id = new long[1];
        assertEquals(ConsentLedger.Offer.SENT, l.offer(A, B, ShareScope.livePosition(), 10, id));
        assertNotEquals(0, id[0]);
        assertFalse(l.hasGrant(A, B, ShareScope.Kind.LIVE_POSITION)); // not before acceptance
        assertEquals(1, s.find(B).orElseThrow().incoming().size());
        assertTrue(l.positionSharersFor(B).isEmpty());
        assertTrue(l.accept(B, id[0]) != null);
        assertTrue(l.hasGrant(A, B, ShareScope.Kind.LIVE_POSITION));
        assertEquals(1, l.positionSharersFor(B).size());
        assertTrue(l.positionSharersFor(A).isEmpty()); // one direction only
        assertEquals(ConsentLedger.Offer.ALREADY_SHARED, l.offer(A, B, ShareScope.livePosition(), 10, id));
        // either side revokes; effective immediately
        assertEquals(1, l.revoke(B, A, null).size());
        assertFalse(l.hasGrant(A, B, ShareScope.Kind.LIVE_POSITION));
        assertTrue(l.positionSharersFor(B).isEmpty());
    }

    @Test
    void forgedAndForeignAcceptsAreRejected() {
        MapPlayerState s = new MapPlayerState();
        ConsentLedger l = new ConsentLedger(s, new Random(3)::nextLong);
        long[] id = new long[1];
        l.offer(A, B, ShareScope.map("*"), 10, id);
        assertNull(l.accept(B, id[0] + 1), "an invented request id does nothing");
        assertNull(l.accept(C, id[0]), "a third player cannot accept someone else's request");
        assertNull(l.accept(A, id[0]), "the sharer cannot accept their own offer");
        assertFalse(l.hasGrant(A, B, ShareScope.Kind.EXPLORED_MAP));
        assertTrue(l.decline(B, id[0]) != null);
        assertNull(l.accept(B, id[0]), "a declined request is gone");
        assertFalse(l.hasGrant(A, B, ShareScope.Kind.EXPLORED_MAP));
    }

    @Test
    void refusalsCapsAndExpiry() {
        MapPlayerState s = new MapPlayerState();
        ConsentLedger l = new ConsentLedger(s, new Random(5)::nextLong);
        long[] id = new long[1];
        assertEquals(ConsentLedger.Offer.SELF, l.offer(A, A, ShareScope.livePosition(), 1, id));
        assertEquals(ConsentLedger.Offer.NO_SUCH_WAYPOINT, l.offer(A, B, ShareScope.waypoint(99), 1, id));
        s.setAcceptRequests(C, false);
        assertEquals(ConsentLedger.Offer.SENT, l.offer(A, C, ShareScope.livePosition(), 1, id));
        assertEquals(0, id[0]); // silently dropped
        assertTrue(s.find(C).orElseThrow().incoming().isEmpty());
        for (int i = 0; i < 20; i++) {
            l.offer(UUID.randomUUID(), B, ShareScope.livePosition(), i, id);
        }
        assertEquals(ShareRequest.MAX_PENDING, s.find(B).orElseThrow().incoming().size());
        // a full inbox drops new offers rather than evicting anyone's: days 0..15 are pending; on day 20 everything
        // older than 7 days (days 0..12) goes
        assertEquals(13, l.purgeExpired(20));
        assertEquals(3, s.find(B).orElseThrow().incoming().size());
        l.purgeExpired(1000);
        assertEquals(0, s.find(B).orElseThrow().incoming().size());
    }

    @Test
    void waypointShareFollowsTheWaypoint() {
        MapPlayerState s = new MapPlayerState();
        int[] wid = new int[1];
        s.addWaypoint(A, MapPlayerStateTest.wp("Cave", 5), 256, "", wid);
        ConsentLedger l = new ConsentLedger(s, new Random(9)::nextLong);
        long[] id = new long[1];
        l.offer(A, B, ShareScope.waypoint(wid[0]), 1, id);
        l.accept(B, id[0]);
        assertEquals(1, l.waypointsSharedWith(B).size());
        assertTrue(s.find(A).orElseThrow().waypoint(wid[0]).orElseThrow().shared());
        assertTrue(l.waypointsSharedWith(C).isEmpty());
        s.removeWaypoint(A, wid[0]);
        assertTrue(l.waypointsSharedWith(B).isEmpty());
        assertTrue(s.find(A).orElseThrow().grants().isEmpty());
    }

    @Test
    void mapSharersFollowGrants() {
        MapPlayerState s = new MapPlayerState();
        ConsentLedger l = new ConsentLedger(s, new Random(11)::nextLong);
        long[] id = new long[1];
        l.offer(A, B, ShareScope.map("minecraft:the_nether"), 1, id);
        l.accept(B, id[0]);
        assertEquals(1, l.mapSharersFor(B, "minecraft:the_nether").size());
        assertTrue(l.mapSharersFor(B, "minecraft:overworld").isEmpty());
        l.revoke(A, B, ShareScope.Kind.EXPLORED_MAP);
        assertTrue(l.mapSharersFor(B, "minecraft:the_nether").isEmpty());
    }

    @Test
    void aSenderCannotEvictOtherPlayersPendingOffers() {
        MapPlayerState s = new MapPlayerState();
        ConsentLedger l = new ConsentLedger(s, new Random(13)::nextLong);
        long[] id = new long[1];
        l.offer(C, B, ShareScope.livePosition(), 1, id);
        long fromC = id[0];
        // A floods B with offers of every scope it can name
        for (int i = 0; i < 40; i++) {
            l.offer(A, B, ShareScope.map("minecraft:dim_" + i), 2, id);
        }
        java.util.List<ShareRequest> in = s.find(B).orElseThrow().incoming();
        assertEquals(1 + ConsentLedger.MAX_PENDING_PER_SENDER, in.size());
        assertTrue(in.stream().anyMatch(q -> q.requestId() == fromC), "C's offer survives");
        assertEquals(ConsentLedger.MAX_PENDING_PER_SENDER, in.stream().filter(q -> q.from().equals(A)).count());
        assertTrue(in.stream().anyMatch(q -> q.scope().arg().equals("minecraft:dim_39")), "A keeps its newest");
        // other players still reach B until the inbox is full; past that new offers are dropped, nobody is evicted
        for (int i = 0; i < 30; i++) {
            l.offer(UUID.randomUUID(), B, ShareScope.livePosition(), 3, id);
        }
        in = s.find(B).orElseThrow().incoming();
        assertEquals(ShareRequest.MAX_PENDING, in.size());
        assertTrue(in.stream().anyMatch(q -> q.requestId() == fromC), "still there");
    }

    @Test
    void revokingTheLastWaypointGrantClearsItsSharedFlag() {
        MapPlayerState s = new MapPlayerState();
        int[] wid = new int[1];
        s.addWaypoint(A, MapPlayerStateTest.wp("Cave", 5), 256, "", wid);
        ConsentLedger l = new ConsentLedger(s, new Random(17)::nextLong);
        long[] id = new long[1];
        l.offer(A, B, ShareScope.waypoint(wid[0]), 1, id);
        l.accept(B, id[0]);
        l.offer(A, C, ShareScope.waypoint(wid[0]), 1, id);
        l.accept(C, id[0]);
        l.revoke(B, A, null);
        assertTrue(s.find(A).orElseThrow().waypoint(wid[0]).orElseThrow().shared(), "C still has it");
        l.revoke(A, C, ShareScope.Kind.WAYPOINT);
        assertFalse(s.find(A).orElseThrow().waypoint(wid[0]).orElseThrow().shared());
    }
}
