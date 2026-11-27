package za.co.neroland.neromaps.neoforge;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
//? if >=1.20.5 {
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?}
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=1.20.4 {
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//?}
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//? if >=1.20.5 {
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
//?}
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.ClientCommands;
import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ColourTables;
import za.co.neroland.neromaps.client.MapKeys;
import za.co.neroland.neromaps.client.hud.MinimapHud;

/**
 * NeoForge client-only setup: minimap HUD layer, key mappings, client tick and the client-only commands. Loaded
 * only on the physical client (the entry point gates on {@code Dist.CLIENT}).
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        za.co.neroland.neromaps.client.ClientState.install();
        //? if >=1.20.5 {
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "minimap"), (g, delta) -> MinimapHud.render(g)));
        //?} else {
        /*modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent event) ->
                event.registerAboveAll(Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "minimap"),
                        (gui, graphics, delta, width, height) -> MinimapHud.render(graphics)));
        *///?}
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> MapKeys.all().forEach(event::register));
        //? if >=1.20.4 {
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(
                za.co.neroland.neromaps.registry.ModMenus.CARTOGRAPHY_DESK.get(),
                za.co.neroland.neromaps.client.screen.CartographyDeskScreen::new));
        //?} else {
        /*modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                        za.co.neroland.neromaps.registry.ModMenus.CARTOGRAPHY_DESK.get(),
                        za.co.neroland.neromaps.client.screen.CartographyDeskScreen::new)));
        *///?}
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(za.co.neroland.neromaps.registry.ModBlockEntities.WALL_MAP.get(),
                    ctx -> new za.co.neroland.neromaps.client.render.WallMapRenderer());
            event.registerBlockEntityRenderer(za.co.neroland.neromaps.registry.ModBlockEntities.MAP_TABLE.get(),
                    ctx -> new za.co.neroland.neromaps.client.render.MapTableRenderer());
        });
        // Register the colour table with the resource reload during startup. NeoForge freezes the listener list
        // after loading, so registering it lazily from the first client tick throws (MC-NEROMAPS-1).
        ClientMap.colourTablesRegisteredByLoader = true;
        //? if >=1.21.4 {
        modEventBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) ->
                event.addListener(Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "colour_tables"),
                        ColourTables.INSTANCE));
        //?} else {
        /*modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener(ColourTables.INSTANCE));
        *///?}
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientMap.tick());
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TickEvent.ClientTickEvent event) -> {
            if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) {
                ClientMap.tick();
            }
        });
        *///?}
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                ClientCommands.register(event.getDispatcher()));
    }
}
