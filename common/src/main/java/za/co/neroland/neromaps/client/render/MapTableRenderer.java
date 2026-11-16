package za.co.neroland.neromaps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.block.SimpleTickingBlockEntity;

/**
 * The Map Table's flat map of its surroundings (decision 013's fallback for the holographic relief), centred on the
 * table, {@code span} blocks across, drawn in the glass top inside x/z 1..15. Fog-checked server tiles only.
 *
 * <p><b>Culling — never blanket true:</b> within {@value #RANGE} blocks and only when the camera is above the top;
 * NeoForge also frustum-tests {@link #getRenderBoundingBox}. CLIENT-ONLY.
 */
public class MapTableRenderer implements BlockEntityRenderer<SimpleTickingBlockEntity, MapSurfaceRenderState> {

    public static final int RANGE = 32;
    private static final float INSET = 1f / 16f;

    @Override
    public MapSurfaceRenderState createRenderState() {
        return new MapSurfaceRenderState();
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }

    /** NeoForge frustum hook (IBlockEntityRendererExtension); inert on Fabric/Forge, so no {@code @Override}. */
    public AABB getRenderBoundingBox(SimpleTickingBlockEntity be) {
        return new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRender(SimpleTickingBlockEntity be, Vec3 cameraPos) {
        BlockPos pos = be.getBlockPos();
        double dx = cameraPos.x - (pos.getX() + 0.5);
        double dy = cameraPos.y - (pos.getY() + MapSurfaces.TABLE_Y);
        double dz = cameraPos.z - (pos.getZ() + 0.5);
        return dy > 0 && dx * dx + dy * dy + dz * dz <= (double) RANGE * RANGE;
    }

    @Override
    public void extractRenderState(SimpleTickingBlockEntity be, MapSurfaceRenderState s, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, breakProgress);
        s.draws = 0;
        s.table = true;
        if (be.getLevel() == null) {
            return;
        }
        WallPrint p = new WallPrint(be.getLevel().dimension().identifier().toString(), be.getBlockPos().getX(),
                be.getBlockPos().getZ(), be.span());
        MapSurfaces.fill(s, p.dimension(), p.lod(), p.panelMinX(0, 1), p.panelMinZ(0, 1), p.span(), INSET,
                1f - INSET, INSET, 1f - INSET);
    }

    @Override
    public void submit(MapSurfaceRenderState s, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        MapSurfaces.submit(s, poseStack, collector);
    }
}
