package za.co.neroland.neromaps.survival;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neromaps.server.tiles.CaptureService;

/**
 * Server-thread, memory-only index of NeroMaps blocks in ticking chunks: signposts and trail markers (for the
 * proximity map layers) and wall maps / map tables (whose count is capture demand, decision 006). Each block entity
 * touches its entry once a second; an entry not touched for three seconds (chunk unloaded, block gone) is dropped.
 * Nothing here is saved, and no entry holds an owner: these blocks have none.
 */
public final class PlacedIndex {

    public enum Kind { SIGNPOST, TRAIL_MARKER, WALL_MAP }

    /** One indexed block. {@code label}, {@code glyph}, {@code colour} and {@code shown} are signpost/marker data. */
    public record Entry(Kind kind, BlockPos pos, int lastTick, String label, int glyph, int colour, boolean shown) { }

    private static final Map<String, Map<Long, Entry>> BY_DIM = new HashMap<>();
    private static int wallMaps;

    private PlacedIndex() {
    }

    private static int now(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    private static void put(ServerLevel level, Entry e) {
        BY_DIM.computeIfAbsent(CaptureService.dimension(level), k -> new HashMap<>()).put(e.pos().asLong(), e);
    }

    public static void touchWallMap(ServerLevel level, BlockPos pos, boolean hasArea) {
        put(level, new Entry(Kind.WALL_MAP, pos.immutable(), now(level), "", 0, 0, hasArea));
    }

    public static void touchSignpost(ServerLevel level, BlockPos pos, String label, int glyph, int colour,
            boolean shown) {
        put(level, new Entry(Kind.SIGNPOST, pos.immutable(), now(level), label, glyph, colour, shown));
    }

    public static void touchTrailMarker(ServerLevel level, BlockPos pos, int colour) {
        put(level, new Entry(Kind.TRAIL_MARKER, pos.immutable(), now(level), "", 0, colour, true));
    }

    /** Entries of {@code kind} in {@code dimension} (server thread). */
    public static List<Entry> entries(String dimension, Kind kind) {
        Map<Long, Entry> m = BY_DIM.get(dimension);
        if (m == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : m.values()) {
            if (e.kind() == kind) {
                out.add(e);
            }
        }
        return out;
    }

    /** Once a second from the server tick: drop stale entries and publish the wall-map count as capture demand. */
    public static void second(MinecraftServer s) {
        int cut = s.getTickCount() - 60;
        int walls = 0;
        for (Iterator<Map<Long, Entry>> it = BY_DIM.values().iterator(); it.hasNext();) {
            Map<Long, Entry> m = it.next();
            m.values().removeIf(e -> e.lastTick() < cut);
            for (Entry e : m.values()) {
                if (e.kind() == Kind.WALL_MAP && e.shown()) {
                    walls++;
                }
            }
            if (m.isEmpty()) {
                it.remove();
            }
        }
        if (walls != wallMaps) {
            wallMaps = walls;
            CaptureService.setWallMaps(walls);
        }
    }

    public static int wallMaps() {
        return wallMaps;
    }

    public static void reset() {
        BY_DIM.clear();
        wallMaps = 0;
    }
}
