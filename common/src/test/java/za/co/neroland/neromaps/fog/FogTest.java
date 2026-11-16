package za.co.neroland.neromaps.fog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.ExploredMap;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.tile.Fixtures;
import za.co.neroland.neromaps.tile.PerfLog;
import za.co.neroland.neromaps.tile.Tile;

class FogTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final String OW = "minecraft:overworld";

    @Test
    void tileQueriesAgreeWithChunkBits() {
        Random r = new Random(4);
        ExploredMap m = new ExploredMap();
        for (int i = 0; i < 3000; i++) {
            m.mark(OW, r.nextInt(200) - 100, r.nextInt(200) - 100, 4096);
        }
        for (int tx = -7; tx <= 7; tx++) {
            for (int tz = -7; tz <= 7; tz++) {
                long[] mask = new long[4];
                m.orTileMask(OW, tx, tz, mask);
                boolean any = false;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        boolean e = m.isExplored(OW, tx * 16 + x, tz * 16 + z);
                        int i = z * 16 + x;
                        assertEquals(e, (mask[i >>> 6] & (1L << (i & 63))) != 0);
                        any |= e;
                    }
                }
                assertEquals(any, m.anyInTile(OW, tx, tz));
            }
        }
    }

    @Test
    void visibilityIsOwnPlusGrantedAndRevocationIsImmediate() {
        MapPlayerState s = new MapPlayerState();
        s.markExplored(A, OW, 0, 0, 4096);
        s.markExplored(B, OW, 100, 100, 4096);
        assertTrue(Visibility.of(s, A, OW, false).chunk(0, 0));
        assertFalse(Visibility.of(s, A, OW, false).chunk(100, 100), "another player's exploration is hidden");
        assertFalse(Visibility.of(s, A, OW, false).anyInTile(6, 6));
        ConsentLedger l = new ConsentLedger(s, new Random(1)::nextLong);
        long[] id = new long[1];
        l.offer(B, A, ShareScope.map(OW), 1, id);
        assertFalse(Visibility.of(s, A, OW, false).chunk(100, 100), "an offer is not consent");
        l.accept(A, id[0]);
        assertTrue(Visibility.of(s, A, OW, false).chunk(100, 100));
        assertFalse(Visibility.of(s, B, OW, false).chunk(0, 0), "sharing is one-way");
        assertFalse(Visibility.of(s, A, "minecraft:the_nether", false).chunk(100, 100));
        l.revoke(A, B, null);
        assertFalse(Visibility.of(s, A, OW, false).chunk(100, 100), "revoked on the next read");
        assertTrue(Visibility.of(s, A, OW, true).chunk(5000, 5000), "public map");
    }

    @Test
    void maskedTilesCarryNothingHiddenAndEtagsDiffer() {
        MapPlayerState s = new MapPlayerState();
        for (int cz = 0; cz < 8; cz++) {
            for (int cx = 0; cx < 16; cx++) {
                s.markExplored(A, OW, cx, cz, 4096); // the northern half of tile (0, 0)
            }
        }
        Visibility v = Visibility.of(s, A, OW, false);
        TileMask m = TileMask.of(v, 0, 0, 0);
        assertFalse(m.none());
        assertFalse(m.all());
        Tile t = Fixtures.plains();
        Tile masked = m.apply(t);
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                int i = Tile.index(x, z);
                if (z >= 128) {
                    assertFalse(masked.present(i), "hidden column leaked");
                } else {
                    assertEquals(t.present(i), masked.present(i));
                }
            }
        }
        assertNotEquals(1234L, m.etag(1234L));
        assertTrue(TileMask.of(v, 0, 5, 5).none(), "a tile outside the fog is hidden entirely");
        assertEquals(1234L, TileMask.of(Visibility.of(s, A, OW, true), 0, 0, 0).etag(1234L));
        // LOD 2: one chunk is one 4 x 4 pixel block
        TileMask m2 = TileMask.of(v, 2, 0, 0);
        Tile lod2 = m2.apply(Fixtures.plains());
        assertTrue(lod2.present(Tile.index(0, 0)) || !Fixtures.plains().present(Tile.index(0, 0)));
        assertFalse(lod2.present(Tile.index(0, 40)), "chunk z=10 of the LOD-2 tile is hidden");
        long t0 = System.nanoTime();
        int n = 200;
        for (int i = 0; i < n; i++) {
            m.apply(t);
        }
        long mask0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            TileMask.of(v, 2, 0, 0);
        }
        long mask2 = System.nanoTime();
        PerfLog.line(String.format("M5 masked tile build %.1f us/tile (LOD 0 half visible); LOD-2 mask (4096 chunk "
                + "checks) %.1f us", (mask0 - t0) / 1000.0 / n, (mask2 - mask0) / 1000.0 / n));
    }
}
