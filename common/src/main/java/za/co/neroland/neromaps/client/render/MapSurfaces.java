package za.co.neroland.neromaps.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import za.co.neroland.neromaps.client.ColourTables;
import za.co.neroland.neromaps.client.WallTiles;
import za.co.neroland.neromaps.client.texture.MapTexture;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * Shared drawing for the wall map and the map table: works out which server tiles cover a block's world rectangle,
 * asks {@link WallTiles} for any it lacks, and turns the tiles it has into textured rectangles. Only fog-checked
 * tiles ever reach {@link WallTiles}, so nothing outside the viewer's own fog of war can be drawn. CLIENT-ONLY.
 */
final class MapSurfaces {

    static final int FULL_BRIGHT = 0x00F000F0;
    /** The map plane of a north-facing wall panel (glass at z 15, 0.02 in front of it). */
    static final float WALL_Z = 15f / 16f - 0.02f;
    /** The map plane of the table (its top at y 11, just above). */
    static final float TABLE_Y = 11f / 16f + 0.01f;

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    private MapSurfaces() {
    }

    static RenderType type(Identifier id) {
        RenderType t = TYPES.get(id);
        if (t == null) {
            if (TYPES.size() > 64) {
                TYPES.clear();
            }
            t = RenderTypes.entityTranslucentEmissive(id);
            TYPES.put(id, t);
        }
        return t;
    }

    static int quarterTurns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /**
     * Fills {@code s} for the world rectangle {@code [wx0, wx0+span) x [wz0, wz0+span)} shown on the face area
     * {@code [h0, h1] x [v0, v1]} (which maps linearly onto the whole rectangle {@code [0,1]}).
     */
    static void fill(MapSurfaceRenderState s, String dim, int lod, int wx0, int wz0, int span, float h0, float h1,
            float v0, float v1) {
        s.draws = 0;
        int tileBlocks = Tile.SIZE << lod;
        // the visible world rectangle
        double vx0 = wx0 + h0 * span;
        double vx1 = wx0 + h1 * span;
        double vz0 = wz0 + v0 * span;
        double vz1 = wz0 + v1 * span;
        int tx0 = Math.floorDiv((int) Math.floor(vx0), tileBlocks);
        int tx1 = Math.floorDiv((int) Math.ceil(vx1) - 1, tileBlocks);
        int tz0 = Math.floorDiv((int) Math.floor(vz0), tileBlocks);
        int tz1 = Math.floorDiv((int) Math.ceil(vz1) - 1, tileBlocks);
        for (int tz = tz0; tz <= tz1; tz++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                Tile t = WallTiles.get(dim, lod, tx, tz);
                WallTiles.want(dim, lod, tx, tz);
                if (t == null || s.draws >= MapSurfaceRenderState.MAX) {
                    continue;
                }
                MapTexture mt = WallTiles.textures().texture(TileKey.tile(lod, tx, tz), t, WallTiles.lookup(dim),
                        ColourTables.current(), WallTiles.raster(), WallTiles.raster().version);
                if (mt == null) {
                    continue;
                }
                double ax0 = Math.max(vx0, (double) tx * tileBlocks);
                double ax1 = Math.min(vx1, (double) (tx + 1) * tileBlocks);
                double az0 = Math.max(vz0, (double) tz * tileBlocks);
                double az1 = Math.min(vz1, (double) (tz + 1) * tileBlocks);
                if (ax1 <= ax0 || az1 <= az0) {
                    continue;
                }
                int o = s.draws * 8;
                s.texture[s.draws] = mt.id;
                s.geo[o] = (float) ((ax0 - wx0) / span);
                s.geo[o + 1] = (float) ((ax1 - wx0) / span);
                s.geo[o + 2] = (float) ((az0 - wz0) / span);
                s.geo[o + 3] = (float) ((az1 - wz0) / span);
                s.geo[o + 4] = (float) ((ax0 - (double) tx * tileBlocks) / tileBlocks);
                s.geo[o + 5] = (float) ((ax1 - (double) tx * tileBlocks) / tileBlocks);
                s.geo[o + 6] = (float) ((az0 - (double) tz * tileBlocks) / tileBlocks);
                s.geo[o + 7] = (float) ((az1 - (double) tz * tileBlocks) / tileBlocks);
                s.draws++;
            }
        }
    }

    static void submit(MapSurfaceRenderState s, PoseStack poseStack, SubmitNodeCollector collector) {
        for (int i = 0; i < s.draws; i++) {
            Identifier tex = s.texture[i];
            if (tex == null) {
                continue;
            }
            float[] g = new float[8];
            System.arraycopy(s.geo, i * 8, g, 0, 8);
            boolean table = s.table;
            int q = s.quarterTurns;
            collector.order(1).submitCustomGeometry(poseStack, type(tex), (pose, c) -> quad(c, pose, table, q, g));
        }
    }

    /** One double-sided rectangle on the face. */
    private static void quad(VertexConsumer c, PoseStack.Pose pose, boolean table, int q, float[] g) {
        float[][] p = {
            point(table, q, g[0], g[2]), point(table, q, g[1], g[2]), point(table, q, g[1], g[3]),
            point(table, q, g[0], g[3])
        };
        float[] uv = {g[4], g[6], g[5], g[6], g[5], g[7], g[4], g[7]};
        float[] n = normal(table, q);
        for (int i = 0; i < 4; i++) {
            vertex(c, pose, p[i], uv[i * 2], uv[i * 2 + 1], n);
        }
        float[] m = {-n[0], -n[1], -n[2]};
        for (int i = 3; i >= 0; i--) {
            vertex(c, pose, p[i], uv[i * 2], uv[i * 2 + 1], m);
        }
    }

    /** Face point {@code (h, v)} (0..1 from the viewer's top-left) in block space. */
    static float[] point(boolean table, int q, float h, float v) {
        if (table) {
            return new float[] {h, TABLE_Y, v};
        }
        float x = 1f - h;
        float y = 1f - v;
        float z = WALL_Z;
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {1f - z, y, x};
            case 2 -> new float[] {1f - x, y, 1f - z};
            case 3 -> new float[] {z, y, 1f - x};
            default -> new float[] {x, y, z};
        };
    }

    private static float[] normal(boolean table, int q) {
        if (table) {
            return new float[] {0f, 1f, 0f};
        }
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new float[] {1f, 0f, 0f};
            case 2 -> new float[] {0f, 0f, 1f};
            case 3 -> new float[] {-1f, 0f, 0f};
            default -> new float[] {0f, 0f, -1f};
        };
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float[] p, float u, float v, float[] n) {
        //? if >=1.21 {
        c.addVertex(pose, p[0], p[1], p[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, n[0], n[1], n[2]);
        //?} else {
        /*c.vertex(pose, p[0], p[1], p[2])
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(pose, n[0], n[1], n[2])
                .endVertex();
        *///?}
    }
}
