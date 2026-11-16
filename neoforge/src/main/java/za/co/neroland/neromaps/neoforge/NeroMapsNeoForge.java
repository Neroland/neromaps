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
    }
}
