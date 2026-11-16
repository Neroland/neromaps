package za.co.neroland.neromaps.tile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Round-trip, fuzz and size tests for the one tile codec (decision 002). */
class TileCodecTest {

    private static void roundTrip(Tile t) throws TileCorruptException {
        byte[] enc = TileCodec.encode(t);
        Tile back = TileCodec.decode(enc);
        assertTrue(t.sameColumns(back), "columns differ after a round trip");
        assertEquals(t.lod, back.lod);
        assertArrayEquals(enc, TileCodec.encode(back), "re-encoding must be byte-identical (deterministic)");
    }

    @Test
    void roundTripsFixturesAndRandomTiles() throws TileCorruptException {
        roundTrip(Fixtures.plains());
        roundTrip(Fixtures.ocean());
        roundTrip(Fixtures.mountains());
        roundTrip(new Tile(0));
        roundTrip(Fixtures.random(7, 40, 8, 0.6));
        roundTrip(Fixtures.random(8, 3000, 256, 1.0)); // wide (u16) block palette
    }

    @Test
    void roundTripsHigherLods() throws TileCorruptException {
        Tile p = TileLod.downsample(Fixtures.plains(), Fixtures.ocean(), Fixtures.mountains(), null, 1);
        roundTrip(p);
    }

    @Test
    void hashIsAContentEtag() throws TileCorruptException {
        Tile a = Fixtures.plains();
        long h1 = TileCodec.contentHash(TileCodec.encode(a));
        // same columns through a different palette history: same hash
        Tile b = new Tile(0);
        b.internBlock("minecraft:unused");
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                int i = Tile.index(x, z);
                b.set(x, z, a.flags[i], a.height[i], b.internBlock(a.blockId(a.block[i])),
                        b.internBiome(a.biomeId(a.biome[i])), a.depth[i] & 0xFF);
            }
        }
        assertEquals(h1, TileCodec.contentHash(TileCodec.encode(b)));
        a.set(10, 10, Tile.F_PRESENT, 99, 0, 0, 0);
        assertNotEquals(h1, TileCodec.contentHash(TileCodec.encode(a)));
    }

    @Test
    void unknownSchemaAndTruncationAreCorrupt() {
        byte[] enc = TileCodec.encode(Fixtures.plains());
        byte[] wrongSchema = enc.clone();
        wrongSchema[0] = 2;
        assertThrows(TileCorruptException.class, () -> TileCodec.decode(wrongSchema));
        assertThrows(TileCorruptException.class, () -> TileCodec.decode(java.util.Arrays.copyOf(enc, enc.length - 5)));
        byte[] trailing = java.util.Arrays.copyOf(enc, enc.length + 3);
        assertThrows(TileCorruptException.class, () -> TileCodec.decode(trailing));
        assertThrows(TileCorruptException.class, () -> TileCodec.decode(new byte[0]));
        assertThrows(TileCorruptException.class, () -> TileCodec.decode(null));
    }

    @Test
    void fuzzNeverThrowsAnythingButCorrupt() {
        Random r = new Random(42);
        byte[] base = TileCodec.encode(Fixtures.random(5, 20, 4, 0.8));
        int corrupt = 0;
        for (int i = 0; i < 2000; i++) {
            byte[] b;
            if (i % 4 == 0) {
                b = new byte[r.nextInt(64)];
                r.nextBytes(b);
            } else {
                b = base.clone();
                int flips = 1 + r.nextInt(4);
                for (int k = 0; k < flips; k++) {
                    int p = r.nextInt(b.length);
                    b[p] ^= (byte) (1 << r.nextInt(8));
                }
            }
            try {
                TileCodec.decode(b);
            } catch (TileCorruptException e) {
                corrupt++;
            } catch (Throwable t) {
                fail("decode threw " + t);
            }
        }
        assertTrue(corrupt > 1900, "nearly every damaged tile must be detected (hash), got " + corrupt);
    }

    @Test
    void sizesMeetTheTargets() {
        int plains = TileCodec.encode(Fixtures.plains()).length;
        int ocean = TileCodec.encode(Fixtures.ocean()).length;
        int mountains = TileCodec.encode(Fixtures.mountains()).length;
        PerfLog.line("M1 size plains " + plains + " B, ocean " + ocean + " B, mountains " + mountains + " B");
        assertTrue(plains <= 48 * 1024, "plains " + plains);
        assertTrue(ocean <= 16 * 1024, "ocean " + ocean);
        assertTrue(TileCodec.encode(new Tile(0)).length < 1024, "an empty tile is tiny");
    }

    @Test
    void heightsAreClampedToTheCodecRange() throws TileCorruptException {
        Tile t = new Tile(0);
        int b = t.internBlock("a:b");
        int bi = t.internBiome("a:c");
        t.set(0, 0, Tile.F_PRESENT, 40000, b, bi, 0);
        t.set(1, 0, Tile.F_PRESENT, -40000, b, bi, 0);
        Tile back = TileCodec.decode(TileCodec.encode(t));
        assertEquals(Tile.MAX_HEIGHT, back.height[0]);
        assertEquals(Tile.MIN_HEIGHT, back.height[1]);
    }
}
