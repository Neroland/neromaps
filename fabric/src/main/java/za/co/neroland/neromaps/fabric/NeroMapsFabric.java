package za.co.neroland.neromaps.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.command.MapCommands;
import za.co.neroland.neromaps.server.MapServer;

/** Fabric entry point for NeroMaps: common init, the payload channel, the server tick, commands and shutdown. */
public final class NeroMapsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroMapsCommon.LOGGER.info("[NeroMaps] Fabric bootstrap");
        NeroMapsCommon.init();
        FabricMapNetwork.registerCommon();
        ServerTickEvents.END_SERVER_TICK.register(MapServer::tick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> MapCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MapServer.stopping());
    }
}
