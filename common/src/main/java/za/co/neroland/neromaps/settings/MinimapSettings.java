package za.co.neroland.neromaps.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import za.co.neroland.neromaps.colour.MapStyle;

/**
 * Every client display setting of the minimap and world map, stored client-side in
 * {@code config/neromaps-client.properties}. These are preferences, not player data, and never leave the client.
 * Server policy (decision 010) can only take things away: the effective value of a policy-gated setting is
 * {@code setting AND policy}.
 */
public final class MinimapSettings {

    public enum Shape { SQUARE, ROUND }

    public enum Anchor { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, FREE }

    public enum Border { NERO, MINIMAL, NONE }

    public static final int MIN_SIZE = 48;
    public static final int MAX_SIZE = 192;
    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 8f;

    public boolean enabled = true;
    public Shape shape = Shape.SQUARE;
    public Anchor anchor = Anchor.TOP_RIGHT;
    /** Free position, as fractions of the screen (0..1), clamped to the safe area. */
    public float freeX = 0.5f;
    public float freeY = 0.5f;
    /** Safe-area margin in GUI pixels. */
    public int margin = 4;
    /** Map size in GUI pixels (the frame adds a few). */
    public int size = 96;
    /** GUI pixels per block. */
    public float zoom = 1f;
    public float opacity = 1f;
    public Border border = Border.NERO;
    public boolean rotate;
    public MapStyle style = MapStyle.DAY_NIGHT;
    public boolean highContrast;
    public boolean grid;
    public boolean chunkBorders;
    public boolean caveMode;
    public boolean showCoords = true;
    public boolean showFacing;
    public boolean showBiome = true;
    public boolean showDimension;
    public boolean showTime;
    public boolean showLight;
    /** Chunk patches uploaded per frame (decision 004 B2: default 8, cap 16). */
    public int uploadBudget = 8;
    public int textureBudgetMb = 64;
    public int cacheMb = 256;
    public int memoryTiles = 48;

    /** Bumped on every change, so renderers can compare. */
    public int version;

    public MapStyle effectiveStyle() {
        return highContrast ? MapStyle.HIGH_CONTRAST : style;
    }

    public void changed() {
        version++;
        clamp();
    }

    public void clamp() {
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        opacity = Math.max(0.2f, Math.min(1f, opacity));
        freeX = Math.max(0f, Math.min(1f, freeX));
        freeY = Math.max(0f, Math.min(1f, freeY));
        margin = Math.max(0, Math.min(64, margin));
        uploadBudget = Math.max(1, Math.min(16, uploadBudget));
        textureBudgetMb = Math.max(16, Math.min(1024, textureBudgetMb));
        cacheMb = Math.max(0, Math.min(16384, cacheMb));
        memoryTiles = Math.max(16, Math.min(512, memoryTiles));
    }

    /** Zoom one step in (true) or out, by powers of two. */
    public void zoomStep(boolean in) {
        zoom = in ? zoom * 2f : zoom * 0.5f;
        changed();
    }

    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("shape", shape.name());
        p.setProperty("anchor", anchor.name());
        p.setProperty("freeX", Float.toString(freeX));
        p.setProperty("freeY", Float.toString(freeY));
        p.setProperty("margin", Integer.toString(margin));
        p.setProperty("size", Integer.toString(size));
        p.setProperty("zoom", Float.toString(zoom));
        p.setProperty("opacity", Float.toString(opacity));
        p.setProperty("border", border.name());
        p.setProperty("rotate", Boolean.toString(rotate));
        p.setProperty("style", style.name());
        p.setProperty("highContrast", Boolean.toString(highContrast));
        p.setProperty("grid", Boolean.toString(grid));
        p.setProperty("chunkBorders", Boolean.toString(chunkBorders));
        p.setProperty("caveMode", Boolean.toString(caveMode));
        p.setProperty("showCoords", Boolean.toString(showCoords));
        p.setProperty("showFacing", Boolean.toString(showFacing));
        p.setProperty("showBiome", Boolean.toString(showBiome));
        p.setProperty("showDimension", Boolean.toString(showDimension));
        p.setProperty("showTime", Boolean.toString(showTime));
        p.setProperty("showLight", Boolean.toString(showLight));
        p.setProperty("uploadBudget", Integer.toString(uploadBudget));
        p.setProperty("textureBudgetMb", Integer.toString(textureBudgetMb));
        p.setProperty("cacheMb", Integer.toString(cacheMb));
        p.setProperty("memoryTiles", Integer.toString(memoryTiles));
        return p;
    }

    public void fromProperties(Properties p) {
        enabled = bool(p, "enabled", enabled);
        shape = enumOf(Shape.class, p.getProperty("shape"), shape);
        anchor = enumOf(Anchor.class, p.getProperty("anchor"), anchor);
        freeX = flt(p, "freeX", freeX);
        freeY = flt(p, "freeY", freeY);
        margin = integer(p, "margin", margin);
        size = integer(p, "size", size);
        zoom = flt(p, "zoom", zoom);
        opacity = flt(p, "opacity", opacity);
        border = enumOf(Border.class, p.getProperty("border"), border);
        rotate = bool(p, "rotate", rotate);
        style = MapStyle.parse(p.getProperty("style", ""), style);
        highContrast = bool(p, "highContrast", highContrast);
        grid = bool(p, "grid", grid);
        chunkBorders = bool(p, "chunkBorders", chunkBorders);
        caveMode = bool(p, "caveMode", caveMode);
        showCoords = bool(p, "showCoords", showCoords);
        showFacing = bool(p, "showFacing", showFacing);
        showBiome = bool(p, "showBiome", showBiome);
        showDimension = bool(p, "showDimension", showDimension);
        showTime = bool(p, "showTime", showTime);
        showLight = bool(p, "showLight", showLight);
        uploadBudget = integer(p, "uploadBudget", uploadBudget);
        textureBudgetMb = integer(p, "textureBudgetMb", textureBudgetMb);
        cacheMb = integer(p, "cacheMb", cacheMb);
        memoryTiles = integer(p, "memoryTiles", memoryTiles);
        changed();
    }

    /** Loads from {@code file}; a missing or unreadable file keeps the defaults. */
    public void load(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return;
        }
        fromProperties(p);
    }

    /** Saves atomically (temp file, then move). */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            toProperties().store(w, "NeroMaps client display settings (local preferences, never sent anywhere)");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }

    private static int integer(Properties p, String k, int d) {
        try {
            String v = p.getProperty(k);
            return v == null ? d : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static float flt(Properties p, String k, float d) {
        try {
            String v = p.getProperty(k);
            float f = v == null ? d : Float.parseFloat(v.trim());
            return Float.isFinite(f) ? f : d;
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String v, E d) {
        if (v == null) {
            return d;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(v.trim())) {
                return e;
            }
        }
        return d;
    }
}
