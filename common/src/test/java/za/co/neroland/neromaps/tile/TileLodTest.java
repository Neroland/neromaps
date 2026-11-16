package za.co.neroland.neromaps.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TileLodTest {

    @Test
    void highestColumnWinsUnlessTwoAreFluid() {
        Tile c = new Tile(0);
        int stone = c.internBlock("minecraft:stone");
        int sand = c.internBlock("minecraft:sand");
        int b = c.internBiome("minecraft:plains");
        c.set(0, 0, Tile.F_PRESENT, 70, stone, b, 0);
        c.set(1, 0, Tile.F_PRESENT, 80, sand, b, 0);
        c.set(0, 1, Tile.F_PRESENT, 60, stone, b, 0);
        // a 2x2 block with two fluid columns: shallowest fluid wins
        c.set(2, 0, Tile.F_PRESENT | Tile.F_WATER, 62, sand, b, 9);
        c.set(3, 0, Tile.F_PRESENT | Tile.F_WATER, 62, stone, b, 3);
        c.set(2, 1, Tile.F_PRESENT, 90, stone, b, 0);
        Tile p = TileLod.downsample(c, null, null, null, 1);
        assertEquals(1, p.lod);
        assertEquals(80, p.height[Tile.index(0, 0)]);
        assertEquals("minecraft:sand", p.blockId(p.block[Tile.index(0, 0)]));
        int i = Tile.index(1, 0);
        assertTrue((p.flags[i] & Tile.F_WATER) != 0);
        assertEquals(3, p.depth[i] & 0xFF);
        assertFalse(p.present(Tile.index(5, 5)), "all-absent stays absent");
        assertFalse(p.present(Tile.index(200, 10)), "a null child is unexplored");
    }

    @Test
    void quadrantsLandInPlace() {
        Tile m = Fixtures.mountains();
        Tile p = TileLod.downsample(null, null, null, m, 1);
        assertFalse(p.present(Tile.index(10, 10)));
        assertTrue(p.present(Tile.index(200, 200)));
    }
}
