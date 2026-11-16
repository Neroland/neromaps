package za.co.neroland.neromaps.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileDiskCacheTest {

    @TempDir
    Path tmp;

    @Test
    void writesAtomicallyAndReadsBack() throws Exception {
        TileDiskCache c = new TileDiskCache(tmp.resolve("cache"), 1 << 20);
        Path dir = c.dimensionDir("abc", "default", "minecraft", "overworld");
        Path f = TileDiskCache.tileFile(dir, 0, -3, 7);
        c.write(f, new byte[] {1, 2, 3});
        assertTrue(c.exists(f));
        assertArrayEquals(new byte[] {1, 2, 3}, c.read(f));
        assertFalse(Files.exists(f.resolveSibling(f.getFileName() + ".tmp")), "no temp file left behind");
        assertEquals(3, c.totalBytes());
        c.delete(f);
        assertFalse(c.exists(f));
        assertNull(c.read(f));
    }

    @Test
    void evictsLeastRecentlyUsedUnderTheCap() throws Exception {
        TileDiskCache c = new TileDiskCache(tmp.resolve("cache"), 2500);
        Path dir = c.dimensionDir("abc", "default", "minecraft", "overworld");
        Path a = TileDiskCache.tileFile(dir, 0, 0, 0);
        Path b = TileDiskCache.tileFile(dir, 0, 1, 0);
        Path d = TileDiskCache.tileFile(dir, 0, 2, 0);
        c.write(a, new byte[1000]);
        Thread.sleep(5);
        c.write(b, new byte[1000]);
        Thread.sleep(5);
        c.read(a); // a is now more recent than b
        Thread.sleep(5);
        c.write(d, new byte[1000]);
        assertTrue(c.totalBytes() <= 2500);
        assertTrue(c.exists(a));
        assertFalse(c.exists(b), "the least recently used file goes first");
        assertTrue(c.exists(d));
        // a fresh instance learns the same state from disk
        TileDiskCache again = new TileDiskCache(tmp.resolve("cache"), 2500);
        again.scan();
        assertEquals(c.totalBytes(), again.totalBytes());
    }

    @Test
    void keysNeverContainTheAddressOrEscapeTheRoot() {
        String k = TileDiskCache.hashKey("mp:play.example.org");
        assertEquals(16, k.length());
        assertFalse(k.contains("example"));
        assertEquals("__", TileDiskCache.safe(".."));
        assertEquals("a_b", TileDiskCache.safe("a/b"));
        assertEquals("planets_luna", TileDiskCache.safe("planets/luna"));
        TileDiskCache c = new TileDiskCache(tmp, 1);
        assertTrue(c.dimensionDir("..", "..", "..", "..").normalize().startsWith(tmp));
    }
}
