package za.co.neroland.neromaps.client.hud;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?} else if >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}
//? if >=1.21.6 {
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
//?} else if >=1.21.6 {
/*import net.minecraft.client.gui.render.state.GuiElementRenderState;
*///?}
//? if >=1.21.6 {

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jetbrains.annotations.Nullable;

// A reusable GUI element that streams a whole batch of quads (the minimap surface, or the overlay) in one
// buildVertices call. Vanilla's blit allocates a render state, a texture setup and a matrix per quad; this object,
// its texture setup and its bounds are made once and refilled each frame, so submitting it allocates nothing in
// NeroMaps code. 26.x reaches the frame's GUI render state through a public getter; 1.21.6 - 1.21.11 through
// mixin.GuiGraphicsAccessor. Client only.
public final class MinimapGuiState implements GuiElementRenderState {

    private final Matrix3x2f pose = new Matrix3x2f();
    private @Nullable TextureSetup textureSetup;
    private ScreenRectangle bounds = new ScreenRectangle(0, 0, 1, 1);
    private float[] xy = new float[0];
    private float[] uv = new float[0];
    private int @Nullable [] colours;
    private int colour = -1;
    private int quads;

    public void texture(TextureSetup setup) {
        this.textureSetup = setup;
    }

    public boolean hasTexture() {
        return textureSetup != null;
    }

    // Refills the batch; arrays are the producer's own. colours null means every quad is colour.
    public void set(Matrix3x2fc guiPose, float[] xy, float[] uv, int @Nullable [] colours, int colour, int quads,
            int x0, int y0, int x1, int y1) {
        this.pose.set(guiPose);
        this.xy = xy;
        this.uv = uv;
        this.colours = colours;
        this.colour = colour;
        this.quads = quads;
        ScreenRectangle b = bounds;
        if (b.left() != x0 || b.top() != y0 || b.width() != x1 - x0 || b.height() != y1 - y0) {
            bounds = new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)); // layout change only
        }
    }

    public int quads() {
        return quads;
    }

    //? if >=1.21.10 {
    @Override
    public void buildVertices(VertexConsumer vc) {
        float[] p = xy;
        float[] t = uv;
        int[] c = colours;
        for (int q = 0; q < quads; q++) {
            int argb = c == null ? colour : c[q];
            int o = q * 8;
            for (int k = 0; k < 8; k += 2) {
                vc.addVertexWith2DPose(pose, p[o + k], p[o + k + 1]).setUv(t[o + k], t[o + k + 1]).setColor(argb);
            }
        }
    }
    //?} else {
    /*@Override
    public void buildVertices(VertexConsumer vc, float z) {
        float[] p = xy;
        float[] t = uv;
        int[] c = colours;
        for (int q = 0; q < quads; q++) {
            int argb = c == null ? colour : c[q];
            int o = q * 8;
            for (int k = 0; k < 8; k += 2) {
                vc.addVertexWith2DPose(pose, p[o + k], p[o + k + 1], z).setUv(t[o + k], t[o + k + 1]).setColor(argb);
            }
        }
    }
    *///?}

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
    public ScreenRectangle bounds() {
        return bounds;
    }
}
//?} else {
/*// Before 1.21.6 the minimap streams straight into a buffer (see MinimapHud); this class is unused.
public final class MinimapGuiState {
}
*///?}
