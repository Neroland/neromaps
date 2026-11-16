package za.co.neroland.neromaps.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.neromaps.NeroMapsCommon;

/** Fabric client entry point for NeroMaps. Client-only wiring (minimap HUD, key bindings) lands here later. */
public final class NeroMapsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroMapsCommon.LOGGER.debug("[NeroMaps] Fabric client bootstrap");
        // SPIKE (Stage 0 minimap draw path): debug HUD layer, off unless -Dneromaps.spike.minimap=true. Stage 2 removes.
        za.co.neroland.neromaps.fabric.spike.SpikeFabricHud.register();
    }
}
