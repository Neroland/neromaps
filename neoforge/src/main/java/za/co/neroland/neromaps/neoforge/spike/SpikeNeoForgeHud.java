package za.co.neroland.neromaps.neoforge.spike;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import za.co.neroland.neromaps.spike.SpikeMinimapHud;

/**
 * SPIKE (Stage 0 minimap draw path). Registers the debug minimap HUD layer on NeoForge's mod bus. Client only:
 * the entry point calls it behind a {@code Dist.CLIENT} check. Stage 2 replaces this with the real layer.
 */
public final class SpikeNeoForgeHud {

    private SpikeNeoForgeHud() {
    }

    public static void register(IEventBus modEventBus) {
        if (!SpikeMinimapHud.ENABLED) {
            return;
        }
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath("neromaps", "spike_minimap"), (g, delta) -> SpikeMinimapHud.render(g)));
    }
}
