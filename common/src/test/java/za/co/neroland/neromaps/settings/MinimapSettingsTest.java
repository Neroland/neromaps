package za.co.neroland.neromaps.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Defaults on a fresh install, and the one-time migration of older settings files. */
class MinimapSettingsTest {

    @Test
    void freshDefaultsShowTheMinimapAndTheTime() {
        MinimapSettings s = new MinimapSettings();
        assertTrue(s.enabled);
        assertTrue(s.showTime);
        assertEquals("explorer", s.preset);
        MinimapSettings explorer = new MinimapSettings();
        MapPresets.applyBuiltIn("explorer", explorer);
        assertTrue(explorer.showTime, "the default look carries the time readout");
    }

    @Test
    void anUntouchedOldFileGetsTheNewDefaults(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("neromaps-client.properties");
        Files.writeString(f, "enabled=false\nshowTime=false\npreset=explorer\n", StandardCharsets.UTF_8);
        MinimapSettings s = new MinimapSettings();
        s.load(f);
        assertTrue(s.enabled);
        assertTrue(s.showTime);
    }

    @Test
    void aCustomisedOldFileKeepsThePlayersChoices(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("neromaps-client.properties");
        Files.writeString(f, "enabled=false\nshowTime=false\npreset=\n", StandardCharsets.UTF_8);
        MinimapSettings s = new MinimapSettings();
        s.load(f);
        assertFalse(s.enabled);
        assertFalse(s.showTime);
    }

    @Test
    void aCurrentFileIsNeverMigrated(@TempDir Path dir) throws IOException {
        MinimapSettings s = new MinimapSettings();
        s.enabled = false;
        s.showTime = false;
        Path f = dir.resolve("neromaps-client.properties");
        s.save(f);
        MinimapSettings back = new MinimapSettings();
        back.load(f);
        assertFalse(back.enabled, "hidden with the toggle key after updating: stays hidden");
        assertFalse(back.showTime);
    }
}
