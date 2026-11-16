package za.co.neroland.neromaps.spike;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.platform.NativeImage;
//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * SPIKE. One 256 x 256 RGBA texture created once and then updated a 16 x 16 chunk at a time, never reallocated.
 * The texture is a toroidal window over the world (texel = world block mod 256) sampled with REPEAT, so the
 * minimap stays one quad wherever the player stands. Render thread only. Upload path per Minecraft line:
 * <ul>
 *   <li>26.2+: {@code CommandEncoder.writeToTexture(GpuTexture, ByteBuffer, mip, layer, x, y, w, h)} from one reused
 *       1 KiB direct staging buffer (the {@code NativeImage} overload with source offsets is gone);</li>
 *   <li>1.21.8 - 26.1.2: {@code writeToTexture(GpuTexture, NativeImage, mip, layer, x, y, w, h, srcX, srcY)} straight
 *       from the texture's own {@code NativeImage};</li>
 *   <li>1.21.5: the same without the layer argument;</li>
 *   <li>1.21.1 - 1.21.4: bind, then {@code NativeImage.upload(level, x, y, srcX, srcY, w, h, ...)}.</li>
 * </ul>
 */
public final class SpikeRegionTexture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("neromaps", "spike/region");
    public static final Identifier MARKER_ID = Identifier.fromNamespaceAndPath("neromaps", "spike/marker");
    private static final int SIZE = SpikeFrame.TEXTURE;
    private static final int CHUNK = 16;
    private static final int MARKER = 8;

    private final DynamicTexture texture;
    private final DynamicTexture marker;
    private final ByteBuffer staging = SpikeStaging.allocate(CHUNK, CHUNK);
    private int uploads;

    public SpikeRegionTexture() {
        //? if >=1.21.5 {
        texture = new DynamicTexture("neromaps spike region", SIZE, SIZE, true);
        marker = new DynamicTexture("neromaps spike marker", MARKER, MARKER, true);
        //?} else {
        /*texture = new DynamicTexture(SIZE, SIZE, true);
        marker = new DynamicTexture(MARKER, MARKER, true);
        *///?}
        Minecraft.getInstance().getTextureManager().register(ID, texture);
        Minecraft.getInstance().getTextureManager().register(MARKER_ID, marker);
        // a white disc with a dark rim; markers are tinted by vertex colour
        NativeImage dot = marker.getPixels();
        for (int y = 0; y < MARKER; y++) {
            for (int x = 0; x < MARKER; x++) {
                float dx = x + 0.5f - MARKER / 2f;
                float dy = y + 0.5f - MARKER / 2f;
                float d = dx * dx + dy * dy;
                int argb = d > 16f ? 0 : d > 9f ? 0xFF202020 : 0xFFFFFFFF;
                //? if >=1.21.2 {
                dot.setPixel(x, y, argb);
                //?} else {
                /*dot.setPixelRGBA(x, y, SpikeStaging.toAbgr(argb));
                *///?}
            }
        }
        marker.upload();
    }

    /**
     * Uploads up to {@code budget} dirty chunks of {@code argb} (a 256 x 256 ARGB mirror). Returns the number
     * uploaded. One call per frame at most: this is the frame's single upload batch.
     */
    public int uploadDirty(int[] argb, SpikeDirtyChunks dirty, int budget) {
        int done = 0;
        while (done < budget) {
            int c = dirty.poll();
            if (c < 0) {
                break;
            }
            uploadChunk(argb, (c & 15) * CHUNK, (c >>> 4) * CHUNK);
            done++;
        }
        uploads += done;
        return done;
    }

    /** Chunks uploaded so far. */
    public int uploads() {
        return uploads;
    }

    private void uploadChunk(int[] argb, int x, int y) {
        //? if >=26.2 {
        SpikeStaging.copyRect(argb, SIZE, x, y, CHUNK, CHUNK, staging);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), staging, 0, 0, x, y, CHUNK, CHUNK);
        //?} else if >=1.21.8 {
        /*copyToImage(argb, x, y);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), texture.getPixels(), 0, 0, x, y, CHUNK, CHUNK, x, y);
        *///?} else if >=1.21.5 {
        /*copyToImage(argb, x, y);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), texture.getPixels(), 0, x, y, CHUNK, CHUNK, x, y);
        *///?} else if >=1.21.2 {
        /*copyToImage(argb, x, y);
        texture.bind();
        texture.getPixels().upload(0, x, y, x, y, CHUNK, CHUNK, false);
        *///?} else {
        /*copyToImage(argb, x, y);
        texture.bind();
        texture.getPixels().upload(0, x, y, x, y, CHUNK, CHUNK, false, false);
        *///?}
    }

    //? if >=1.21.2 && <26.2 {
    /*private void copyToImage(int[] argb, int x0, int y0) {
        NativeImage image = texture.getPixels();
        for (int y = y0; y < y0 + CHUNK; y++) {
            for (int x = x0; x < x0 + CHUNK; x++) {
                image.setPixel(x, y, argb[y * SIZE + x]);
            }
        }
    }
    *///?} else if <1.21.2 {
    /*private void copyToImage(int[] argb, int x0, int y0) {
        NativeImage image = texture.getPixels();
        for (int y = y0; y < y0 + CHUNK; y++) {
            for (int x = x0; x < x0 + CHUNK; x++) {
                image.setPixelRGBA(x, y, SpikeStaging.toAbgr(argb[y * SIZE + x]));
            }
        }
    }
    *///?}

    /** The region texture (for the 26.x render state's texture setup). */
    public DynamicTexture texture() {
        return texture;
    }

    /** The marker sprite texture. */
    public DynamicTexture marker() {
        return marker;
    }

    /** Frees both GPU textures. */
    public void close() {
        Minecraft.getInstance().getTextureManager().release(ID);
        Minecraft.getInstance().getTextureManager().release(MARKER_ID);
    }
}
