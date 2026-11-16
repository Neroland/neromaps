package za.co.neroland.neromaps.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * What a wall-map panel or a map table draws this frame: up to {@value #MAX} textured rectangles on its face, each
 * a part of one server tile. Face coordinates run 0..1 from the viewer's top-left.
 */
public class MapSurfaceRenderState extends BlockEntityRenderState {

    public static final int MAX = 4;

    public int quarterTurns;
    public boolean table;
    public int draws;
    public final Identifier[] texture = new Identifier[MAX];
    /** Per draw: h0, h1, v0, v1 (face), u0, u1, t0, t1 (texture). */
    public final float[] geo = new float[MAX * 8];
}
