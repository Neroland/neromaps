package za.co.neroland.neromaps.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neromaps.NeroMapsCommon;

/** NeoForge entry point for NeroMaps. */
@Mod(NeroMapsCommon.MOD_ID)
public final class NeroMapsNeoForge {

    public NeroMapsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroMapsCommon.LOGGER.info("[NeroMaps] NeoForge bootstrap");
        NeroMapsCommon.init();
        // Common init created NeroMaps' deferred registers through Core's registration seam; this attaches them
        // to our mod event bus.
        RegistrationProvider.attach(modEventBus);
        // SPIKE (Stage 0 minimap draw path): debug HUD layer, client only, off unless -Dneromaps.spike.minimap=true.
        // Stage 2 replaces this block with the real client setup.
        //? if >=1.21.9 {
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
        //?} else {
        /*if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
        *///?}
            za.co.neroland.neromaps.neoforge.spike.SpikeNeoForgeHud.register(modEventBus);
        }
    }
}
