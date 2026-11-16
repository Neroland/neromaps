package za.co.neroland.neromaps.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.store.PlayerExport;

/** A server-sent export reaches the player's disk only through their own Save click, capped in size and count. */
class ExportInboxTest {

    @TempDir
    Path dir;

    static long files(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    void anUnaskedExportWritesNothingUntilThePlayerSavesIt() throws Exception {
        ExportInbox inbox = new ExportInbox();
        byte[] json = "{\"waypoints\":[]}".getBytes(StandardCharsets.UTF_8);
        // a server sends exports unasked, over and over: nothing reaches the disk
        for (int i = 0; i < 50; i++) {
            assertTrue(inbox.hold(PlayerExport.deflate(new String(json, StandardCharsets.UTF_8))));
        }
        assertEquals(0, files(dir));
        Path out = inbox.save(dir, ExportInbox.PREFIX + "20261116-120000.json");
        assertArrayEquals(json, Files.readAllBytes(out));
        assertNull(inbox.save(dir, ExportInbox.PREFIX + "20261116-120001.json"), "one click, one file");
        assertEquals(1, files(dir));
        inbox.hold(PlayerExport.deflate("{}"));
        inbox.clear(); // disconnect
        assertFalse(inbox.holding());
    }

    @Test
    void sizeAndCountAreCapped() throws Exception {
        ExportInbox inbox = new ExportInbox();
        assertFalse(inbox.hold(new byte[Payloads.Export.MAX_BYTES + 1]), "over the wire cap");
        assertFalse(inbox.hold(new byte[0]));
        // a small payload that inflates past the JSON cap is refused on save, nothing written
        String huge = "x".repeat(PlayerExport.MAX_JSON + 1024);
        assertTrue(inbox.hold(PlayerExport.deflate(huge)));
        assertThrows(IllegalArgumentException.class, () -> inbox.save(dir, ExportInbox.PREFIX + "a.json"));
        assertEquals(0, files(dir));
        for (int i = 0; i < ExportInbox.KEEP + 5; i++) {
            inbox.hold(PlayerExport.deflate("{}"));
            inbox.save(dir, String.format("%s20261116-1200%02d.json", ExportInbox.PREFIX, i));
        }
        Files.writeString(dir.resolve("notes.txt"), "mine");
        ExportInbox.prune(dir, ExportInbox.KEEP);
        assertEquals(ExportInbox.KEEP + 1, files(dir), "the newest exports and the player's own file");
        assertTrue(Files.exists(dir.resolve(String.format("%s20261116-1200%02d.json", ExportInbox.PREFIX,
                ExportInbox.KEEP + 4))));
        assertFalse(Files.exists(dir.resolve(ExportInbox.PREFIX + "20261116-120000.json")));
    }
}
