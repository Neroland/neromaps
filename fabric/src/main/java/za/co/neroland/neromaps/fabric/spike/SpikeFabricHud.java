package za.co.neroland.neromaps.fabric.spike;

//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}

import za.co.neroland.neromaps.spike.SpikeMinimapHud;

/**
 * SPIKE (Stage 0 minimap draw path). Registers the debug minimap HUD layer on Fabric. Stage 2 replaces this with
 * the real minimap layer; delete the class and its one call in {@code NeroMapsFabricClient} then.
 */
public final class SpikeFabricHud {

    private SpikeFabricHud() {
    }

    public static void register() {
        if (!SpikeMinimapHud.ENABLED) {
            return;
        }
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("neromaps", "spike_minimap"),
                (graphics, delta) -> SpikeMinimapHud.render(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, delta) -> SpikeMinimapHud.render(graphics));
        *///?}
    }
}
