package za.co.neroland.neromaps.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** ARGB int pixels to tightly packed RGBA8 in a reused direct buffer (the 26.2+ upload path). Allocation-free. */
public final class RgbaStaging {

    private RgbaStaging() {
    }

    public static ByteBuffer allocate(int w, int h) {
        return ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Copies a {@code w x h} rect starting at {@code src[off]} (row stride {@code stride}) into {@code dst}. */
    public static void copy(int[] src, int off, int stride, int w, int h, ByteBuffer dst) {
        int o = 0;
        for (int y = 0; y < h; y++) {
            int row = off + y * stride;
            for (int x = 0; x < w; x++) {
                dst.putInt(o, toAbgr(src[row + x]));
                o += 4;
            }
        }
        dst.position(0);
        dst.limit(o);
    }

    /** ARGB to ABGR (what little-endian RGBA8 bytes and 1.21.1's NativeImage expect). */
    public static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }
}
