package za.co.neroland.neromaps.colour;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The block and biome colour table (decision 003). Built from the JSON files the {@code tools/} script writes
 * ({@code map_colours/<namespace>.json}); later files win per key, so a resource pack overrides single blocks.
 * A block missing from the table falls back to its vanilla map colour through {@link Fallback}, computed once and
 * memoised. Main (render) thread only.
 */
public final class ColourTable {

    public static final int TINT_NONE = 0;
    public static final int TINT_GRASS = 1;
    public static final int TINT_FOLIAGE = 2;
    public static final int TINT_WATER = 3;

    public static final int DEFAULT_GRASS = 0x7FB238;
    public static final int DEFAULT_FOLIAGE = 0x59AE30;
    public static final int DEFAULT_WATER = 0x3F76E4;
    /** Grey used when even the fallback has nothing. */
    public static final int UNKNOWN = 0x7F7F7F;

    /** Vanilla map colour of a block id (RGB), or -1 when unknown. */
    @FunctionalInterface
    public interface Fallback {
        int rgb(String blockId);
    }

    private static int versions;

    private final HashMap<String, Integer> blocks = new HashMap<>();
    private final HashMap<String, int[]> biomes = new HashMap<>();
    private final Fallback fallback;
    private final int version;
    private int fallbacks;

    public ColourTable(Fallback fallback) {
        this.fallback = fallback;
        this.version = ++versions;
    }

    /** A table with no entries (everything falls back). */
    public static ColourTable empty(Fallback fallback) {
        return new ColourTable(fallback);
    }

    /** Distinct per table instance; tile colour caches compare it. */
    public int version() {
        return version;
    }

    public int size() {
        return blocks.size();
    }

    /** Blocks resolved through the fallback so far (a perf counter, and the fallback test's witness). */
    public int fallbackCount() {
        return fallbacks;
    }

    /** Merges one {@code map_colours} JSON document; its keys replace earlier ones. Bad entries are skipped. */
    public void merge(JsonObject json) {
        JsonElement b = json.get("blocks");
        if (b != null && b.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : b.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                int rgb = hex(o.get("rgb"), -1);
                if (rgb < 0) {
                    continue;
                }
                blocks.put(e.getKey(), pack(rgb, tint(o.get("tint"))));
            }
        }
        JsonElement bi = json.get("biomes");
        if (bi != null && bi.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : bi.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                biomes.put(e.getKey(), new int[] {hex(o.get("grass"), DEFAULT_GRASS),
                        hex(o.get("foliage"), DEFAULT_FOLIAGE), hex(o.get("water"), DEFAULT_WATER)});
            }
        }
    }

    /** Packed {@code tint << 24 | rgb} for a block id; missing ids resolve through the fallback once. */
    public int block(String id) {
        Integer v = blocks.get(id);
        if (v != null) {
            return v;
        }
        int rgb = -1;
        try {
            rgb = fallback.rgb(id);
        } catch (RuntimeException e) {
            rgb = -1;
        }
        int packed = pack(rgb < 0 ? UNKNOWN : rgb, TINT_NONE);
        blocks.put(id, packed);
        fallbacks++;
        return packed;
    }

    /** True when the id has its own table entry (not a fallback). */
    public boolean hasEntry(String id) {
        return blocks.containsKey(id);
    }

    /** {grass, foliage, water} RGB for a biome id; unknown biomes get the neutral defaults. */
    public int[] biome(String id) {
        int[] v = biomes.get(id);
        return v != null ? v : NEUTRAL;
    }

    private static final int[] NEUTRAL = {DEFAULT_GRASS, DEFAULT_FOLIAGE, DEFAULT_WATER};

    /** Biome-style family colours ({@code map/biome_styles.json}), resolved by id and tag on the client. */
    private final HashMap<String, Integer> biomeStyle = new HashMap<>();
    private int biomeStyleDefault = 0x7A5C8A;

    public void setBiomeStyle(String biomeId, int rgb) {
        biomeStyle.put(biomeId, rgb & 0xFFFFFF);
    }

    public void setBiomeStyleDefault(int rgb) {
        biomeStyleDefault = rgb & 0xFFFFFF;
    }

    /** The flat Biome-style colour of a biome. */
    public int biomeStyle(String id) {
        Integer v = biomeStyle.get(id);
        return v != null ? v : biomeStyleDefault;
    }

    public static int rgb(int packed) {
        return packed & 0xFFFFFF;
    }

    public static int tint(int packed) {
        return packed >>> 24;
    }

    private static int pack(int rgb, int tint) {
        return (tint << 24) | (rgb & 0xFFFFFF);
    }

    private static int tint(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return TINT_NONE;
        }
        switch (e.getAsString()) {
            case "grass":
                return TINT_GRASS;
            case "foliage":
                return TINT_FOLIAGE;
            case "water":
                return TINT_WATER;
            default:
                return TINT_NONE;
        }
    }

    private static int hex(JsonElement e, int fallbackValue) {
        if (e == null || !e.isJsonPrimitive()) {
            return fallbackValue;
        }
        String s = e.getAsString();
        if (s.length() != 6) {
            return fallbackValue;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }
}
