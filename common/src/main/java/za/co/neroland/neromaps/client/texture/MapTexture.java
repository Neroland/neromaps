package za.co.neroland.neromaps.client.texture;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.platform.NativeImage;
//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import za.co.neroland.neromaps.engine.RgbaStaging;
import za.co.neroland.neromaps.perf.PerfCounters;

/**
 * One square RGBA texture created once and updated by sub-rectangle, never reallocated. Every
 * {@code //?} of the upload path lives here:
 * <ul>
 *   <li>26.2+: {@code writeToTexture(GpuTexture, ByteBuffer, mip, layer, x, y, w, h)} from one reused direct
 *       staging buffer;</li>
 *   <li>1.21.8 - 26.1.2: the 10-argument {@code writeToTexture} from the texture's own {@code NativeImage};</li>
 *   <li>1.21.5: the same without the layer argument;</li>
 *   <li>1.21.1 - 1.21.4: bind, then {@code NativeImage.upload}.</li>
 * </ul>
 * Backed by a {@code DynamicTexture} on every line, which keeps a CPU copy: memory is charged twice (GPU + CPU),
 * exactly as the texture budget accounts for the fallback. Render thread only.
 */
public final class MapTexture {

    public final Identifier id;
    public final int size;
    private final DynamicTexture texture;
    private final ByteBuffer staging;
    private boolean closed;

    public MapTexture(String name, int size) {
        this.id = Identifier.fromNamespaceAndPath("neromaps", "dynamic/" + name);
        this.size = size;
        //? if >=1.21.5 {
        texture = new DynamicTexture("neromaps " + name, size, size, true);
        //?} else {
        /*texture = new DynamicTexture(size, size, true);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
        staging = RgbaStaging.allocate(16, 16);
        PerfCounters.textureBytes += bytes();
    }

    /** Bytes charged to the texture budget: GPU texture plus the CPU-side copy. */
    public long bytes() {
        return (long) size * size * 4L * 2L;
    }

    public DynamicTexture texture() {
        return texture;
    }

    /** Uploads a 16 x 16 patch {@code argb[off..]} (row stride {@code stride}) to texel {@code (x, y)}. */
    public void upload16(int[] argb, int off, int stride, int x, int y) {
        //? if >=26.2 {
        RgbaStaging.copy(argb, off, stride, 16, 16, staging);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), staging, 0, 0, x, y, 16, 16);
        //?} else if >=1.21.8 {
        /*copyToImage(argb, off, stride, x, y);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), texture.getPixels(), 0, 0, x, y, 16, 16, x, y);
        *///?} else if >=1.21.5 {
        /*copyToImage(argb, off, stride, x, y);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), texture.getPixels(), 0, x, y, 16, 16, x, y);
        *///?} else if >=1.21.2 {
        /*copyToImage(argb, off, stride, x, y);
        texture.bind();
        texture.getPixels().upload(0, x, y, x, y, 16, 16, false);
        *///?} else {
        /*copyToImage(argb, off, stride, x, y);
        texture.bind();
        texture.getPixels().upload(0, x, y, x, y, 16, 16, false, false);
        *///?}
    }

    /** Writes the whole image from {@code argb} (size x size) and uploads it (small sprite sheets only). */
    public void uploadAll(int[] argb) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                //? if >=1.21.2 {
                image.setPixel(x, y, argb[y * size + x]);
                //?} else {
                /*image.setPixelRGBA(x, y, RgbaStaging.toAbgr(argb[y * size + x]));
                *///?}
            }
        }
        texture.upload();
    }

    //? if >=1.21.2 && <26.2 {
    /*private void copyToImage(int[] argb, int off, int stride, int x0, int y0) {
        NativeImage image = texture.getPixels();
        for (int y = 0; y < 16; y++) {
            int row = off + y * stride;
            for (int x = 0; x < 16; x++) {
                image.setPixel(x0 + x, y0 + y, argb[row + x]);
            }
        }
    }
    *///?} else if <1.21.2 {
    /*private void copyToImage(int[] argb, int off, int stride, int x0, int y0) {
        NativeImage image = texture.getPixels();
        for (int y = 0; y < 16; y++) {
            int row = off + y * stride;
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(x0 + x, y0 + y, RgbaStaging.toAbgr(argb[row + x]));
            }
        }
    }
    *///?}

    public void close() {
        if (!closed) {
            closed = true;
            Minecraft.getInstance().getTextureManager().release(id);
            PerfCounters.textureBytes -= bytes();
        }
    }
}
