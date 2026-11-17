package za.co.neroland.neromaps.engine;

import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.settings.MinimapSettings.Anchor;

/**
 * Where the minimap goes: any corner or a free position, always inside a safe-area margin. The
 * default anchor is top-right, pushed down one 26 px row per active status-effect row so vanilla's effect icons
 * stay visible. Readouts stack under the map (above it is never used). Integers only; allocation-free.
 */
public final class MinimapLayout {

    public static final int EFFECT_ROW = 26;
    public static final int READOUT_LINE = 10;
    /** Space between the frame and the readout block, and the block's inner padding above and below its text. */
    public static final int READOUT_GAP = 2;
    public static final int READOUT_PAD = 4;

    /** Height the readout block takes under the map, gap included; 0 with no lines. */
    public static int readoutHeight(int lines) {
        return lines > 0 ? READOUT_GAP + lines * READOUT_LINE + READOUT_PAD * 2 - 2 : 0;
    }

    /** Map area (inside the frame). */
    public int left;
    public int top;
    public int size;
    /** Frame thickness. */
    public int frame;
    /** Readout block (under the map). */
    public int readoutX;
    public int readoutY;
    public int readoutLines;

    public int right() {
        return left + size;
    }

    public int bottom() {
        return top + size;
    }

    public void compute(MinimapSettings s, int screenW, int screenH, int effectRows, int readoutLines, int frame) {
        this.size = s.size;
        this.frame = frame;
        this.readoutLines = readoutLines;
        int m = s.margin;
        int box = size + frame * 2;
        int readH = readoutHeight(readoutLines);
        int x;
        int y;
        Anchor a = s.anchor;
        switch (a) {
            case TOP_LEFT:
                x = m;
                y = m;
                break;
            case BOTTOM_RIGHT:
                x = screenW - m - box;
                y = screenH - m - box - readH;
                break;
            case BOTTOM_LEFT:
                x = m;
                y = screenH - m - box - readH;
                break;
            case FREE:
                x = Math.round(s.freeX * (screenW - box));
                y = Math.round(s.freeY * (screenH - box - readH));
                break;
            case TOP_RIGHT:
            default:
                x = screenW - m - box;
                y = m + effectRows * EFFECT_ROW;
                break;
        }
        // safe area: never closer than the margin to any edge (if the screen is big enough)
        x = clamp(x, m, screenW - m - box);
        y = clamp(y, m, screenH - m - box - readH);
        left = x + frame;
        top = y + frame;
        readoutX = left;
        readoutY = top + size + frame + READOUT_GAP;
    }

    private static int clamp(int v, int lo, int hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
