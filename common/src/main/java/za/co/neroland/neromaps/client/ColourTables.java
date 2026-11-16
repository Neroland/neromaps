package za.co.neroland.neromaps.client;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.colour.ColourTable;

/**
 * Loads the colour table from {@code assets/<ns>/map_colours/*.json} (every pack, bottom to top, later keys win)
 * and the biome-style families from {@code assets/neromaps/map/biome_styles.json}, again on every resource reload,
 * so a resource pack can recolour single blocks. Blocks missing from the table use their vanilla map colour.
 * Client main thread only.
 */
public final class ColourTables implements ResourceManagerReloadListener {

    public static final ColourTables INSTANCE = new ColourTables();
    private static ColourTable current = ColourTable.empty(ColourTables::vanillaMapColour);
    private static final HashMap<Holder<Biome>, Boolean> RESOLVED = new HashMap<>();
    private static final java.util.ArrayList<Object[]> TAGS = new java.util.ArrayList<>();
    private static final HashMap<String, Integer> FAMILIES = new HashMap<>();
    private static int defaultFamily = 0x7A5C8A;

    private ColourTables() {
    }

    public static ColourTable current() {
        return current;
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        reload(rm);
    }

    /** Rebuilds the table from the resource manager. */
    public static void reload(ResourceManager rm) {
        ColourTable t = new ColourTable(ColourTables::vanillaMapColour);
        int files = 0;
        try {
            Map<Identifier, List<Resource>> stacks =
                    rm.listResourceStacks("map_colours", id -> id.getPath().endsWith(".json"));
            // vanilla first, then everything else, so Nero namespaces can refine shared ids
            java.util.ArrayList<Identifier> ids = new java.util.ArrayList<>(stacks.keySet());
            ids.sort((a, b) -> a.toString().compareTo(b.toString()));
            for (Identifier id : ids) {
                for (Resource r : stacks.get(id)) {
                    try (Reader reader = r.openAsReader()) {
                        JsonElement e = JsonParser.parseReader(reader);
                        if (e.isJsonObject()) {
                            t.merge(e.getAsJsonObject());
                            files++;
                        }
                    } catch (Exception ex) {
                        NeroMapsCommon.LOGGER.debug("[NeroMaps] skipped a malformed colour table");
                    }
                }
            }
        } catch (RuntimeException ex) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] could not list colour tables; using vanilla map colours");
        }
        loadBiomeStyles(rm, t);
        current = t;
        NeroMapsCommon.LOGGER.debug("[NeroMaps] colour table: {} blocks from {} files", t.size(), files);
    }

    private static void loadBiomeStyles(ResourceManager rm, ColourTable t) {
        FAMILIES.clear();
        TAGS.clear();
        RESOLVED.clear();
        Optional<Resource> res = rm.getResource(Identifier.fromNamespaceAndPath("neromaps", "map/biome_styles.json"));
        if (res.isEmpty()) {
            return;
        }
        try (Reader reader = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("families").entrySet()) {
                FAMILIES.put(e.getKey(), hex(e.getValue().getAsString()));
            }
            defaultFamily = FAMILIES.getOrDefault("exotic", defaultFamily);
            t.setBiomeStyleDefault(defaultFamily);
            if (o.has("ids")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ids").entrySet()) {
                    Integer c = FAMILIES.get(e.getValue().getAsString());
                    if (c != null) {
                        t.setBiomeStyle(e.getKey(), c);
                    }
                }
            }
            if (o.has("tags")) {
                for (JsonElement e : o.getAsJsonArray("tags")) {
                    String tag = e.getAsJsonArray().get(0).getAsString();
                    Integer c = FAMILIES.get(e.getAsJsonArray().get(1).getAsString());
                    if (c != null && tag.startsWith("#")) {
                        TAGS.add(new Object[] {TagKey.create(Registries.BIOME, Identifier.parse(tag.substring(1))), c});
                    }
                }
            }
        } catch (Exception ex) {
            NeroMapsCommon.LOGGER.debug("[NeroMaps] skipped malformed biome styles");
        }
    }

    /** Gives a biome first seen this session its Biome-style family through its tags (main thread). */
    @SuppressWarnings("unchecked")
    static void resolveBiome(Holder<Biome> h, String id) {
        if (RESOLVED.putIfAbsent(h, Boolean.TRUE) != null) {
            return;
        }
        ColourTable t = current;
        for (Object[] tag : TAGS) {
            if (h.is((TagKey<Biome>) tag[0])) {
                t.setBiomeStyle(id, (Integer) tag[1]);
                return;
            }
        }
    }

    /** The vanilla map colour of a block id (the colour table's fallback), or -1. */
    static int vanillaMapColour(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) {
            return -1;
        }
        Block b = BuiltInRegistries.BLOCK.getValue(rl);
        MapColor c = b.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return c == null || c == MapColor.NONE ? -1 : c.col;
    }

    private static int hex(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        return Integer.parseInt(h, 16);
    }
}
