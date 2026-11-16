package za.co.neroland.neromaps.survival;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.registry.MapIds;

/**
 * The NeroQuests chapter NeroMaps ships as inert data ({@code data/neromaps/neroquests/}, "Charting the World").
 * NeroQuests' {@code SiblingPacksTest} lists its siblings by name and NeroMaps is not on that list, and NeroQuests
 * may not be edited from here, so this test replicates its checks: the Gson pack validator NeroQuests publishes for
 * sibling repos (its wiki page "Shipping a quest pack": structure, pack rules, translation keys) plus the stricter
 * checks the siblings' own pack tests make (only NeroMaps or vanilla items, prerequisites inside the pack, the chapter
 * lists exactly the pack's quests, numeric grid positions, no gate or reputation rewards).
 */
class QuestPackTest {

    static final String NAMESPACE = "neromaps";
    static final Set<String> OBJECTIVES = Set.of("collect_item", "craft_item", "kill_entity", "reach_dimension",
            "visit_structure", "gate_open", "quest_complete", "custom_event", "economy", "manual");
    static final Set<String> REWARDS = Set.of("item", "xp", "currency", "mod_hook");
    static final String KEY = "[a-z0-9_-]+(\\.[a-z0-9_-]+)+";

    @Test
    void packFollowsTheNeroQuestsFormat() throws IOException {
        Path root = RepoFiles.resources();
        Path data = root.resolve("data/" + NAMESPACE + "/neroquests");
        Map<String, JsonObject> quests = read(data.resolve("quests"));
        Map<String, JsonObject> chapters = read(data.resolve("chapters"));
        JsonObject lang = RepoFiles.lang();
        List<String> problems = new ArrayList<>();
        assertTrue(!quests.isEmpty() && chapters.size() == 1, "one chapter with quests");
        Set<String> serverOk = new HashSet<>();
        Set<String> listed = new HashSet<>();
        chapters.forEach((id, c) -> {
            key(problems, lang, id, c, "title", true);
            item(problems, id, c.get("icon").getAsString());
            for (JsonElement e : c.getAsJsonArray("quests")) {
                JsonObject entry = e.getAsJsonObject();
                String q = entry.get("quest").getAsString();
                listed.add(q);
                entry.get("x").getAsInt();
                entry.get("y").getAsInt();
                if (!quests.containsKey(q)) {
                    problems.add(id + " lists unknown/foreign quest " + q);
                }
                if (c.has("allow_server_scope") && c.get("allow_server_scope").getAsBoolean()) {
                    serverOk.add(q);
                }
            }
        });
        if (!listed.equals(quests.keySet())) {
            problems.add("the chapter must list exactly the pack's quests");
        }
        quests.forEach((id, q) -> {
            key(problems, lang, id, q, "title", true);
            key(problems, lang, id, q, "description", false);
            item(problems, id, q.get("icon").getAsString());
            boolean server = q.has("scope") && "server".equals(q.get("scope").getAsString());
            if (server && !serverOk.contains(id)) {
                problems.add(id + ": scope server needs allow_server_scope");
            }
            if (q.has("prerequisites")) {
                q.getAsJsonArray("prerequisites").forEach(p -> ref(problems, quests, id, p.getAsString()));
            }
            JsonArray objectives = q.getAsJsonArray("objectives");
            if (objectives == null || objectives.isEmpty()) {
                problems.add(id + ": no objectives");
            } else {
                for (JsonElement e : objectives) {
                    JsonObject o = e.getAsJsonObject();
                    String type = o.get("type").getAsString().replace("neroquests:", "");
                    if (!OBJECTIVES.contains(type)) {
                        problems.add(id + ": unknown objective " + type);
                    }
                    if (type.equals("craft_item") || type.equals("collect_item")) {
                        item(problems, id, o.get("item").getAsString());
                        if (o.get("count").getAsInt() < 1) {
                            problems.add(id + ": count must be at least 1");
                        }
                    }
                    if (type.equals("quest_complete")) {
                        ref(problems, quests, id, o.get("quest").getAsString());
                    }
                    if (type.equals("custom_event") && !server && !(o.has("audience")
                            && "everyone".equals(o.get("audience").getAsString()))) {
                        problems.add(id + ": custom_event on a player quest needs \"audience\": \"everyone\"");
                    }
                }
            }
            if (q.has("rewards")) {
                for (JsonElement e : q.getAsJsonArray("rewards")) {
                    JsonObject r = e.getAsJsonObject();
                    String type = r.get("type").getAsString().replace("neroquests:", "");
                    if (!REWARDS.contains(type)) {
                        problems.add(id + ": reward " + type + " not allowed in a pack");
                    }
                    if (type.equals("mod_hook") && !r.get("gate").getAsString().startsWith(NAMESPACE + ":hook/")) {
                        problems.add(id + ": mod_hook gate must be " + NAMESPACE + ":hook/<id>");
                    }
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static void item(List<String> problems, String id, String item) {
        int c = item.indexOf(':');
        String ns = c < 0 ? "minecraft" : item.substring(0, c);
        String path = c < 0 ? item : item.substring(c + 1);
        if (!(ns.equals("minecraft") || ns.equals(NAMESPACE) && MapIds.ITEMS.contains(path))) {
            problems.add(id + ": item " + item + " is neither NeroMaps' nor vanilla");
        }
    }

    static void ref(List<String> problems, Map<String, JsonObject> quests, String id, String target) {
        if (target.startsWith("neroquests:")) {
            return;
        }
        if (!target.startsWith(NAMESPACE + ":")) {
            problems.add(id + ": depends on another pack's quest " + target);
        } else if (!quests.containsKey(target)) {
            problems.add(id + ": unknown quest " + target);
        }
    }

    static void key(List<String> problems, JsonObject lang, String id, JsonObject o, String field, boolean required) {
        if (!o.has(field)) {
            if (required) {
                problems.add(id + ": missing " + field);
            }
            return;
        }
        String key = o.get(field).getAsString();
        if (!key.matches(KEY)) {
            problems.add(id + ": " + field + " is not a translation key");
        } else if (!lang.has(key)) {
            problems.add(id + ": en_us.json has no " + key);
        }
    }

    static Map<String, JsonObject> read(Path dir) throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                out.put(NAMESPACE + ":" + rel.substring(0, rel.length() - 5), RepoFiles.json(f));
            }
        }
        return out;
    }
}
