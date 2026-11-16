package za.co.neroland.neromaps.mixin;

//? if >=1.21.6 && <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 1.21.6 - 1.21.11 only (listed in resources-1.21.6/neromaps-gui.mixins.json, a non-required config): the frame's
// GUI render state is private there, and the minimap needs it to submit its two reusable render states instead of
// vanilla blits that allocate per quad (decision 005). If this fails to apply, MinimapHud falls back to blits.
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {

    @Accessor("guiRenderState")
    GuiRenderState neromaps$guiRenderState();
}
*///?} else {
// Only 1.21.6 - 1.21.11 need the accessor; on every other line this is an unused marker type.
public interface GuiGraphicsAccessor {
}
//?}
