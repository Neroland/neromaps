package za.co.neroland.neromaps.progression;

/**
 * Pure decisions for {@link MapMilestones}, free of game classes so they can be unit tested. Every input is data
 * NeroMaps already holds (the explored-chunk bitsets); nothing here adds a stored counter.
 */
public final class MapMilestoneRules {

    /** Explored-chunk rungs, ascending. Each opens gate {@code neromaps:explored/<rung>}. */
    public static final int[] EXPLORED_RUNGS = {1_000, 10_000, 50_000};

    private MapMilestoneRules() {
    }

    /** How many rungs {@code exploredChunks} has reached (0 = none). */
    public static int rungsReached(long exploredChunks) {
        int n = 0;
        for (int rung : EXPLORED_RUNGS) {
            if (exploredChunks >= rung) {
                n++;
            }
        }
        return n;
    }
}
