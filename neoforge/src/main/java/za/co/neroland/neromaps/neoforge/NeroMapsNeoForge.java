package za.co.neroland.neromaps.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.command.MapCommands;
import za.co.neroland.neromaps.server.MapServer;

/** NeoForge entry point for NeroMaps. */
@Mod(NeroMapsCommon.MOD_ID)
public final class NeroMapsNeoForge {

    public NeroMapsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroMapsCommon.LOGGER.info("[NeroMaps] NeoForge bootstrap");
        NeroMapsCommon.init();
        // Common init created NeroMaps' deferred registers through Core's registration seam; this attaches them
        // to our mod event bus.
        RegistrationProvider.attach(modEventBus);
        NeoForgeMapNetwork.register(modEventBus);
        //? if >=1.20.5 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> MapServer.tick(event.getServer()));
        //?} else {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TickEvent.ServerTickEvent event) -> {
                    if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) {
                        MapServer.tick(event.getServer());
                    }
                });
        *///?}
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) -> MapCommands.register(event.getDispatcher()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> MapServer.stopping());
        // Client-only wiring (minimap, world map, keys); never loaded on a dedicated server.
        //? if >=1.21.9 {
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
        //?} else {
        /*if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
        *///?}
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}
