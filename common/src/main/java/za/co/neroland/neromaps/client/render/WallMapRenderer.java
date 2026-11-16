package za.co.neroland.neromaps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.block.MapBlockProperties;
import za.co.neroland.neromaps.block.WallMapBlockEntity;

/**
 * Draws one wall-map panel's share of its array's printed area over the panel glass, inset 2 px on framed sides and
 * flush on joined sides. The terrain comes only from fog-checked server tiles.
 *
 * <p><b>Culling — never blanket true:</b> only within {@value #RANGE} blocks of the panel and only when the camera is
 * in front of it; NeoForge also frustum-tests {@link #getRenderBoundingBox}. CLIENT-ONLY.
 */
public class WallMapRenderer implements BlockEntityRenderer<WallMapBlockEntity, MapSurfaceRenderState> {

    public static final int RANGE = 48;
    private static final float INSET = 2f / 16f;

    @Override
    public MapSurfaceRenderState createRenderState() {
        return new MapSurfaceRenderState();
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(WallMapBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(WallMapBlockEntity be, Vec3 cameraPos) {
        BlockState state = be.getBlockState();
        if (be.print() == null || !state.hasProperty(MapBlockProperties.FACING)) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        double dx = cameraPos.x - (pos.getX() + 0.5);
        double dy = cameraPos.y - (pos.getY() + 0.5);
        double dz = cameraPos.z - (pos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > (double) RANGE * RANGE) {
            return false;
        }
        Direction facing = state.getValue(MapBlockProperties.FACING);
        return dx * facing.getStepX() + dz * facing.getStepZ() > -0.4;
    }

    @Override
    public void extractRenderState(WallMapBlockEntity be, MapSurfaceRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        s.draws = 0;
        s.table = false;
        WallPrint p = be.print();
        BlockState state = be.getBlockState();
        if (p == null || !state.hasProperty(MapBlockProperties.FACING)) {
            return;
        }
        s.quarterTurns = MapSurfaces.quarterTurns(state.getValue(MapBlockProperties.FACING));
        int mask = state.getValue(MapBlockProperties.MASK);
        float h0 = (mask & 8) != 0 ? 0f : INSET;
        float h1 = (mask & 2) != 0 ? 1f : 1f - INSET;
        float v0 = (mask & 1) != 0 ? 0f : INSET;
        float v1 = (mask & 4) != 0 ? 1f : 1f - INSET;
        MapSurfaces.fill(s, p.dimension(), p.lod(), p.panelMinX(be.col(), be.cols()), p.panelMinZ(be.row(), be.rows()),
                p.span(), h0, h1, v0, v1);
    }

    @Override
    public void submit(MapSurfaceRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        MapSurfaces.submit(s, poseStack, collector);
    }
}
