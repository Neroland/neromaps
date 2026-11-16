package za.co.neroland.neromaps.spike;

//? if >=26.1 {
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * SPIKE. The debug minimap HUD layer each loader registers. Draws nothing unless the JVM runs with
 * {@code -Dneromaps.spike.minimap=true}. Per frame: at most one upload batch of dirty chunks, the CPU path in
 * {@link SpikeFrame}, then one surface batch and one marker batch. Every 600 frames it logs the average CPU cost
 * of this method (the only allocation it makes, once every ten seconds or so).
 * <p>Draw path per line: 26.x adds two reusable {@code SpikeGuiState} elements straight to the frame's
 * {@code GuiRenderState} (round, rotating, zero allocations). 1.21.6 - 1.21.11 have no public route to that state,
 * so the spike falls back to vanilla blits (square, north-locked, one vanilla allocation set per quad); Stage 2
 * needs an accessor mixin for {@code GuiGraphics.guiRenderState} there. 1.21.4 - 1.21.5 stream into
 * {@code GuiGraphics.drawSpecial}'s buffer source; 1.21.1 draws immediately through the {@code Tesselator}.
 */
public final class SpikeMinimapHud {

    /** Off unless explicitly enabled for a profiling session. */
    public static final boolean ENABLED = Boolean.getBoolean("neromaps.spike.minimap");
    /** Fake markers placed around the player on the first frame. */
    public static final int FAKE_MARKERS = 200;
    private static final int UPLOAD_BUDGET = 8;
    private static final int REPORT_FRAMES = 600;

    private static final SpikeFrame FRAME = new SpikeFrame();
    private static final SpikeDirtyChunks DIRTY = new SpikeDirtyChunks();
    private static @Nullable SpikeRegionTexture region;
    private static int @Nullable [] mirror;
    private static int frames;
    private static long nanos;

    //? if >=26.1 {
    private static final SpikeGuiState SURFACE = new SpikeGuiState();
    private static final SpikeGuiState MARKERS = new SpikeGuiState();
    //?}

    private SpikeMinimapHud() {
    }

    /** HUD layer body. Render thread only. */
    public static void render(GuiGraphicsExtractor g) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        long t0 = System.nanoTime();
        SpikeRegionTexture r = region;
        int[] m = mirror;
        if (r == null || m == null) {
            r = init(player);
            m = mirror;
            if (m == null) {
                return;
            }
        }
        // keep the sub-rectangle upload path warm: re-dirty one chunk every 20 frames
        frames++;
        if (frames % 20 == 0) {
            DIRTY.mark((frames / 20) & 15, (frames / 320) & 15);
        }
        r.uploadDirty(m, DIRTY, UPLOAD_BUDGET);
        FRAME.update(player.getX(), player.getZ(), player.getYRot(), mc.getWindow().getGuiScaledWidth());
        emit(g, mc, r);
        nanos += System.nanoTime() - t0;
        if (frames % REPORT_FRAMES == 0) {
            NeroMapsCommon.LOGGER.info("[NeroMaps spike] minimap CPU {} ns/frame (avg of {}), {} chunk uploads so far",
                    nanos / REPORT_FRAMES, REPORT_FRAMES, r.uploads());
            nanos = 0;
        }
    }

    private static SpikeRegionTexture init(LocalPlayer player) {
        SpikeRegionTexture r = new SpikeRegionTexture();
        int[] m = new int[SpikeColumns.SIZE * SpikeColumns.SIZE];
        SpikeTileFill.fill(SpikeColumns.fake(0), SpikeTileFill.fakePalette(), m);
        DIRTY.markAll();
        FRAME.markers.clear();
        double px = player.getX();
        double pz = player.getZ();
        for (int i = 0; i < FAKE_MARKERS; i++) {
            double a = i * 2.399963; // golden angle spiral
            double d = 6 + i * 0.4;
            FRAME.markers.add(px + Math.cos(a) * d, pz + Math.sin(a) * d, 0xFF000000 | (i * 0x3F7A1D & 0xFFFFFF));
        }
        //? if >=1.21.6 && <26.1 {
        /*FRAME.round = false;
        FRAME.rotate = false;
        *///?}
        region = r;
        mirror = m;
        return r;
    }

    private static void emit(GuiGraphicsExtractor g, Minecraft mc, SpikeRegionTexture r) {
        //? if >=26.2 {
        emitState(g, mc.gameRenderer.gameRenderState().guiRenderState, r);
        //?} else if >=26.1 {
        /*emitState(g, mc.gameRenderer.getGameRenderState().guiRenderState, r);
        *///?} else if >=1.21.6 {
        /*emitBlits(g);
        *///?} else if >=1.21.2 {
        /*emitDrawSpecial(g);
        *///?} else {
        /*emitTesselator(g);
        *///?}
    }

    //? if >=26.1 {
    private static void emitState(GuiGraphicsExtractor g, GuiRenderState state, SpikeRegionTexture r) {
        if (!SURFACE.hasTexture()) {
            SURFACE.texture(TextureSetup.singleTexture(r.texture().getTextureView(), r.texture().getSampler()));
            MARKERS.texture(TextureSetup.singleTexture(r.marker().getTextureView(), r.marker().getSampler()));
        }
        SpikeMinimapGeometry s = FRAME.surface;
        SpikeMarkerBatch b = FRAME.markers;
        SURFACE.set(g.pose(), s.xy, s.uv, null, -1, s.quads, FRAME.left, FRAME.top, FRAME.right, FRAME.bottom);
        state.addGuiElement(SURFACE);
        if (b.quads > 0) {
            MARKERS.set(g.pose(), b.xy, null, b.quadColour, -1, b.quads, FRAME.left, FRAME.top, FRAME.right,
                    FRAME.bottom);
            state.addGuiElement(MARKERS);
        }
    }
    //?}

    //? if >=1.21.6 && <26.1 {
    /*private static void emitBlits(GuiGraphicsExtractor g) {
        float[] uv = FRAME.surface.uv;
        g.blit(SpikeRegionTexture.ID, FRAME.left, FRAME.top, FRAME.right, FRAME.bottom, uv[0], uv[4], uv[1], uv[5]);
        SpikeMarkerBatch b = FRAME.markers;
        int size = (int) (FRAME.markerHalf * 2);
        for (int q = 0; q < b.quads; q++) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SpikeRegionTexture.MARKER_ID,
                    (int) b.xy[q * 8], (int) b.xy[q * 8 + 1], 0f, 0f, size, size, size, size, b.quadColour[q]);
        }
    }
    *///?}

    //? if >=1.21.2 && <1.21.6 {
    /*private static org.joml.Matrix4f legacyPose = new org.joml.Matrix4f();
    private static final java.util.function.Consumer<net.minecraft.client.renderer.MultiBufferSource> EMIT =
            SpikeMinimapHud::emitInto;

    private static void emitDrawSpecial(GuiGraphicsExtractor g) {
        legacyPose = g.pose().last().pose();
        g.drawSpecial(EMIT);
    }

    private static void emitInto(net.minecraft.client.renderer.MultiBufferSource buffers) {
        org.joml.Matrix4f pose = legacyPose;
        SpikeMinimapGeometry s = FRAME.surface;
        SpikeMarkerBatch b = FRAME.markers;
        quads(buffers.getBuffer(net.minecraft.client.renderer.RenderType.guiTextured(SpikeRegionTexture.ID)), pose,
                s.xy, s.uv, null, s.quads);
        quads(buffers.getBuffer(net.minecraft.client.renderer.RenderType.guiTextured(SpikeRegionTexture.MARKER_ID)),
                pose, b.xy, null, b.quadColour, b.quads);
    }
    *///?}

    //? if <1.21.2 {
    /*private static void emitTesselator(GuiGraphicsExtractor g) {
        org.joml.Matrix4f pose = g.pose().last().pose();
        SpikeMinimapGeometry s = FRAME.surface;
        SpikeMarkerBatch b = FRAME.markers;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, SpikeRegionTexture.ID);
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        quads(bb, pose, s.xy, s.uv, null, s.quads);
        com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
        if (b.quads > 0) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, SpikeRegionTexture.MARKER_ID);
            bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
            quads(bb, pose, b.xy, null, b.quadColour, b.quads);
            mesh = bb.build();
            if (mesh != null) {
                com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    *///?}

    //? if <1.21.6 {
    /*private static void quads(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f pose, float[] xy,
            float @Nullable [] uv, int @Nullable [] colours, int quads) {
        for (int q = 0; q < quads; q++) {
            int argb = colours == null ? -1 : colours[q];
            int o = q * 8;
            for (int k = 0; k < 8; k += 2) {
                float u = uv != null ? uv[o + k] : (k == 4 || k == 6 ? 1f : 0f);
                float v = uv != null ? uv[o + k + 1] : (k == 2 || k == 4 ? 1f : 0f);
                vc.addVertex(pose, xy[o + k], xy[o + k + 1], 0f).setUv(u, v).setColor(argb);
            }
        }
    }
    *///?}
}
