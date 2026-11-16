package za.co.neroland.neromaps.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.tile.Tile;

class ColourTableTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void laterFilesWinPerKey() {
        ColourTable t = new ColourTable(id -> -1);
        t.merge(json("{\"version\":1,\"blocks\":{\"minecraft:stone\":{\"rgb\":\"7d7d7d\",\"tint\":\"none\"},"
                + "\"minecraft:grass_block\":{\"rgb\":\"939393\",\"tint\":\"grass\"}}}"));
        t.merge(json("{\"blocks\":{\"minecraft:stone\":{\"rgb\":\"ff0000\"}}}"));
        assertEquals(0xFF0000, ColourTable.rgb(t.block("minecraft:stone")));
        assertEquals(ColourTable.TINT_GRASS, ColourTable.tint(t.block("minecraft:grass_block")));
        assertEquals(0, t.fallbackCount());
    }

    @Test
    void missingBlocksFallBackToTheVanillaMapColourOnce() {
        AtomicInteger calls = new AtomicInteger();
        ColourTable t = new ColourTable(id -> {
            calls.incrementAndGet();
            return id.equals("modx:thing") ? 0x123456 : -1;
        });
        assertFalse(t.hasEntry("modx:thing"));
        assertEquals(0x123456, ColourTable.rgb(t.block("modx:thing")));
        assertEquals(0x123456, ColourTable.rgb(t.block("modx:thing")));
        assertEquals(1, calls.get(), "the fallback is memoised");
        assertEquals(ColourTable.UNKNOWN, ColourTable.rgb(t.block("modx:unknown")));
        ColourTable broken = new ColourTable(id -> {
            throw new IllegalStateException("no registry");
        });
        assertEquals(ColourTable.UNKNOWN, ColourTable.rgb(broken.block("a:b")), "a failing fallback never escapes");
    }

    @Test
    void malformedEntriesAreSkipped() {
        ColourTable t = new ColourTable(id -> 0x010203);
        t.merge(json("{\"blocks\":{\"a:b\":{\"rgb\":\"zz\"},\"a:c\":7,\"a:d\":{\"rgb\":\"00ff00\"}},\"biomes\":[]}"));
        assertEquals(0x010203, ColourTable.rgb(t.block("a:b")));
        assertEquals(0x00FF00, ColourTable.rgb(t.block("a:d")));
        assertEquals(ColourTable.DEFAULT_WATER, t.biome("x:y")[2]);
    }

    @Test
    void shippedVanillaTableLoadsAndCoversCommonBlocks() throws Exception {
        var in = ColourTableTest.class.getResourceAsStream("/assets/neromaps/map_colours/minecraft.json");
        if (in == null) {
            return; // resources not on this test classpath
        }
        ColourTable t = new ColourTable(id -> -1);
        try (var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            t.merge(JsonParser.parseReader(r).getAsJsonObject());
        }
        assertTrue(t.size() > 500, "vanilla table has " + t.size());
        for (String id : new String[] {"minecraft:stone", "minecraft:grass_block", "minecraft:sand",
                "minecraft:water", "minecraft:oak_leaves", "minecraft:snow_block"}) {
            assertTrue(t.hasEntry(id), id);
        }
        assertEquals(ColourTable.TINT_GRASS, ColourTable.tint(t.block("minecraft:grass_block")));
        assertEquals(ColourTable.TINT_FOLIAGE, ColourTable.tint(t.block("minecraft:oak_leaves")));
    }

    @Test
    void rasterStylesAndFog() {
        ColourTable table = new ColourTable(id -> 0x808080);
        table.merge(json("{\"blocks\":{\"minecraft:stone\":{\"rgb\":\"808080\"}},"
                + "\"biomes\":{\"minecraft:ocean\":{\"grass\":\"00ff00\",\"foliage\":\"00ff00\",\"water\":\"0000ff\"}}}"));
        Tile t = new Tile(0);
        int stone = t.internBlock("minecraft:stone");
        int ocean = t.internBiome("minecraft:ocean");
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (x < 8) {
                    t.set(x, z, Tile.F_PRESENT, 64, stone, ocean, 0);
                } else {
                    t.set(x, z, Tile.F_PRESENT | Tile.F_WATER, 62, stone, ocean, x == 15 ? 20 : 4);
                }
            }
        }
        RasterOptions o = new RasterOptions();
        int[] px = new int[256];
        MapRasterizer.chunk(t, 0, 0, 0, 0, TileLookup.NONE, table, o, px, 0, 16);
        assertEquals(0xFF808080, px[3 * 16 + 3], "flat land, no slope");
        int deep = px[3 * 16 + 15];
        int shallow = px[3 * 16 + 12];
        assertTrue((deep & 0xFF) < (shallow & 0xFF), "deeper water is darker");
        o.set(MapStyle.HIGH_CONTRAST, false, false, 63);
        MapRasterizer.chunk(t, 0, 0, 0, 0, TileLookup.NONE, table, o, px, 0, 16);
        assertEquals(0xFFFFFFFF, px[3 * 16 + 8], "water touching land is a white shoreline in high contrast");
        assertEquals(0xFF0B2A7A, px[3 * 16 + 12]);
        MapRasterizer.chunk(null, 5, 5, 0, 0, TileLookup.NONE, table, o, px, 0, 16);
        assertEquals(0xFF000000, px[0], "fog in high contrast is black");
        o.set(MapStyle.DAY_NIGHT, false, false, 63);
        MapRasterizer.chunk(null, 5, 5, 0, 0, TileLookup.NONE, table, o, px, 0, 16);
        for (int p : px) {
            assertTrue(p == MapPalette.VOID || p == MapPalette.VOID_STAR);
        }
    }

    @Test
    void nightTintAndDarkness() {
        assertEquals(0xFFFFFFFF, MapRasterizer.nightTint(0f));
        assertEquals(0f, MapRasterizer.darkness(6000));
        assertEquals(1f, MapRasterizer.darkness(18000));
        int n = MapRasterizer.nightTint(1f);
        assertTrue(((n >> 16) & 0xFF) < 255 && (n & 0xFF) < 255);
    }
}
