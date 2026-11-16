package za.co.neroland.neromaps.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Stage 7: the fog edge dither (decision 013 section 3) is deterministic and touches only the explored side. */
class FogEdgeTest {

    private static final int LAND = 0xFF3F9E78;

    @Test
    void ditherStepsFromTheFogOutward() {
        int voids1 = 0;
        int voids2 = 0;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                if (MapRasterizer.fogEdge(LAND, 1, x, z, MapStyle.DAY_NIGHT) == MapPalette.VOID) {
                    voids1++;
                }
                if (MapRasterizer.fogEdge(LAND, 2, x, z, MapStyle.DAY_NIGHT) == MapPalette.VOID) {
                    voids2++;
                }
            }
        }
        assertEquals(32, voids1, "50 % at one column from the fog");
        assertEquals(16, voids2, "25 % at two");
        int rim = MapRasterizer.fogEdge(LAND, 3, 5, 9, MapStyle.DAY_NIGHT);
        assertNotEquals(LAND, rim);
        assertNotEquals(MapPalette.VOID, rim);
        assertEquals(LAND, MapRasterizer.fogEdge(LAND, 4, 5, 9, MapStyle.DAY_NIGHT), "nothing farther in");
        assertEquals(MapRasterizer.fogEdge(LAND, 1, 3, 4, MapStyle.DAY_NIGHT),
                MapRasterizer.fogEdge(LAND, 1, 3, 4, MapStyle.DAY_NIGHT), "deterministic: never shimmers");
    }

    @Test
    void highContrastDrawsAWhiteEdge() {
        assertEquals(0xFFFFFFFF, MapRasterizer.fogEdge(LAND, 1, 0, 0, MapStyle.HIGH_CONTRAST));
        assertEquals(LAND, MapRasterizer.fogEdge(LAND, 2, 0, 0, MapStyle.HIGH_CONTRAST));
    }
}
