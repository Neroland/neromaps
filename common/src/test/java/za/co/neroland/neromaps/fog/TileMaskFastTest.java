package za.co.neroland.neromaps.fog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.store.ConsentLedger;
import za.co.neroland.neromaps.store.MapPlayerState;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.tile.PerfLog;

/** The region-wise {@link TileMask#of} agrees bit for bit with the per-chunk reference at every LOD. */
class TileMaskFastTest {

    static final UUID A = new UUID(1, 1);
    static final UUID B = new UUID(2, 2);
    static final String OW = "minecraft:overworld";

    @Test
    void regionMaskMatchesTheChunkReference() {
        MapPlayerState s = new MapPlayerState();
        Random r = new Random(42);
        for (int i = 0; i < 6000; i++) {
            s.markExplored(A, OW, r.nextInt(1200) - 600, r.nextInt(1200) - 600, 4096);
            s.markExplored(B, OW, r.nextInt(300) + 700, r.nextInt(300) - 900, 4096);
        }
        for (int i = -40; i < 40; i++) { // a solid strip across tile and region edges
            for (int j = -3; j < 3; j++) {
                s.markExplored(A, OW, i, j, 4096);
            }
        }
        long[] id = new long[1];
        new ConsentLedger(s, () -> 5L).offer(B, A, ShareScope.map(OW), 1, id);
        new ConsentLedger(s, () -> 0L).accept(A, id[0]);
        Visibility v = Visibility.of(s, A, OW, false);
        long fast = 0;
        long slow = 0;
        for (int lod = 0; lod <= 4; lod++) {
            int range = 4 >> Math.min(lod, 2);
            for (int tz = -range - 2; tz <= range + 1; tz++) {
                for (int tx = -range - 2; tx <= range + 1; tx++) {
                    long t0 = System.nanoTime();
                    TileMask f = TileMask.of(v, lod, tx, tz);
                    long t1 = System.nanoTime();
                    TileMask c = TileMask.ofChunks(v, lod, tx, tz);
                    long t2 = System.nanoTime();
                    fast += t1 - t0;
                    slow += t2 - t1;
                    assertTrue(f.sameAs(c), "lod " + lod + " tile " + tx + "," + tz);
                }
            }
        }
        Visibility pub = Visibility.of(s, A, OW, true);
        assertTrue(TileMask.of(pub, 4, 3, -2).all());
        assertTrue(TileMask.of(Visibility.of(s, UUID.randomUUID(), OW, false), 3, 0, 0).none());
        PerfLog.line(String.format("TileMask region path %.2f ms vs per-chunk %.2f ms over the sweep",
                fast / 1e6, slow / 1e6));
    }
}
