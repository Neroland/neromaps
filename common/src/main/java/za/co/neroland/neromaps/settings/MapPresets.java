package za.co.neroland.neromaps.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import za.co.neroland.neromaps.colour.MapStyle;

/**
 * Named display presets: five built in — Compact, Explorer (the default look), Builder,
 * Cinematic and Off — plus the player's own, saved in {@code config/neromaps-presets.json}. A preset holds client
 * display settings only, never server policy (policy still wins at draw time) and never performance budgets. Presets
 * export and import as a small JSON object of setting keys; import accepts known keys only and clamps every value.
 */
public final class MapPresets {

    public static final List<String> BUILT_IN = List.of("compact", "explorer", "builder", "cinematic", "off");
    public static final int MAX_USER = 16;
    public static final int MAX_NAME = 24;
    /** Export/import text larger than this is refused (a preset is a few hundred bytes). */
    public static final int MAX_JSON = 8192;

    /** The display keys a preset carries: everything in {@link MinimapSettings#toProperties()} but the budgets. */
    public static final Set<String> KEYS = Set.of("enabled", "shape", "anchor", "freeX", "freeY", "margin", "size",
            "zoom", "opacity", "border", "rotate", "style", "highContrast", "grid", "chunkBorders", "caveMode",
            "showCoords", "showFacing", "showBiome", "showDimension", "showTime", "showLight", "beams",
            "distanceLabels", "beamRange", "waypointsOnMinimap", "deathPointsShown", "reduceMotion", "keyline",
            "solidPlates", "markerPalette", "nightDarkening", "layersOffWorld", "layersOnWorld", "layersOnMinimap",
            "layersOffMinimap");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, Properties> user = new LinkedHashMap<>();

    /** Applies built-in preset {@code name} to {@code s}; false for an unknown name. */
    public static boolean applyBuiltIn(String name, MinimapSettings s) {
        switch (name) {
            case "compact" -> {
                s.enabled = true;
                s.shape = MinimapSettings.Shape.ROUND;
                s.size = 64;
                s.rotate = true;
                s.style = MapStyle.DAY_NIGHT;
                s.border = MinimapSettings.Border.NERO;
                s.opacity = 1f;
                readouts(s, false, false, false);
                s.waypointsOnMinimap = true;
                s.deathPointsShown = true;
                s.grid = false;
                s.chunkBorders = false;
            }
            case "explorer" -> {
                s.enabled = true;
                s.shape = MinimapSettings.Shape.SQUARE;
                s.size = 96;
                s.rotate = false;
                s.style = MapStyle.DAY_NIGHT;
                s.border = MinimapSettings.Border.NERO;
                s.opacity = 1f;
                readouts(s, true, false, true);
                s.showTime = true;
                s.waypointsOnMinimap = true;
                s.deathPointsShown = true;
                s.beams = true;
                s.grid = false;
                s.chunkBorders = false;
                s.nightDarkening = true;
            }
            case "builder" -> {
                s.enabled = true;
                s.shape = MinimapSettings.Shape.SQUARE;
                s.size = 128;
                s.rotate = false;
                s.style = MapStyle.FLAT;
                s.border = MinimapSettings.Border.NERO;
                s.opacity = 1f;
                readouts(s, true, true, false);
                s.showLight = true;
                s.grid = true;
                s.chunkBorders = true;
                s.nightDarkening = false;
                s.waypointsOnMinimap = true;
            }
            case "cinematic" -> {
                s.enabled = true;
                s.shape = MinimapSettings.Shape.ROUND;
                s.size = 96;
                s.rotate = true;
                s.style = MapStyle.DAY_NIGHT;
                s.border = MinimapSettings.Border.MINIMAL;
                s.opacity = 0.7f;
                readouts(s, false, false, false);
                s.beams = false;
                s.distanceLabels = false;
                s.deathPointsShown = false;
                s.waypointsOnMinimap = true;
                s.grid = false;
                s.chunkBorders = false;
            }
            case "off" -> s.enabled = false;
            default -> {
                return false;
            }
        }
        s.preset = name;
        s.changed();
        return true;
    }

    private static void readouts(MinimapSettings s, boolean coords, boolean facing, boolean biome) {
        s.showCoords = coords;
        s.showFacing = facing;
        s.showBiome = biome;
        s.showDimension = false;
        s.showTime = false;
        s.showLight = false;
    }

    /** Built-ins then user presets, in cycle order. */
    public List<String> names() {
        List<String> out = new ArrayList<>(BUILT_IN);
        out.addAll(user.keySet());
        return out;
    }

    public boolean isBuiltIn(String name) {
        return BUILT_IN.contains(name);
    }

    /** Applies any preset by name; false when there is none. */
    public boolean apply(String name, MinimapSettings s) {
        if (applyBuiltIn(name, s)) {
            return true;
        }
        Properties p = user.get(name);
        if (p == null) {
            return false;
        }
        s.fromProperties(p);
        s.preset = name;
        s.changed();
        return true;
    }

    /** The preset after {@code current} in {@link #names()}, wrapping (the cycle-preset key). */
    public String next(String current) {
        List<String> all = names();
        int i = all.indexOf(current);
        return all.get((i + 1) % all.size());
    }

    /** Saves the current settings as a user preset; false when the name is invalid, built in, or the list is full. */
    public boolean saveAs(String rawName, MinimapSettings s) {
        String name = cleanName(rawName);
        if (name.isEmpty() || isBuiltIn(name) || !user.containsKey(name) && user.size() >= MAX_USER) {
            return false;
        }
        user.put(name, displayOnly(s.toProperties()));
        s.preset = name;
        return true;
    }

    public boolean delete(String name) {
        return user.remove(name) != null;
    }

    public boolean rename(String from, String rawTo) {
        String to = cleanName(rawTo);
        if (to.isEmpty() || isBuiltIn(to) || user.containsKey(to) || !user.containsKey(from)) {
            return false;
        }
        Map<String, Properties> copy = new LinkedHashMap<>();
        user.forEach((k, v) -> copy.put(k.equals(from) ? to : k, v));
        user.clear();
        user.putAll(copy);
        return true;
    }

    static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        raw.trim().toLowerCase(Locale.ROOT).codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == ' ' || cp == '-' || cp == '_') {
                b.appendCodePoint(cp);
            }
        });
        String s = b.toString().trim();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    private static Properties displayOnly(Properties all) {
        Properties p = new Properties();
        for (String k : all.stringPropertyNames()) {
            if (KEYS.contains(k)) {
                p.setProperty(k, all.getProperty(k));
            }
        }
        return p;
    }

    /** The current display settings as preset JSON text (for the clipboard). */
    public static String export(MinimapSettings s) {
        JsonObject o = new JsonObject();
        o.addProperty("neromaps_preset", 1);
        Properties p = displayOnly(s.toProperties());
        for (String k : new java.util.TreeSet<>(p.stringPropertyNames())) {
            o.addProperty(k, p.getProperty(k));
        }
        return GSON.toJson(o);
    }

    /**
     * Parses preset JSON text into {@code into} (only known keys; every value is clamped by
     * {@link MinimapSettings#fromProperties}). Returns null on success, else a short reason for the player.
     */
    public static String importInto(String text, MinimapSettings into) {
        if (text == null || text.isBlank()) {
            return "empty";
        }
        if (text.length() > MAX_JSON) {
            return "too long";
        }
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(text);
            if (!e.isJsonObject()) {
                return "not a JSON object";
            }
            o = e.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return "not valid JSON";
        }
        if (!o.has("neromaps_preset")) {
            return "not a NeroMaps preset";
        }
        Properties p = new Properties();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().equals("neromaps_preset")) {
                continue;
            }
            if (!KEYS.contains(e.getKey())) {
                return "unknown setting " + e.getKey();
            }
            if (!e.getValue().isJsonPrimitive()) {
                return "bad value for " + e.getKey();
            }
            String v = e.getValue().getAsString();
            if (v.length() > 2048) {
                return "bad value for " + e.getKey();
            }
            p.setProperty(e.getKey(), v);
        }
        into.fromProperties(p);
        into.preset = "";
        return null;
    }

    // --- persistence -------------------------------------------------------------------------------------------

    public void load(Path file) {
        user.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(r);
            if (!e.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
                String name = cleanName(entry.getKey());
                if (name.isEmpty() || isBuiltIn(name) || !entry.getValue().isJsonObject() || user.size() >= MAX_USER) {
                    continue;
                }
                Properties p = new Properties();
                for (Map.Entry<String, JsonElement> kv : entry.getValue().getAsJsonObject().entrySet()) {
                    if (KEYS.contains(kv.getKey()) && kv.getValue().isJsonPrimitive()) {
                        p.setProperty(kv.getKey(), kv.getValue().getAsString());
                    }
                }
                user.put(name, p);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // an unreadable file keeps no user presets; the built-ins always exist
        }
    }

    public void save(Path file) throws IOException {
        JsonObject root = new JsonObject();
        user.forEach((name, p) -> {
            JsonObject o = new JsonObject();
            for (String k : new java.util.TreeSet<>(p.stringPropertyNames())) {
                o.addProperty(k, p.getProperty(k));
            }
            root.add(name, o);
        });
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
