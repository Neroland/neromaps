package za.co.neroland.neromaps.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.registry.MapIds;

/**
 * Breaking any NeroMaps block returns everything inside it. Every block's loot table drops the block itself
 * (the signpost one item per plate, matching its three plate states); no block entity holds an item (none has an
 * item field or container), so there is nothing else inside to lose; the Cartography Desk holds nothing at all — its
 * slots belong to the open menu, which hands them back when it closes.
 */
class BlockDropsTest {

    @Test
    void everyBlockDropsItself() {
        for (String b : MapIds.BLOCKS) {
            Path p = RepoFiles.resources().resolve("data/neromaps/loot_table/blocks/" + b + ".json");
            assertTrue(Files.isRegularFile(p), "loot table for " + b);
            JsonObject t = RepoFiles.json(p);
            assertEquals("minecraft:block", t.get("type").getAsString());
            JsonArray pools = t.getAsJsonArray("pools");
            assertEquals(1, pools.size(), b);
            JsonObject entry = pools.get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("neromaps:" + b, entry.get("name").getAsString(), b + " drops itself");
        }
    }

    @Test
    void signpostReturnsEveryPlate() {
        JsonObject t = RepoFiles.json(RepoFiles.resources().resolve("data/neromaps/loot_table/blocks/signpost.json"));
        JsonObject entry = t.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject();
        Set<Integer> counts = new HashSet<>();
        for (JsonElement f : entry.getAsJsonArray("functions")) {
            JsonObject fn = f.getAsJsonObject();
            int count = fn.get("count").getAsInt();
            String plates = fn.getAsJsonArray("conditions").get(0).getAsJsonObject().getAsJsonObject("properties")
                    .get("plates").getAsString();
            assertEquals(Integer.toString(count), plates, "one signpost per plate");
            counts.add(count);
        }
        assertEquals(Set.of(2, 3), counts, "plates 2 and 3 return 2 and 3 signposts (plates 1 returns 1)");
    }

    @Test
    void blockEntitiesHoldNoItems() throws IOException {
        try (Stream<Path> files = Files.walk(RepoFiles.java().resolve("block"))) {
            for (Path f : files.filter(p -> p.toString().endsWith("BlockEntity.java")).toList()) {
                String code = IdentityFreeTest.stripComments(RepoFiles.read(f));
                assertFalse(code.matches("(?s).*private\\s+(final\\s+)?(ItemStack|NonNullList|SimpleContainer|Container)\\b.*"),
                        f.getFileName() + " holds items");
            }
        }
    }

    @Test
    void deskHoldsNothingAndTheMenuReturnsItsSlots() {
        String desk = RepoFiles.read(RepoFiles.java().resolve("block/CartographyDeskBlock.java"));
        assertFalse(desk.contains("implements EntityBlock"), "the desk has no block entity, so no inventory");
        String menu = IdentityFreeTest.stripComments(RepoFiles.read(RepoFiles.java().resolve("menu/CartographyDeskMenu.java")));
        assertTrue(menu.contains("clearContainer(player, input)"), "closing the menu returns its inputs");
        assertTrue(menu.contains("result.removeItemNoUpdate(0)"), "the preview output is never duplicated");
    }
}
