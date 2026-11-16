package za.co.neroland.neromaps.scan;

import za.co.neroland.neromaps.tile.Tile;

/**
 * The one column scan, pure over {@link ColumnSource}, identical on every Minecraft line:
 * <ul>
 *   <li>surface: from {@code WORLD_SURFACE - 1} down through invisible blocks to the first visible one; a fluid
 *       records its kind, then the scan walks down to the floor and counts the depth (capped at 255);</li>
 *   <li>roofed: from the logical top down through the solid ceiling, then through air, then as above, setting
 *       {@code CEILING};</li>
 *   <li>cave (client only, never cached to disk): from a start height; if that is solid, down at most 16 to air,
 *       then down to the first visible block.</li>
 * </ul>
 * Empty sections are skipped 16 at a time. Allocates nothing.
 */
public final class ColumnScanner {

    public static final int MAX_DEPTH = 255;
    public static final int CAVE_WALK = 16;

    private ColumnScanner() {
    }

    public static void scanSurface(ColumnSource s, ChunkColumns out) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int start = Math.min(s.surfaceY(x, z), s.maxY()) - 1;
                resolveDown(s, x, z, start, 0, out);
            }
        }
    }

    /** Roofed dimensions: {@code top} is the highest Y to consider (min Y + logical height - 1). */
    public static void scanRoofed(ColumnSource s, int top, ChunkColumns out) {
        int y0 = Math.min(top, s.maxY() - 1);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = y0;
                int min = s.minY();
                // through the solid ceiling run
                while (y >= min && !s.sectionEmpty(y) && s.kind(x, y, z) != ColumnSource.KIND_INVISIBLE) {
                    y--;
                }
                if (y < min) {
                    record(s, x, z, y0, Tile.F_CEILING, out);
                    continue;
                }
                resolveDown(s, x, z, y, Tile.F_CEILING, out);
            }
        }
    }

    /** Cave slice at {@code startY} (the player's feet + 2). */
    public static void scanCave(ColumnSource s, int startY, ChunkColumns out) {
        int start = Math.min(startY, s.maxY() - 1);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = start;
                if (!s.sectionEmpty(y) && s.kind(x, y, z) != ColumnSource.KIND_INVISIBLE) {
                    int stop = y - CAVE_WALK;
                    while (y > stop && y >= s.minY() && !s.sectionEmpty(y)
                            && s.kind(x, y, z) != ColumnSource.KIND_INVISIBLE) {
                        y--;
                    }
                    if (y == stop || y < s.minY()) {
                        // solid rock all the way: draw the rock at the slice
                        record(s, x, z, start, 0, out);
                        continue;
                    }
                }
                resolveDown(s, x, z, y, 0, out);
            }
        }
    }

    private static void resolveDown(ColumnSource s, int x, int z, int yStart, int extraFlags, ChunkColumns out) {
        int i = (z << 4) | x;
        int min = s.minY();
        int y = yStart;
        while (y >= min) {
            if (s.sectionEmpty(y)) {
                y = (y & ~15) - 1;
                continue;
            }
            if (s.kind(x, y, z) != ColumnSource.KIND_INVISIBLE) {
                break;
            }
            y--;
        }
        if (y < min) {
            out.height[i] = (short) min;
            out.block[i] = s.airBlock();
            out.biome[i] = s.biome(x, min, z);
            out.depth[i] = 0;
            out.flags[i] = (byte) (Tile.F_PRESENT | extraFlags);
            return;
        }
        int kind = s.kind(x, y, z);
        int flags = Tile.F_PRESENT | extraFlags;
        if (kind == ColumnSource.KIND_SOLID) {
            record(s, x, z, y, extraFlags, out);
            return;
        }
        if (kind == ColumnSource.KIND_WATERLOGGED) {
            record(s, x, z, y, extraFlags | Tile.F_WATERLOGGED_TOP, out);
            return;
        }
        flags |= kind == ColumnSource.KIND_WATER ? Tile.F_WATER
                : kind == ColumnSource.KIND_LAVA ? Tile.F_LAVA : Tile.F_FLUID_OTHER;
        int surface = y;
        int biome = s.biome(x, surface, z);
        int fluidBlock = s.block(x, y, z);
        int depth = 0;
        int floorBlock = fluidBlock;
        while (true) {
            if (depth >= MAX_DEPTH || y - 1 < min) {
                break;
            }
            y--;
            if (s.sectionEmpty(y)) {
                // the fluid ends at an empty section: treat what is below as the floor search
                floorBlock = findFloor(s, x, z, y);
                break;
            }
            int k = s.kind(x, y, z);
            if (k == kind) {
                depth++;
                continue;
            }
            if (k == ColumnSource.KIND_INVISIBLE) {
                floorBlock = findFloor(s, x, z, y);
                break;
            }
            floorBlock = s.block(x, y, z);
            break;
        }
        out.height[i] = (short) surface;
        out.block[i] = floorBlock;
        out.biome[i] = biome;
        out.depth[i] = (byte) Math.min(MAX_DEPTH, depth + 1);
        out.flags[i] = (byte) flags;
    }

    private static int findFloor(ColumnSource s, int x, int z, int y) {
        int min = s.minY();
        while (y >= min) {
            if (s.sectionEmpty(y)) {
                y = (y & ~15) - 1;
                continue;
            }
            if (s.kind(x, y, z) != ColumnSource.KIND_INVISIBLE) {
                return s.block(x, y, z);
            }
            y--;
        }
        return s.airBlock();
    }

    private static void record(ColumnSource s, int x, int z, int y, int extraFlags, ChunkColumns out) {
        int i = (z << 4) | x;
        out.height[i] = (short) y;
        out.block[i] = s.block(x, y, z);
        out.biome[i] = s.biome(x, y, z);
        out.depth[i] = 0;
        out.flags[i] = (byte) (Tile.F_PRESENT | extraFlags);
    }
}
