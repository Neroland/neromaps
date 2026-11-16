package za.co.neroland.neromaps.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neromaps.NeroMapsCommon;

/** MinecraftForge entry point for NeroMaps. */
@Mod(NeroMapsCommon.MOD_ID)
public final class NeroMapsForge {

    public NeroMapsForge(FMLJavaModLoadingContext context) {
        NeroMapsCommon.LOGGER.info("[NeroMaps] Forge bootstrap");
        NeroMapsCommon.init();
        // Common init created NeroMaps' deferred registers through Core's registration seam; this attaches them
        // to our mod bus group.
        //? if >=1.21.6 {
        RegistrationProvider.attach(context.getModBusGroup());
        //?} else {
        /*RegistrationProvider.attach(context.getModEventBus());
        *///?}
    }
}
