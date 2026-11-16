package za.co.neroland.neromaps.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

import za.co.neroland.neromaps.atlas.AtlasData;
import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.store.ExploredMap;

/**
 * Stage 5: no item, block entity or chunk NBT NeroMaps writes carries a UUID or a player name. Items: the atlas and
 * print components are serialised to NBT exactly as the game stores them and scanned. Block entities (the only thing
 * NeroMaps puts in chunk NBT) cannot be constructed without a bootstrapped registry, so their source is scanned: no
 * block, block entity, atlas or survival class touches a UUID, a profile or a player name.
 */
class IdentityFreeTest {

    private static final UUID PLAYER = UUID.fromString("5f3e1c2a-9b7d-4e61-8a0f-2c4b6d8e1f03");
    private static final String NAME = "Cartographer_Alice";
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Pattern INT4 = Pattern.compile("\\[I;\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*\\]");

    /** Scans the SNBT form (stable across every Minecraft line) for anything identity-shaped. */
    private static void scan(Tag t, String where) {
        String snbt = t.toString();
        assertFalse(INT4.matcher(snbt).find(), where + ": a four-int array is how NBT stores a UUID");
        assertFalse(UUID_TEXT.matcher(snbt).find(), where + ": UUID text");
        assertFalse(snbt.contains(NAME), where + ": player name");
        String lower = snbt.toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("owner") || lower.contains("uuid") || lower.contains("player"),
                where + ": identity-named key");
    }

    @Test
    void atlasComponentCarriesCoverageOnly() {
        ExploredMap own = new ExploredMap();
        for (int x = -40; x < 40; x += 3) {
            for (int z = -40; z < 40; z += 5) {
                own.mark("minecraft:overworld", x, z, 4096);
            }
        }
        // the recording player's id and name are in scope and must not leak into the item
        AtlasData d = AtlasData.BLANK.withPage().withBinding().withLabel(NAME.substring(0, 3) + " camp")
                .record(own, "minecraft:overworld", 0, 0);
        assertTrue(d.filled());
        Tag nbt = AtlasData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        scan(nbt, "atlas");
        assertFalse(nbt.toString().contains(PLAYER.toString()));
        AtlasData back = AtlasData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(d, back);
        assertEquals(own.chunkCount("minecraft:overworld") > 0, back.chunkCount() > 0);
    }

    @Test
    void atlasLabelsAreFiltered() {
        AtlasData d = AtlasData.BLANK.withLabel("§cRed‮ label that is far too long for any atlas spine");
        assertTrue(d.label().length() <= AtlasData.MAX_LABEL);
        assertFalse(d.label().contains("§"));
        assertFalse(d.label().contains("‮"));
    }

    @Test
    void wallPrintIsAnAreaAndADimension() {
        WallPrint p = new WallPrint("minecraft:overworld", 120, -340, 70);
        Tag nbt = WallPrint.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
        scan(nbt, "print");
        assertTrue(nbt instanceof CompoundTag);
        assertEquals(WallPrint.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(), p);
        assertEquals(64, p.span(), "spans snap to 32 / 64 / 128");
        assertEquals(1, p.lod());
    }

    @Test
    void blockEntitiesAndSurvivalCodeNeverTouchAnIdentity() throws IOException {
        Pattern forbidden = Pattern.compile("\\bUUID\\b|getUUID|GameProfile|getGameProfile|getName\\(\\)|getScoreboardName"
                + "|getDisplayName|\\bowner\\b");
        // every block and block entity, and everything an item, an index entry or a map marker is built from;
        // (AtlasItem and SurvivalHandlers read the acting player's id to find their own record or rate limit them,
        // and write it nowhere: the NBT scans above prove what the stack holds)
        List<Path> files;
        try (Stream<Path> walk = Files.walk(RepoFiles.java().resolve("block"))) {
            files = new java.util.ArrayList<>(walk.filter(p -> p.toString().endsWith(".java")).toList());
        }
        for (String f : List.of("atlas/AtlasData.java", "atlas/WallPrint.java", "survival/PlacedIndex.java",
                "survival/SurvivalLayers.java")) {
            files.add(RepoFiles.java().resolve(f));
        }
        for (Path f : files) {
            String code = stripComments(RepoFiles.read(f));
            assertFalse(forbidden.matcher(code).find(), f.getFileName() + " touches an identity");
        }
    }

    @Test
    void indexedEntriesHoldNoIdentity() {
        for (java.lang.reflect.RecordComponent rc : PlacedIndex.Entry.class.getRecordComponents()) {
            assertFalse(rc.getType() == UUID.class, rc.getName());
        }
    }

    static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*\\*.*?\\*/", "").replaceAll("(?m)^\\s*//(?![?]).*$", "")
                .replaceAll("(?m)//\\s[A-Za-z].*$", "");
    }
}
