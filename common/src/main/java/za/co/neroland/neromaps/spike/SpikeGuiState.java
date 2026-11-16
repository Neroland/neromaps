package za.co.neroland.neromaps.spike;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?} else if >=26.1 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}
//? if >=26.1 {
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jetbrains.annotations.Nullable;

// SPIKE, 26.x only. A reusable GUI element that streams a whole batch of quads (the minimap surface, or every
// visible marker) in one {@code buildVertices} call. Vanilla's {@code blit} allocates a {@code BlitRenderState},
// a {@code TextureSetup} and a {@code Matrix3x2f} per quad; this object, its texture setup and its bounds are
// created once and refilled each frame, so adding it to the frame's {@code GuiRenderState} allocates nothing.
public final class SpikeGuiState implements GuiElementRenderState {

    private final Matrix3x2f pose = new Matrix3x2f();
    private @Nullable TextureSetup textureSetup;
    private @Nullable ScreenRectangle bounds;
    private int bx;
    private int by;
    private int bw;
    private int bh;
    private float[] xy = new float[0];
    private float @Nullable [] uv;
    private int @Nullable [] colours;
    private int colour = -1;
    private int quads;

    // Sets the texture once (its view and sampler never change for a live texture).
    public void texture(TextureSetup setup) {
        this.textureSetup = setup;
    }

    // True once {@link #texture} was called.
    public boolean hasTexture() {
        return textureSetup != null;
    }

    // Refills the batch. {@code uv} null means each quad samples the whole texture; {@code colours} null means
    // every quad uses {@code colour}. The arrays are the producer's own (no copy).
    public void set(Matrix3x2fc guiPose, float[] xy, float @Nullable [] uv, int @Nullable [] colours, int colour,
            int quads, int x0, int y0, int x1, int y1) {
        this.pose.set(guiPose);
        this.xy = xy;
        this.uv = uv;
        this.colours = colours;
        this.colour = colour;
        this.quads = quads;
        if (bounds == null || x0 != bx || y0 != by || x1 - x0 != bw || y1 - y0 != bh) {
            bx = x0;
            by = y0;
            bw = x1 - x0;
            bh = y1 - y0;
            bounds = new ScreenRectangle(bx, by, bw, bh); // only when the layout changes
        }
    }

    // Quads in the current batch.
    public int quads() {
        return quads;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float[] p = xy;
        float[] t = uv;
        int[] c = colours;
        for (int q = 0; q < quads; q++) {
            int argb = c == null ? colour : c[q];
            int o = q * 8;
            if (t == null) {
                vertexConsumer.addVertexWith2DPose(pose, p[o], p[o + 1]).setUv(0f, 0f).setColor(argb);
                vertexConsumer.addVertexWith2DPose(pose, p[o + 2], p[o + 3]).setUv(0f, 1f).setColor(argb);
                vertexConsumer.addVertexWith2DPose(pose, p[o + 4], p[o + 5]).setUv(1f, 1f).setColor(argb);
                vertexConsumer.addVertexWith2DPose(pose, p[o + 6], p[o + 7]).setUv(1f, 0f).setColor(argb);
            } else {
                for (int k = 0; k < 8; k += 2) {
                    vertexConsumer.addVertexWith2DPose(pose, p[o + k], p[o + k + 1]).setUv(t[o + k], t[o + k + 1])
                            .setColor(argb);
                }
            }
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public TextureSetup textureSetup() {
        TextureSetup setup = textureSetup;
        return setup != null ? setup : TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
//?}
