package za.co.neroland.neromaps.spike;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * SPIKE. Copies one rectangle of an ARGB texel array into a tightly packed RGBA8 staging buffer, the source shape
 * 26.2+'s {@code CommandEncoder.writeToTexture(GpuTexture, ByteBuffer, mip, layer, destX, destY, w, h)} needs
 * (26.2 removed the {@code NativeImage} overload with source offsets). Absolute puts only: no allocation.
 */
public final class SpikeStaging {

    private SpikeStaging() {
    }

    /** A direct little-endian buffer big enough for {@code w * h} RGBA8 texels. Allocate once and reuse. */
    public static ByteBuffer allocate(int w, int h) {
        return ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Writes texels {@code [x0, x0 + w) x [y0, y0 + h)} of {@code argb} (row stride {@code stride}) into {@code dst}
     * as R, G, B, A bytes and leaves {@code dst} at position 0 with limit {@code w * h * 4}.
     */
    public static void copyRect(int[] argb, int stride, int x0, int y0, int w, int h, ByteBuffer dst) {
        int off = 0;
        for (int y = y0; y < y0 + h; y++) {
            int row = y * stride;
            for (int x = x0; x < x0 + w; x++) {
                dst.putInt(off, toAbgr(argb[row + x]));
                off += 4;
            }
        }
        dst.position(0);
        dst.limit(off);
    }

    /** ARGB to ABGR (red and blue swapped): the int that, stored little-endian, is R, G, B, A in memory. */
    public static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }
}
