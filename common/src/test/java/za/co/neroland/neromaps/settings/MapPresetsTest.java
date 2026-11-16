package za.co.neroland.neromaps.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.colour.MapStyle;

/** Built-in presets, cycling, user presets, and JSON export / import with validation. */
class MapPresetsTest {

    @Test
    void builtInsApplyTheirLook() {
        MinimapSettings s = new MinimapSettings();
        assertTrue(MapPresets.applyBuiltIn("compact", s));
        assertEquals(MinimapSettings.Shape.ROUND, s.shape);
        assertEquals(64, s.size);
        assertTrue(s.rotate);
        assertTrue(MapPresets.applyBuiltIn("builder", s));
        assertEquals(MapStyle.FLAT, s.style);
        assertTrue(s.grid && s.chunkBorders);
        assertTrue(MapPresets.applyBuiltIn("cinematic", s));
        assertEquals(0.7f, s.opacity, 1e-6);
        assertFalse(s.beams);
        assertTrue(MapPresets.applyBuiltIn("off", s));
        assertFalse(s.enabled);
        assertTrue(MapPresets.applyBuiltIn("explorer", s));
        assertTrue(s.enabled);
        assertFalse(MapPresets.applyBuiltIn("nope", s));
        assertEquals(5, MapPresets.BUILT_IN.size());
    }

    @Test
    void cycleWrapsThroughBuiltInsAndUserPresets() {
        MapPresets p = new MapPresets();
        MinimapSettings s = new MinimapSettings();
        assertEquals("builder", p.next("explorer"));
        assertEquals("compact", p.next("off"));
        assertTrue(p.saveAs("My Cave Look!", s));
        assertEquals("my cave look", s.preset, "names are cleaned");
        assertEquals("my cave look", p.next("off"));
        assertEquals("compact", p.next("my cave look"));
        assertFalse(p.saveAs("explorer", s), "a built-in name cannot be taken");
        assertTrue(p.rename("my cave look", "caves"));
        assertTrue(p.names().contains("caves"));
        assertTrue(p.delete("caves"));
    }

    @Test
    void exportImportRoundTripsDisplaySettingsOnly() {
        MinimapSettings a = new MinimapSettings();
        MapPresets.applyBuiltIn("builder", a);
        a.uploadBudget = 3; // a budget, never part of a preset
        a.markerPalette = MinimapSettings.MarkerPalette.TOL_BRIGHT;
        String json = MapPresets.export(a);
        assertFalse(json.contains("uploadBudget"));
        MinimapSettings b = new MinimapSettings();
        assertNull(MapPresets.importInto(json, b));
        assertEquals(a.size, b.size);
        assertEquals(a.style, b.style);
        assertEquals(MinimapSettings.MarkerPalette.TOL_BRIGHT, b.markerPalette);
        assertEquals(8, b.uploadBudget);
    }

    @Test
    void importRefusesJunkAndClampsValues() {
        MinimapSettings s = new MinimapSettings();
        assertNotNull(MapPresets.importInto("", s));
        assertNotNull(MapPresets.importInto("{not json", s));
        assertNotNull(MapPresets.importInto("[1,2]", s));
        assertNotNull(MapPresets.importInto("{\"size\":\"96\"}", s), "needs the preset marker");
        assertNotNull(MapPresets.importInto("{\"neromaps_preset\":1,\"cacheMb\":\"99999\"}", s), "budgets refused");
        assertNotNull(MapPresets.importInto("{\"neromaps_preset\":1,\"size\":{\"x\":1}}", s));
        assertNotNull(MapPresets.importInto("{\"neromaps_preset\":1,\"x\":\"" + "a".repeat(9000) + "\"}", s));
        assertNull(MapPresets.importInto("{\"neromaps_preset\":1,\"size\":\"100000\",\"opacity\":\"-3\"}", s));
        assertEquals(MinimapSettings.MAX_SIZE, s.size);
        assertTrue(s.opacity > 0f);
    }

    @Test
    void userPresetsPersist(@TempDir Path dir) throws IOException {
        MapPresets p = new MapPresets();
        MinimapSettings s = new MinimapSettings();
        s.size = 160;
        assertTrue(p.saveAs("big", s));
        p.save(dir.resolve("presets.json"));
        MapPresets q = new MapPresets();
        q.load(dir.resolve("presets.json"));
        MinimapSettings t = new MinimapSettings();
        assertTrue(q.apply("big", t));
        assertEquals(160, t.size);
    }

    @Test
    void markerPalettesMapRolesNotJustColours() {
        for (int i = 0; i < MapPalette.MARKERS.length; i++) {
            assertEquals(MapPalette.MARKERS[i],
                    MapPalette.marker(MapPalette.MARKERS[i], MinimapSettings.MarkerPalette.OKABE_ITO));
            assertEquals(MapPalette.MARKERS_TOL[i],
                    MapPalette.marker(MapPalette.MARKERS[i], MinimapSettings.MarkerPalette.TOL_BRIGHT));
        }
        assertEquals(0xFF123456, MapPalette.marker(0xFF123456, MinimapSettings.MarkerPalette.TOL_BRIGHT));
    }
}
