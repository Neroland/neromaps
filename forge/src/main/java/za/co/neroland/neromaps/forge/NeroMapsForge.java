package za.co.neroland.neromaps.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.command.MapCommands;
import za.co.neroland.neromaps.server.MapServer;

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
        ForgeMapNetwork.register();
        //? if >=1.21.9 {
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(event -> MapServer.tick(event.server()));
        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(event -> MapCommands.register(event.getDispatcher()));
        net.minecraftforge.event.server.ServerStoppingEvent.BUS.addListener(event -> MapServer.stopping());
        //?} else if >=1.21.6 {
        /*net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(event -> MapServer.tick(event.getServer()));
        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(event -> MapCommands.register(event.getDispatcher()));
        net.minecraftforge.event.server.ServerStoppingEvent.BUS.addListener(event -> MapServer.stopping());
        *///?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) -> MapServer.tick(event.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent event) -> MapCommands.register(event.getDispatcher()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppingEvent event) -> MapServer.stopping());
        *///?}
        // Client-only wiring (minimap, world map, keys); never loaded on a dedicated server.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            ForgeClientSetup.init(context);
        }
    }
}
