package za.co.neroland.neromaps.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapMilestoneRulesTest {

    @Test
    void rungsCountUpThroughTheLadder() {
        assertEquals(0, MapMilestoneRules.rungsReached(0));
        assertEquals(0, MapMilestoneRules.rungsReached(999));
        assertEquals(1, MapMilestoneRules.rungsReached(1_000));
        assertEquals(1, MapMilestoneRules.rungsReached(9_999));
        assertEquals(2, MapMilestoneRules.rungsReached(10_000));
        assertEquals(3, MapMilestoneRules.rungsReached(50_000));
        assertEquals(3, MapMilestoneRules.rungsReached(Long.MAX_VALUE));
    }

    @Test
    void rungsAreAscending() {
        int[] r = MapMilestoneRules.EXPLORED_RUNGS;
        for (int i = 1; i < r.length; i++) {
            assertEquals(true, r[i] > r[i - 1]);
        }
    }
}
