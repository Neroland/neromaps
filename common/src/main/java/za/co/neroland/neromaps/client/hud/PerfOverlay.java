package za.co.neroland.neromaps.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.perf.AllocationMeter;
import za.co.neroland.neromaps.perf.PerfCounters;

/**
 * The {@code /neromaps perf} overlay: minimap frame cost (CPU), bytes allocated inside the HUD body,
 * uploads per frame, scan queue depth, texture memory and cache size. Global numbers only; never a player, name
 * or position. Text is rebuilt every 10 frames and is drawn after the HUD body's allocation measurement ends.
 */
public final class PerfOverlay {

    public static boolean enabled;
    /** Server counter lines from the last {@code /neromaps perf} (operators only; global numbers). */
    public static java.util.List<String> serverLines = java.util.List.of();
    private static final String[] LINES = new String[9];
    private static int frame;

    private PerfOverlay() {
    }

    public static String[] lines() {
        rebuild();
        return LINES;
    }

    private static void rebuild() {
        LINES[0] = "NeroMaps perf";
        LINES[1] = String.format(java.util.Locale.ROOT, "minimap CPU %.1f us/frame (frames %d)",
                PerfCounters.hudNanos / 1000.0, PerfCounters.frames);
        LINES[2] = AllocationMeter.supported()
                ? "HUD body alloc " + PerfCounters.hudBytesLastFrame + " B/frame (max " + PerfCounters.hudBytesMax + ")"
                : "HUD body alloc: unsupported JVM";
        LINES[3] = "uploads " + PerfCounters.uploadsLastFrame + "/frame (max " + PerfCounters.uploadsMax
                + "), batches " + PerfCounters.batchesLastFrame;
        long scans = PerfCounters.scans.get();
        LINES[4] = String.format(java.util.Locale.ROOT, "scan queue %d, dirty %d, scans %d (%.1f us avg)",
                PerfCounters.scanQueue.get(), PerfCounters.dirtyChunks, scans,
                scans == 0 ? 0.0 : PerfCounters.scanNanos.get() / 1000.0 / scans);
        LINES[5] = String.format(java.util.Locale.ROOT, "textures %.1f MB, tiles in memory %d",
                PerfCounters.textureBytes / 1048576.0, PerfCounters.tilesInMemory);
        LINES[6] = String.format(java.util.Locale.ROOT, "disk cache %.1f MB, io queue %d, read %d, written %d",
                PerfCounters.cacheBytes.get() / 1048576.0, PerfCounters.ioQueue.get(), PerfCounters.tilesRead.get(),
                PerfCounters.tilesWritten.get());
        LINES[7] = "corrupt tiles dropped " + PerfCounters.corruptTiles.get();
        LINES[8] = "GPU frame cost: measure with the O1 procedure (on/off pairs)";
    }

    static void render(GuiGraphicsExtractor g, Font font) {
        if (frame++ % 10 == 0 || LINES[0] == null) {
            rebuild();
        }
        int y = 4;
        java.util.List<String> server = serverLines;
        g.fill(2, 2, 302, 4 + (LINES.length + server.size()) * 10, (MapPalette.PANEL & 0xFFFFFF) | 0xC0000000);
        for (String s : LINES) {
            g.text(font, s, 4, y, MapPalette.TEXT);
            y += 10;
        }
        for (String s : server) {
            g.text(font, s, 4, y, MapPalette.TEXT_SECONDARY);
            y += 10;
        }
    }
}
