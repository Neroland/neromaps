package za.co.neroland.neromaps.link;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

/**
 * The colour table the app draws server tiles with (the link {@code palette} section, decision 003), built once per
 * server start off the server thread and kept as a finished JSON document with its etag. Sources, later wins:
 * every block's vanilla map colour, then {@code data/<ns>/map_colours/*.json} in id order. Holds no player data.
 */
public final class MapsPalette {

    private final JsonObject document;
    private final String etag;

    private MapsPalette(JsonObject document, String etag) {
        this.document = document;
        this.etag = etag;
    }

    public String etag() {
        return etag;
    }

    /** A copy of the full document ({@code v}, {@code version}, {@code blocks}, {@code biomes}). */
    public JsonObject document() {
        return document.deepCopy();
    }

    /** Builds the palette from a fallback map (block id → rgb) and the colour documents, in merge order. Pure. */
    public static MapsPalette build(Map<String, Integer> fallback, List<JsonObject> docs) {
        JsonObject blocks = new JsonObject();
        fallback.forEach((id, rgb) -> {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray(2);
            a.add(String.format("%06x", rgb & 0xFFFFFF));
            a.add("none");
            blocks.add(id, a);
        });
        JsonObject biomes = new JsonObject();
        int version = 0;
        for (JsonObject d : docs) {
            if (d.has("version") && d.get("version").isJsonPrimitive()) {
                try {
                    version += d.get("version").getAsInt();
                } catch (RuntimeException e) {
                    // not a number: ignored
                }
            }
            copy(d.get("blocks"), blocks);
            copy(d.get("biomes"), biomes);
        }
        JsonObject out = new JsonObject();
        out.addProperty("version", Math.max(1, version));
        out.add("blocks", blocks);
        out.add("biomes", biomes);
        String text = out.toString();
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < text.length(); i++) {
            h = (h ^ text.charAt(i)) * 0x100000001B3L;
        }
        String etag = za.co.neroland.neromaps.server.tiles.LinkTiles.hex(h);
        JsonObject doc = new JsonObject();
        doc.addProperty("v", etag);
        out.entrySet().forEach(e -> doc.add(e.getKey(), e.getValue()));
        return new MapsPalette(doc, etag);
    }

    private static void copy(JsonElement from, JsonObject into) {
        if (from == null || !from.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : from.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonObject() || e.getValue().isJsonArray()) {
                into.add(e.getKey(), e.getValue());
            }
        }
    }

    /** Loads from a running server's data packs and block registry. Call off the server thread. */
    public static MapsPalette load(MinecraftServer server) {
        Map<String, Integer> fallback = new java.util.TreeMap<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            try {
                Block b = BuiltInRegistries.BLOCK.getValue(id);
                MapColor c = b.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (c != null && c != MapColor.NONE) {
                    fallback.put(id.toString(), c.col);
                }
            } catch (RuntimeException e) {
                // a block whose map colour needs a world: left to the app's grey
            }
        }
        List<JsonObject> docs = new ArrayList<>();
        try {
            Map<Identifier, Resource> found = server.getResourceManager().listResources("map_colours",
                    id -> id.getPath().endsWith(".json"));
            List<Identifier> ids = new ArrayList<>(found.keySet());
            ids.sort((a, b) -> a.toString().compareTo(b.toString()));
            for (Identifier id : ids) {
                try (Reader r = found.get(id).openAsReader()) {
                    JsonElement e = JsonParser.parseReader(r);
                    if (e.isJsonObject()) {
                        docs.add(e.getAsJsonObject());
                    }
                } catch (Exception ex) {
                    // a malformed table is skipped
                }
            }
        } catch (RuntimeException ex) {
            // no data packs readable: vanilla colours only
        }
        return build(fallback, docs);
    }
}
