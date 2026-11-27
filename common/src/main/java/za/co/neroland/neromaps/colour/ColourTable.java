package za.co.neroland.neromaps.colour;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

/**
 * The block and biome colour table. Built from the JSON files the {@code tools/} script writes
 * ({@code map_colours/<namespace>.json}); later files win per key, so a resource pack overrides single blocks.
 *
 * <p>On the client a {@link Sampler} reads each block's colour from the textures the game actually renders (the
 * top face of its model, through the active resource packs), so the map matches what the player sees for every
 * block, modded ones included. Resolution order per block id, computed once and memoised: an explicit override
 * ({@link #mergeOverride}), then the sampled texture colour, then the bundled table entry, then the vanilla map
 * colour through {@link Fallback}. Biome grass, foliage and water colours likewise come from the client's own
 * biomes when known ({@link #setBiomeColours}), with the bundled table as the fallback. Main (render) thread only.
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

    /**
     * The colour of a block as the client renders it: packed {@code tint << 24 | rgb}, or -1 when it cannot be read
     * (no model, missing texture, not on the client). {@code tableTint} is the bundled table's tint class for the id,
     * or -1 when the table has no entry; the sampler uses it to tell a biome tint from a constant one.
     */
    @FunctionalInterface
    public interface Sampler {
        int sample(String blockId, int tableTint);
    }

    private static int versions;

    /** Bundled (generated) entries: the fallback below the sampled texture colour. */
    private final HashMap<String, Integer> blocks = new HashMap<>();
    /** Explicit overrides (a resource pack or another mod's {@code map_colours}): always win. */
    private final HashMap<String, Integer> overrides = new HashMap<>();
    /** Every id resolved so far (memoised). */
    private final HashMap<String, Integer> resolved = new HashMap<>();
    private final HashMap<String, int[]> biomes = new HashMap<>();
    /** Biome colours read from the client's own biomes; win over {@link #biomes}. */
    private final HashMap<String, int[]> liveBiomes = new HashMap<>();
    private final Fallback fallback;
    private final @Nullable Sampler sampler;
    private int version;
    private int fallbacks;

    public ColourTable(Fallback fallback) {
        this(fallback, null);
    }

    public ColourTable(Fallback fallback, @Nullable Sampler sampler) {
        this.fallback = fallback;
        this.sampler = sampler;
        this.version = ++versions;
    }

    /** A table with no entries (everything falls back). */
    public static ColourTable empty(Fallback fallback) {
        return new ColourTable(fallback);
    }

    /** Distinct per table instance and bumped when a live biome colour changes; tile colour caches compare it. */
    public int version() {
        return version;
    }

    public int size() {
        int n = blocks.size();
        for (String k : overrides.keySet()) {
            if (!blocks.containsKey(k)) {
                n++;
            }
        }
        return n;
    }

    /** Blocks resolved through the fallback so far (a perf counter, and the fallback test's witness). */
    public int fallbackCount() {
        return fallbacks;
    }

    /** Merges one bundled {@code map_colours} JSON document; its keys replace earlier ones. Bad entries are skipped. */
    public void merge(JsonObject json) {
        merge(json, blocks);
    }

    /**
     * Merges one {@code map_colours} document as explicit overrides: these win over the sampled texture colour, so
     * a resource pack or a mod can still recolour single blocks on purpose.
     */
    public void mergeOverride(JsonObject json) {
        merge(json, overrides);
    }

    private void merge(JsonObject json, HashMap<String, Integer> into) {
        resolved.clear();
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
                into.put(e.getKey(), pack(rgb, tint(o.get("tint"))));
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

    /** Packed {@code tint << 24 | rgb} for a block id, resolved once (see the class comment for the order). */
    public int block(String id) {
        Integer v = resolved.get(id);
        if (v != null) {
            return v;
        }
        int packed = resolve(id);
        resolved.put(id, packed);
        return packed;
    }

    private int resolve(String id) {
        Integer o = overrides.get(id);
        if (o != null) {
            return o;
        }
        Integer b = blocks.get(id);
        Sampler s = sampler;
        if (s != null) {
            int sampled;
            try {
                sampled = s.sample(id, b == null ? -1 : tint(b.intValue()));
            } catch (RuntimeException | LinkageError e) {
                sampled = -1;
            }
            if (sampled >= 0) {
                return sampled;
            }
        }
        if (b != null) {
            return b;
        }
        int rgb;
        try {
            rgb = fallback.rgb(id);
        } catch (RuntimeException e) {
            rgb = -1;
        }
        fallbacks++;
        return pack(rgb < 0 ? UNKNOWN : rgb, TINT_NONE);
    }

    /** True when the id has its own table entry (not a fallback). */
    public boolean hasEntry(String id) {
        return blocks.containsKey(id) || overrides.containsKey(id);
    }

    /** {grass, foliage, water} RGB for a biome id: the client's own colours, else the table, else neutral. */
    public int[] biome(String id) {
        int[] v = liveBiomes.get(id);
        if (v != null) {
            return v;
        }
        v = biomes.get(id);
        return v != null ? v : NEUTRAL;
    }

    /**
     * Records the colours the client renders a biome with (grass, foliage, water RGB). When they differ from what
     * was in use the table's version moves on, so tiles already drawn with the old colours are redrawn.
     */
    public void setBiomeColours(String id, int grass, int foliage, int water) {
        int[] v = {grass & 0xFFFFFF, foliage & 0xFFFFFF, water & 0xFFFFFF};
        int[] old = liveBiomes.put(id, v);
        if (old == null ? !java.util.Arrays.equals(v, biome0(id)) : !java.util.Arrays.equals(v, old)) {
            version = ++versions;
        }
    }

    private int[] biome0(String id) {
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
