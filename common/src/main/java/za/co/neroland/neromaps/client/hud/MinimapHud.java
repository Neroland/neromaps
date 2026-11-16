package za.co.neroland.neromaps.client.hud;

//? if >=26.1 {
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
//?} else if >=1.21.6 {
/*import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiRenderState;
*///?}
import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ColourTables;
import za.co.neroland.neromaps.client.Readouts;
import za.co.neroland.neromaps.client.texture.MapTexture;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.colour.MapRasterizer;
import za.co.neroland.neromaps.colour.MapStyle;
import za.co.neroland.neromaps.colour.RasterOptions;
import za.co.neroland.neromaps.colour.TileLookup;
import za.co.neroland.neromaps.engine.Clipmap;
import za.co.neroland.neromaps.engine.MapStore;
import za.co.neroland.neromaps.engine.MinimapFrame;
import za.co.neroland.neromaps.engine.MinimapLayout;
import za.co.neroland.neromaps.engine.Sprites;
import za.co.neroland.neromaps.perf.AllocationMeter;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * The minimap HUD layer each loader registers. Per frame: at most one upload batch (nearest dirty chunk patches,
 * up to the upload budget), the preallocated CPU path in {@link MinimapFrame}, then one surface batch and one
 * overlay batch. NeroMaps code allocates nothing here in steady state (decision 004 B1); the readout text and the
 * perf overlay are drawn by vanilla's text renderer.
 *
 * <p>Draw path per line (decision 005): 26.x and 1.21.6 - 1.21.11 submit two reusable {@link MinimapGuiState}s
 * (the latter through the accessor mixin, falling back to a square blit if the mixin is missing); 1.21.2 - 1.21.5
 * stream into {@code drawSpecial}'s buffer source; 1.21.1 draws immediately through the {@code Tesselator}.
 */
public final class MinimapHud {

    /** Visible span (blocks) above which the clipmap grows from 256 to 512. */
    public static final int SPAN_512 = 224;

    private static final MinimapFrame FRAME = new MinimapFrame();
    private static final MinimapLayout LAYOUT = new MinimapLayout();
    private static final RasterOptions RASTER = new RasterOptions();
    private static final int[] PATCH = new int[256];
    private static final Revisions REVISIONS = new Revisions();
    private static @Nullable Clipmap clipmap;
    private static @Nullable MapTexture terrain;
    private static @Nullable MapTexture sprites;
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ;
    private static int lastRasterVersion = -1;
    private static long lastTick = -1;

    //? if >=1.21.6 {
    private static final MinimapGuiState SURFACE = new MinimapGuiState();
    private static final MinimapGuiState OVERLAY = new MinimapGuiState();
    //?}

    private MinimapHud() {
    }

    /** Forces a full refill (dimension, cave slice or table change). */
    public static void reset() {
        Clipmap c = clipmap;
        if (c != null) {
            c.invalidate();
        }
        lastChunkX = Integer.MIN_VALUE;
    }

    /** Chunks across the current window. */
    public static int windowChunks() {
        Clipmap c = clipmap;
        return c == null ? 16 : c.slots;
    }

    private static boolean previewing;
    private static int previewX;
    private static int previewY;

    /**
     * The settings screen's live preview: the real minimap, with every current setting, drawn with its frame's
     * top-left at {@code (x, y)} instead of its anchor. Render thread only.
     */
    public static void preview(GuiGraphicsExtractor g, int x, int y) {
        previewing = true;
        previewX = x;
        previewY = y;
        try {
            render(g);
        } finally {
            previewing = false;
        }
    }

    /** HUD layer body. Render thread only. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!previewing) {
            WaypointHud.render(g);
            if (ClientMap.screen(mc) instanceof za.co.neroland.neromaps.client.screen.MapSettingsScreen) {
                return; // the settings screen draws the minimap itself, in its preview well
            }
        }
        LocalPlayer player = mc.player;
        MinimapSettings s = ClientMap.SETTINGS;
        MapStore store = ClientMap.store();
        if (!s.enabled || player == null || store == null || mc.level == null
                || !previewing && ClientMap.hudHidden(mc)) {
            return;
        }
        boolean measure = PerfOverlay.enabled;
        long a0 = measure ? AllocationMeter.current() : 0;
        long t0 = System.nanoTime();

        int window = s.size / s.zoom > SPAN_512 ? 512 : 256;
        Clipmap clip = clipmap;
        MapTexture tex = terrain;
        if (clip == null || tex == null || clip.size != window) {
            if (tex != null) {
                tex.close();
            }
            clip = new Clipmap(window);
            tex = new MapTexture("minimap_" + window, window);
            clipmap = clip;
            terrain = tex;
            lastChunkX = Integer.MIN_VALUE;
        }
        MapTexture spr = sprites;
        if (spr == null) {
            spr = new MapTexture("sprites", Sprites.SIZE);
            sprites = spr;
            spriteKey = -1;
        }
        boolean hcFrame = s.highContrast || s.style == MapStyle.HIGH_CONTRAST;
        int frameKey = s.shape.ordinal() | MinimapFrame.snapRound(s.size) << 2 | s.border.ordinal() << 12
                | (hcFrame ? 1 << 16 : 0) | ColourTables.current().version() << 17;
        if (frameKey != spriteKey) {
            // only when the shape, round size, border or resources change: Fable's frame art for this frame
            spr.uploadAll(spritePixels(mc, s.shape == MinimapSettings.Shape.ROUND, MinimapFrame.snapRound(s.size),
                    s.border, hcFrame));
            spriteKey = frameKey;
        }
        boolean overlays = ClientMap.overlaysAllowed();
        RASTER.set(s.effectiveStyle(), s.chunkBorders && overlays, s.grid && overlays, 63);
        int rasterVersion = RASTER.version * 31 + ColourTables.current().version();
        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        boolean cave = ClientMap.caveActive();
        long tick = mc.level.getGameTime();
        if (pcx != lastChunkX || pcz != lastChunkZ || rasterVersion != lastRasterVersion || tick != lastTick) {
            REVISIONS.store = store;
            REVISIONS.cave = cave;
            clip.retarget(pcx, pcz, REVISIONS, rasterVersion);
            lastChunkX = pcx;
            lastChunkZ = pcz;
            lastRasterVersion = rasterVersion;
            lastTick = tick;
        }
        // the frame's single upload batch
        int uploads = 0;
        TileLookup lookup = cave ? store.caveLookup : store;
        while (uploads < s.uploadBudget) {
            long key = clip.pollNearest();
            if (key == Long.MIN_VALUE) {
                break;
            }
            int cx = TileKey.chunkX(key);
            int cz = TileKey.chunkZ(key);
            Tile t = lookup.get(0, cx >> 4, cz >> 4);
            MapRasterizer.chunk(t, cx >> 4, cz >> 4, cx & 15, cz & 15, lookup, ColourTables.current(), RASTER,
                    PATCH, 0, 16);
            tex.upload16(PATCH, 0, 16, clip.texelX(cx), clip.texelZ(cz));
            uploads++;
        }
        PerfCounters.recordUploads(uploads, uploads > 0 ? 1 : 0);

        int readouts = Readouts.count;
        boolean round = s.shape == MinimapSettings.Shape.ROUND;
        boolean hc = s.highContrast || s.style == MapStyle.HIGH_CONTRAST;
        int fw = MinimapFrame.frameWidth(s.border, hc);
        LAYOUT.compute(s, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                s.anchor == MinimapSettings.Anchor.TOP_RIGHT ? ClientMap.effectRows() : 0, readouts, fw);
        if (previewing) {
            int dx = previewX + LAYOUT.frame - LAYOUT.left;
            int dy = previewY + LAYOUT.frame - LAYOUT.top;
            LAYOUT.left += dx;
            LAYOUT.top += dy;
            LAYOUT.readoutX += dx;
            LAYOUT.readoutY += dy;
        }
        za.co.neroland.neromaps.client.ClientState st = za.co.neroland.neromaps.client.ClientState.INSTANCE;
        st.refreshMarkers(ClientMap.dimensionId(), s);
        FRAME.clearMarkers();
        for (int i = 0; i < st.markerCount; i++) {
            FRAME.addMarker(st.markerX[i], st.markerZ[i], st.markerColour[i]);
        }
        FRAME.update(LAYOUT, round, s.rotate, player.getYRot(), player.getX(), player.getZ(), s.zoom, window,
                s.border, hc);
        float dark = s.effectiveStyle() == MapStyle.DAY_NIGHT && s.nightDarkening && !ClientMap.roofed() && !cave
                ? MapRasterizer.darkness(Readouts.dayTime(mc.level)) : 0f;
        int alpha = Math.round(s.opacity * 255f) << 24;
        int surfaceColour = alpha | (MapRasterizer.nightTint(dark) & 0xFFFFFF);
        emit(g, mc, tex, spr, surfaceColour);
        if (measure) {
            long a1 = AllocationMeter.current();
            PerfCounters.recordHud(System.nanoTime() - t0, a0 < 0 ? -1 : a1 - a0);
        } else {
            PerfCounters.recordHud(System.nanoTime() - t0, 0);
        }
        if (readouts > 0) {
            drawReadouts(g, mc.font, readouts);
        }
        if (measure) {
            PerfOverlay.render(g, mc.font);
        }
    }

    private static void drawReadouts(GuiGraphicsExtractor g, Font font, int n) {
        int x = LAYOUT.readoutX - LAYOUT.frame;
        int y = LAYOUT.readoutY;
        int w = LAYOUT.size + LAYOUT.frame * 2;
        g.fill(x, y - 1, x + w, y + n * MinimapLayout.READOUT_LINE + 1, (MapPalette.PANEL & 0xFFFFFF) | 0xB3000000);
        for (int i = 0; i < n; i++) {
            g.text(font, Readouts.LINES[i], x + 2, y + 1 + i * MinimapLayout.READOUT_LINE, MapPalette.TEXT);
        }
    }

    /** Clipmap revisions: data revision per chunk (and its north/west neighbours) from the live store or cave slice. */
    private static final class Revisions implements Clipmap.Revisions {
        @Nullable MapStore store;
        boolean cave;

        @Override
        public int revision(int chunkX, int chunkZ) {
            MapStore s = store;
            if (s == null) {
                return -1;
            }
            // the north and west neighbours feed this chunk's edge hillshade, so their arrival refreshes it too
            return s.revision(chunkX, chunkZ, cave) * 31 + s.revision(chunkX, chunkZ - 1, cave) * 7
                    + s.revision(chunkX - 1, chunkZ, cave);
        }
    }

    private static int spriteKey = -1;

    /** Reads a PNG from the resource packs as ARGB rows, or null when it is missing or larger than allowed. */
    private static int @Nullable [] png(Minecraft mc, String path, int[] size, int max) {
        try {
            Optional<Resource> r = mc.getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath("neromaps", "textures/gui/minimap/" + path + ".png"));
            if (r.isEmpty()) {
                return null;
            }
            try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth();
                int h = img.getHeight();
                if (w > max || h > max) {
                    return null;
                }
                int[] out = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        //? if >=1.21.2 {
                        out[y * w + x] = img.getPixel(x, y);
                        //?} else {
                        /*out[y * w + x] = za.co.neroland.neromaps.engine.RgbaStaging.toAbgr(img.getPixelRGBA(x, y));
                        *///?}
                    }
                }
                size[0] = w;
                size[1] = h;
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void blitInto(int[] sheet, int[] src, int w, int h, int ox, int oy) {
        for (int y = 0; y < h; y++) {
            System.arraycopy(src, y * w, sheet, (oy + y) * Sprites.SIZE + ox, w);
        }
    }

    /**
     * The sprite sheet for the current frame: the procedural corner, Fable's player arrow, and Fable's frame art
     * (square nine-slice or the round bezel and tick ring for this size, Nero / minimal / high-contrast, and the north
     * tab). Whatever is missing leaves its {@link MinimapFrame.FrameArt} flag off and the geometry frame draws.
     */
    private static int[] spritePixels(Minecraft mc, boolean round, int roundSize, MinimapSettings.Border border,
            boolean hc) {
        int[] p = spritePixels(mc);
        MinimapFrame.FrameArt art = FRAME.art;
        art.clear();
        if (border == MinimapSettings.Border.NONE && !hc) {
            return p;
        }
        String style = hc ? "contrast" : border == MinimapSettings.Border.NERO ? "nero" : "minimal";
        int[] size = new int[2];
        if (round) {
            int[] bezel = png(mc, "frame_round_" + style + "_" + roundSize, size, 166);
            if (bezel != null && size[0] == size[1]) {
                blitInto(p, bezel, size[0], size[1], Sprites.BEZEL_X, Sprites.BEZEL_Y);
                art.round = true;
                art.roundSide = size[0];
                if (!hc && border == MinimapSettings.Border.NERO) {
                    int[] ticks = png(mc, "ticks_round_" + roundSize, size, 166);
                    if (ticks != null && size[0] == art.roundSide && size[1] == art.roundSide) {
                        blitInto(p, ticks, size[0], size[1], Sprites.TICKS_X, Sprites.TICKS_Y);
                        art.ticks = true;
                    }
                }
            }
        } else {
            int[] sq = png(mc, "frame_square_" + style, size, 32);
            if (sq != null && size[0] == size[1]) {
                blitInto(p, sq, size[0], size[1], Sprites.SQUARE_X, Sprites.SQUARE_Y);
                art.square = true;
                art.squareSide = size[0];
                art.squareBorder = size[0] >= 32 ? 6 : 2;
            }
        }
        int[] tab = png(mc, "north_tab", size, 9);
        if (tab != null) {
            blitInto(p, tab, size[0], size[1], Sprites.NORTH_X, Sprites.NORTH_Y);
            art.north = true;
        }
        return p;
    }

    /** The procedural sprite sheet with Fable's player arrow composited in when the texture is present. */
    private static int[] spritePixels(Minecraft mc) {
        int[] p = Sprites.pixels();
        try {
            Optional<Resource> r = mc.getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath("neromaps", "textures/gui/minimap/player_arrow.png"));
            if (r.isPresent()) {
                try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                    if (img.getWidth() <= 9 && img.getHeight() <= 9) {
                        int ox = 16 + (9 - img.getWidth()) / 2;
                        for (int y = 0; y < 9; y++) {
                            for (int x = 16; x < 25; x++) {
                                p[y * Sprites.SIZE + x] = 0;
                            }
                        }
                        for (int y = 0; y < img.getHeight(); y++) {
                            for (int x = 0; x < img.getWidth(); x++) {
                                //? if >=1.21.2 {
                                int argb = img.getPixel(x, y);
                                //?} else {
                                /*int argb = za.co.neroland.neromaps.engine.RgbaStaging.toAbgr(img.getPixelRGBA(x, y));
                                *///?}
                                p[y * Sprites.SIZE + ox + x] = argb;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            NeroMapsCommon.LOGGER.debug("[NeroMaps] player arrow texture unavailable; using the built-in arrow");
        }
        return p;
    }

    private static void emit(GuiGraphicsExtractor g, Minecraft mc, MapTexture tex, MapTexture spr, int surfaceColour) {
        //? if >=26.2 {
        emitState(g, mc.gameRenderer.gameRenderState().guiRenderState, tex, spr, surfaceColour);
        //?} else if >=26.1 {
        /*emitState(g, mc.gameRenderer.getGameRenderState().guiRenderState, tex, spr, surfaceColour);
        *///?} else if >=1.21.6 {
        /*if (!stateFailed) {
            try {
                GuiRenderState state = ((za.co.neroland.neromaps.mixin.GuiGraphicsAccessor) (Object) g)
                        .neromaps$guiRenderState();
                emitState(g, state, tex, spr, surfaceColour);
                return;
            } catch (ClassCastException | LinkageError e) {
                stateFailed = true;
            }
        }
        emitBlit(g, tex);
        *///?} else if >=1.21.2 {
        /*emitDrawSpecial(g, tex, spr, surfaceColour);
        *///?} else {
        /*emitTesselator(g, tex, spr, surfaceColour);
        *///?}
    }

    //? if >=1.21.6 {
    private static void emitState(GuiGraphicsExtractor g, GuiRenderState state, MapTexture tex, MapTexture spr,
            int surfaceColour) {
        if (!SURFACE.hasTexture() || boundTo != tex) {
            boundTo = tex;
            SURFACE.texture(setup(tex));
            OVERLAY.texture(setup(spr));
        }
        var s = FRAME.surface;
        var b = FRAME.overlay;
        int x0 = LAYOUT.left - LAYOUT.frame - 4;
        int y0 = LAYOUT.top - LAYOUT.frame - 4;
        int x1 = LAYOUT.right() + LAYOUT.frame + 4;
        int y1 = LAYOUT.bottom() + LAYOUT.frame + 4;
        SURFACE.set(g.pose(), s.xy, s.uv, null, surfaceColour, s.quads, x0, y0, x1, y1);
        OVERLAY.set(g.pose(), b.xy, b.uv, b.colour, -1, b.quads, x0, y0, x1, y1);
        //? if >=26.1 {
        state.addGuiElement(SURFACE);
        if (b.quads > 0) {
            state.addGuiElement(OVERLAY);
        }
        //?} else {
        /*state.submitGuiElement(SURFACE);
        if (b.quads > 0) {
            state.submitGuiElement(OVERLAY);
        }
        *///?}
    }

    private static @Nullable MapTexture boundTo;

    private static TextureSetup setup(MapTexture t) {
        //? if >=1.21.11 {
        return TextureSetup.singleTexture(t.texture().getTextureView(), t.texture().getSampler());
        //?} else {
        /*return TextureSetup.singleTexture(t.texture().getTextureView());
        *///?}
    }
    //?}

    //? if >=1.21.6 && <26.1 {
    /*private static boolean fallbackLogged;
    private static boolean stateFailed;

    private static void emitBlit(GuiGraphicsExtractor g, MapTexture tex) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            NeroMapsCommon.LOGGER.warn("[NeroMaps] GUI accessor mixin unavailable: the minimap falls back to a square,"
                    + " north-locked blit on this Minecraft line (allocates per quad; decision 004 B1 exception)");
        }
        float[] uv = FRAME.surface.uv;
        g.blit(tex.id, LAYOUT.left, LAYOUT.top, LAYOUT.right(), LAYOUT.bottom(), uv[0], uv[4], uv[1], uv[5]);
    }
    *///?}

    //? if >=1.21.2 && <1.21.6 {
    /*private static org.joml.Matrix4f legacyPose = new org.joml.Matrix4f();
    private static MapTexture legacyTex;
    private static MapTexture legacySpr;
    private static int legacyColour;
    private static final java.util.function.Consumer<net.minecraft.client.renderer.MultiBufferSource> EMIT =
            MinimapHud::emitInto;

    private static void emitDrawSpecial(GuiGraphicsExtractor g, MapTexture tex, MapTexture spr, int colour) {
        legacyPose = g.pose().last().pose();
        legacyTex = tex;
        legacySpr = spr;
        legacyColour = colour;
        g.drawSpecial(EMIT);
    }

    private static void emitInto(net.minecraft.client.renderer.MultiBufferSource buffers) {
        var s = FRAME.surface;
        var b = FRAME.overlay;
        quads(buffers.getBuffer(net.minecraft.client.renderer.RenderType.guiTextured(legacyTex.id)), legacyPose,
                s.xy, s.uv, null, legacyColour, s.quads);
        quads(buffers.getBuffer(net.minecraft.client.renderer.RenderType.guiTextured(legacySpr.id)), legacyPose,
                b.xy, b.uv, b.colour, -1, b.quads);
    }
    *///?}

    //? if <1.21.2 {
    /*private static void emitTesselator(GuiGraphicsExtractor g, MapTexture tex, MapTexture spr, int colour) {
        org.joml.Matrix4f pose = g.pose().last().pose();
        var s = FRAME.surface;
        var b = FRAME.overlay;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex.id);
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        quads(bb, pose, s.xy, s.uv, null, colour, s.quads);
        com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
        if (b.quads > 0) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, spr.id);
            bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
            quads(bb, pose, b.xy, b.uv, b.colour, -1, b.quads);
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
            float[] uv, int @Nullable [] colours, int colour, int quads) {
        for (int q = 0; q < quads; q++) {
            int argb = colours == null ? colour : colours[q];
            int o = q * 8;
            for (int k = 0; k < 8; k += 2) {
                vc.addVertex(pose, xy[o + k], xy[o + k + 1], 0f).setUv(uv[o + k], uv[o + k + 1]).setColor(argb);
            }
        }
    }
    *///?}
}
