package za.co.neroland.neromaps.forge;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.ClientCommands;
import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.MapKeys;
import za.co.neroland.neromaps.client.render.MapTableRenderer;
import za.co.neroland.neromaps.client.render.WallMapRenderer;
import za.co.neroland.neromaps.client.screen.CartographyDeskScreen;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.registry.ModMenus;

/**
 * Forge client-only setup: key mappings, client tick, client-only commands and (when this Forge build has the
 * overlay event) the minimap HUD layer. Loaded only on the physical client.
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        za.co.neroland.neromaps.client.ClientState.install();
        //? if >=1.21.9 {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(ModMenus.CARTOGRAPHY_DESK.get(),
                        CartographyDeskScreen::new)));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.WALL_MAP.get(), ctx -> new WallMapRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MAP_TABLE.get(), ctx -> new MapTableRenderer());
        });
        RegisterKeyMappingsEvent.BUS.addListener(event -> MapKeys.all().forEach(event::register));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ClientMap.tick());
        RegisterClientCommandsEvent.BUS.addListener(event -> ClientCommands.register(event.getDispatcher()));
        //?} else if >=1.21.6 {
        /*FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(ModMenus.CARTOGRAPHY_DESK.get(),
                        CartographyDeskScreen::new)));
        EntityRenderersEvent.RegisterRenderers.getBus(context.getModBusGroup()).addListener(event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.WALL_MAP.get(), ctx -> new WallMapRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MAP_TABLE.get(), ctx -> new MapTableRenderer());
        });
        RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(event -> MapKeys.all().forEach(event::register));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ClientMap.tick());
        RegisterClientCommandsEvent.BUS.addListener(event -> ClientCommands.register(event.getDispatcher()));
        *///?} else {
        /*context.getModEventBus().addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(ModMenus.CARTOGRAPHY_DESK.get(),
                        CartographyDeskScreen::new)));
        context.getModEventBus().addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(ModBlockEntities.WALL_MAP.get(), ctx -> new WallMapRenderer());
            event.registerBlockEntityRenderer(ModBlockEntities.MAP_TABLE.get(), ctx -> new MapTableRenderer());
        });
        context.getModEventBus().addListener((RegisterKeyMappingsEvent event) -> MapKeys.all().forEach(event::register));
        //? if >=1.20.5 {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> ClientMap.tick());
        //?} else {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientMap.tick();
            }
        });
        //?}
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                ClientCommands.register(event.getDispatcher()));
        *///?}
        //? if >=1.20.5 {
        try {
            Class.forName(ForgeHudOverlays.EVENT_CLASS, false, ForgeClientSetup.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] This Forge build has no GUI overlay layer event; the minimap HUD is"
                    + " disabled (the world map still works). Update Forge to the latest build for your version.");
            return;
        }
        try {
            ForgeHudOverlays.register(context);
        } catch (LinkageError e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] Could not register the minimap HUD layer on this Forge build ({}).",
                    e.getClass().getSimpleName());
        }
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Post event) ->
                za.co.neroland.neromaps.client.hud.MinimapHud.render(event.getGuiGraphics()));
        *///?}
    }
}
