package za.co.neroland.neromaps.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/** NeroMaps' Core export: the player's own summary, never another player's UUID, null when nothing is held. */
class CoreExportTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static Waypoint wp(String name) {
        return new Waypoint(0, Waypoint.DEFAULT_SET, name, 0xE69F00, Waypoint.DEFAULT_ICON, "minecraft:overworld", 1,
                64, 2, false, true, true);
    }

    @Test
    void nothingHeldExportsNull() {
        assertNull(CoreExport.summary(new MapPlayerState(), A));
    }

    @Test
    void summaryHoldsOwnDataAndNoOtherUuid() {
        MapPlayerState s = new MapPlayerState();
        s.addWaypoint(A, wp("Home"), 256, "", null);
        s.addWaypoint(B, wp("Someone else's base"), 256, "", null);
        s.markExplored(A, "minecraft:overworld", 5, -7, 4096);
        long[] req = new long[1];
        new ConsentLedger(s, () -> 42L).offer(A, B, ShareScope.map(ShareScope.ALL), 100, req);
        new ConsentLedger(s, () -> 43L).accept(B, req[0]);

        JsonObject a = CoreExport.summary(s, A);
        JsonObject b = CoreExport.summary(s, B);

        assertEquals("Home", a.getAsJsonArray("waypoints").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1L, a.getAsJsonObject("exploredChunksByDimension").get("minecraft:overworld").getAsLong());
        assertEquals(1, a.getAsJsonArray("sharesYouGave").size());
        assertEquals(1, b.getAsJsonArray("sharesGivenToYou").size());
        assertFalse(a.toString().contains(B.toString()), "never the other player's UUID");
        assertFalse(b.toString().contains(A.toString()), "never the sharer's UUID");
        assertFalse(a.toString().contains(A.toString()), "not even the subject's own UUID");
        assertFalse(a.toString().contains("Someone else"), "never another player's waypoint");
    }
}
