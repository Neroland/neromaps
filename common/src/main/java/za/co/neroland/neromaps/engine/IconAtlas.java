package za.co.neroland.neromaps.engine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The shared map icon atlas ({@code assets/neromaps/map/map_icons.json}): icon id to its 16 x 16 cell
 * in {@code textures/gui/map_icons.png}. Sibling layers name an icon id and ship no art; an unknown id resolves to
 * the generic fallback. Pure (Gson only); the client loads it from the resource manager, tests from the classpath.
 */
public final class IconAtlas {

    public static final String FALLBACK = "neromaps:generic";

    private final Map<String, int[]> uv = new HashMap<>();
    private final Map<String, Integer> colour = new HashMap<>();
    private int[] fallback = {0, 0, 16, 16};
    private int size = 256;

    public static IconAtlas parse(Reader r) {
        IconAtlas a = new IconAtlas();
        JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
        if (root.has("size") && root.get("size").isJsonArray()) {
            a.size = root.getAsJsonArray("size").get(0).getAsInt();
        }
        JsonObject icons = root.getAsJsonObject("icons");
        if (icons != null) {
            for (Map.Entry<String, JsonElement> e : icons.entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                JsonArray u = o.getAsJsonArray("uv");
                if (u == null || u.size() != 4) {
                    continue;
                }
                a.uv.put(e.getKey(), new int[] {u.get(0).getAsInt(), u.get(1).getAsInt(), u.get(2).getAsInt(),
                        u.get(3).getAsInt()});
                if (o.has("colour")) {
                    try {
                        a.colour.put(e.getKey(), Integer.parseInt(o.get("colour").getAsString().substring(1), 16));
                    } catch (RuntimeException ex) {
                        // no colour
                    }
                }
            }
        }
        String fb = root.has("fallback") ? root.get("fallback").getAsString() : FALLBACK;
        int[] f = a.uv.get(fb);
        if (f != null) {
            a.fallback = f;
        }
        return a;
    }

    /** The shipped atlas from the classpath, or an empty one. */
    public static IconAtlas builtin() {
        try (InputStream in = IconAtlas.class.getResourceAsStream("/assets/neromaps/map/map_icons.json")) {
            if (in == null) {
                return new IconAtlas();
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new IconAtlas();
        }
    }

    /** The cell {u, v, w, h} of {@code id}, or the fallback's. Never null. */
    public int[] uv(String id) {
        int[] r = id == null ? null : uv.get(id);
        return r == null ? fallback : r;
    }

    public boolean has(String id) {
        return uv.containsKey(id);
    }

    /** The icon's palette colour (RGB), or {@code def}. */
    public int colour(String id, int def) {
        Integer c = colour.get(id);
        return c == null ? def : c;
    }

    public int size() {
        return size;
    }

    public int count() {
        return uv.size();
    }
}
