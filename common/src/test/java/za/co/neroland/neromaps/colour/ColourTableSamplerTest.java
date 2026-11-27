package za.co.neroland.neromaps.colour;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** The client texture sampler's place in the colour resolution order, and live biome colours. */
class ColourTableSamplerTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void overridesThenTexturesThenTableThenMapColour() {
        AtomicInteger samples = new AtomicInteger();
        ColourTable t = new ColourTable(id -> 0x010101, (id, tableTint) -> {
            samples.incrementAndGet();
            if (id.equals("a:grass")) {
                assertEquals(ColourTable.TINT_GRASS, tableTint, "the sampler is told the table's tint class");
                return (ColourTable.TINT_GRASS << 24) | 0x909090;
            }
            if (id.equals("a:untextured")) {
                assertEquals(-1, tableTint, "no table entry");
                return -1;
            }
            return id.equals("a:stone") ? 0x777777 : -1;
        });
        t.merge(json("{\"blocks\":{\"a:stone\":{\"rgb\":\"ff0000\"},\"a:grass\":{\"rgb\":\"00ff00\",\"tint\":\"grass\"},"
                + "\"a:fluid\":{\"rgb\":\"0000ff\"}}}"));
        t.mergeOverride(json("{\"blocks\":{\"a:painted\":{\"rgb\":\"abcdef\"}}}"));
        assertEquals(0x777777, ColourTable.rgb(t.block("a:stone")), "the texture wins over the bundled table");
        assertEquals(ColourTable.TINT_GRASS, ColourTable.tint(t.block("a:grass")));
        assertEquals(0x0000FF, ColourTable.rgb(t.block("a:fluid")), "unreadable texture: the bundled table");
        assertEquals(0x010101, ColourTable.rgb(t.block("a:untextured")), "nothing else: the map colour");
        assertEquals(0xABCDEF, ColourTable.rgb(t.block("a:painted")), "an explicit override always wins");
        int before = samples.get();
        t.block("a:stone");
        t.block("a:fluid");
        assertEquals(before, samples.get(), "each block is sampled once");
        assertTrue(t.hasEntry("a:painted"));
    }

    @Test
    void aFailingSamplerNeverEscapes() {
        ColourTable t = new ColourTable(id -> -1, (id, tint) -> {
            throw new IllegalStateException("models not baked");
        });
        t.merge(json("{\"blocks\":{\"a:b\":{\"rgb\":\"123456\"}}}"));
        assertEquals(0x123456, ColourTable.rgb(t.block("a:b")));
    }

    @Test
    void liveBiomeColoursWinAndRedrawTiles() {
        ColourTable t = new ColourTable(id -> -1);
        t.merge(json("{\"biomes\":{\"a:plains\":{\"grass\":\"111111\",\"foliage\":\"222222\",\"water\":\"333333\"}}}"));
        int v = t.version();
        t.setBiomeColours("a:plains", 0x111111, 0x222222, 0x333333);
        assertEquals(v, t.version(), "same colours: nothing to redraw");
        t.setBiomeColours("a:plains", 0xFF445566, 0x222222, 0x333333);
        assertNotEquals(v, t.version(), "new colours move the version on");
        assertArrayEquals(new int[] {0x445566, 0x222222, 0x333333}, t.biome("a:plains"));
        t.setBiomeColours("x:planet", 0xAA0000, 0x00AA00, 0x0000AA);
        assertArrayEquals(new int[] {0xAA0000, 0x00AA00, 0x0000AA}, t.biome("x:planet"), "a modded biome");
    }
}
