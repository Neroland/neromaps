package za.co.neroland.neromaps.survival;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.registry.MapIds;

/**
 * Asset checker: every registered block has a blockstate whose every model exists, every registered item has
 * an item model (and, for 1.21.4+, an item model definition), every model's parent and textures exist, every block
 * and item has an English name, and every sound event is registered, has its sound files and has a subtitle. Recipes
 * produce only registered items. Reads the shared resources; no game bootstrap.
 */
class AssetCheckerTest {

    private static final Path ASSETS = RepoFiles.resources().resolve("assets/neromaps");

    private static boolean exists(String rel) {
        return Files.isRegularFile(ASSETS.resolve(rel));
    }

    /** {@code neromaps:block/x} (or {@code minecraft:...}) to its file under assets, or null for vanilla. */
    private static String own(String id, String folder, String ext) {
        if (id.startsWith("#")) {
            return null;
        }
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        if (!ns.equals("neromaps")) {
            return null;
        }
        return folder + "/" + id.substring(c + 1) + ext;
    }

    private static void model(String id, List<String> problems, Set<String> seen) {
        String rel = own(id, "models", ".json");
        if (rel == null || !seen.add(rel)) {
            return;
        }
        if (!exists(rel)) {
            problems.add("missing model " + rel);
            return;
        }
        JsonObject m = RepoFiles.json(ASSETS.resolve(rel));
        if (m.has("parent")) {
            model(m.get("parent").getAsString(), problems, seen);
        }
        if (m.has("textures")) {
            for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
                String v = t.getValue().getAsString();
                String tex = own(v, "textures", ".png");
                if (tex != null && !exists(tex)) {
                    problems.add(rel + ": missing texture " + tex);
                }
            }
        }
    }

    private static void collectModels(JsonElement e, Set<String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("model") && o.get("model").isJsonPrimitive()) {
                out.add(o.get("model").getAsString());
            }
            for (Map.Entry<String, JsonElement> k : o.entrySet()) {
                collectModels(k.getValue(), out);
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                collectModels(x, out);
            }
        }
    }

    @Test
    void everyBlockAndItemHasModelTextureAndName() {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonObject lang = RepoFiles.lang();
        for (String b : MapIds.BLOCKS) {
            String bs = "blockstates/" + b + ".json";
            if (!exists(bs)) {
                problems.add("missing " + bs);
                continue;
            }
            Set<String> models = new HashSet<>();
            collectModels(RepoFiles.json(ASSETS.resolve(bs)), models);
            if (models.isEmpty()) {
                problems.add(bs + " names no model");
            }
            models.forEach(m -> model(m, problems, seen));
            if (!lang.has("block.neromaps." + b)) {
                problems.add("no lang block.neromaps." + b);
            }
        }
        for (String i : MapIds.ITEMS) {
            model("neromaps:item/" + i, problems, seen);
            if (!exists("items/" + i + ".json")) {
                problems.add("missing items/" + i + ".json (1.21.4+ item model definition)");
            } else {
                Set<String> models = new HashSet<>();
                collectModels(RepoFiles.json(ASSETS.resolve("items/" + i + ".json")), models);
                models.forEach(m -> model(m, problems, seen));
            }
            if (!lang.has("item.neromaps." + i) && !lang.has("block.neromaps." + i)) {
                problems.add("no lang name for " + i);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everySoundIsRegisteredHasFilesAndASubtitle() {
        List<String> problems = new ArrayList<>();
        JsonObject sounds = RepoFiles.json(ASSETS.resolve("sounds.json"));
        JsonObject lang = RepoFiles.lang();
        if (!sounds.keySet().equals(new HashSet<>(MapIds.SOUNDS))) {
            problems.add("sounds.json events " + sounds.keySet() + " differ from the registered " + MapIds.SOUNDS);
        }
        for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
            JsonObject ev = e.getValue().getAsJsonObject();
            if (!ev.has("subtitle") || !lang.has(ev.get("subtitle").getAsString())) {
                problems.add(e.getKey() + ": no subtitle");
            }
            for (JsonElement s : ev.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                String rel = own(name, "sounds", ".ogg");
                if (rel != null && !exists(rel)) {
                    problems.add(e.getKey() + ": missing " + rel);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void recipesProduceRegisteredItems() throws java.io.IOException {
        List<String> problems = new ArrayList<>();
        Path dir = RepoFiles.resources().resolve("data/neromaps/recipe");
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = RepoFiles.json(f).getAsJsonObject("result").get("id").getAsString();
                if (!MapIds.ITEMS.contains(id.substring(id.indexOf(':') + 1)) || !id.startsWith("neromaps:")) {
                    problems.add(f.getFileName() + " makes " + id);
                }
                String name = f.getFileName().toString().replace(".json", "");
                if (!Files.isRegularFile(RepoFiles.resources()
                        .resolve("data/neromaps/advancement/recipes/misc/" + name + ".json"))) {
                    problems.add(name + ": no recipe unlock advancement");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
