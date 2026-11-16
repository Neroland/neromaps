package za.co.neroland.neromaps.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.neromaps.NeroMapsCommon;

/** Fabric entry point for NeroMaps. */
public final class NeroMapsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroMapsCommon.LOGGER.info("[NeroMaps] Fabric bootstrap");
        NeroMapsCommon.init();
    }
}
