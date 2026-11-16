package za.co.neroland.neromaps.server.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The in-game tile path's admission: bytes per minute, tiles in flight per viewer, and room left for capture. */
class TileBudgetTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void oneViewerCannotFloodTheNetworkOrStarveTheWorker() {
        TileBudget b = new TileBudget(LinkTiles.VIEWER_BYTES_PER_MINUTE, TileService.MAX_INFLIGHT, TileService.MAX_QUEUED);
        // in flight: A stops at its own cap, however often it asks
        int reserved = 0;
        for (int i = 0; i < 10_000; i++) {
            if (b.tryReserve(A)) {
                reserved++;
            }
        }
        assertEquals(TileService.MAX_INFLIGHT, reserved);
        assertTrue(b.queued() <= TileService.MAX_QUEUED);
        assertTrue(TileService.MAX_QUEUED <= CaptureService.QUEUE / 2, "capture keeps most of its queue");
        // B still gets in
        assertTrue(b.tryReserve(B));
        // jobs finishing free A's slots
        b.dequeued();
        b.finished(A);
        assertTrue(b.tryReserve(A));

        // bytes: past the minute's budget A is refused until the window turns
        long now = 1_000_000L;
        assertFalse(b.overBudget(A, now));
        for (int i = 0; i < 200; i++) {
            b.sent(A, 64 * 1024, now);
        }
        assertTrue(b.overBudget(A, now + 1_000));
        assertFalse(b.overBudget(B, now + 1_000), "per viewer");
        assertFalse(b.overBudget(A, now + 60_000), "a new minute");
    }

    @Test
    void allViewersTogetherLeaveRoomForCapture() {
        TileBudget b = new TileBudget(LinkTiles.VIEWER_BYTES_PER_MINUTE, TileService.MAX_INFLIGHT, TileService.MAX_QUEUED);
        int reserved = 0;
        for (int v = 0; v < 64; v++) {
            UUID id = new UUID(7, v);
            for (int i = 0; i < 100; i++) {
                if (b.tryReserve(id)) {
                    reserved++;
                }
            }
        }
        assertEquals(TileService.MAX_QUEUED, reserved);
        assertEquals(TileService.MAX_QUEUED, b.queued());
        b.forget(A);
        b.reset();
        assertEquals(0, b.queued());
    }
}
