package za.co.neroland.neromaps.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.ClientCommands;
import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.MapKeys;
import za.co.neroland.neromaps.client.hud.MinimapHud;
import za.co.neroland.neromaps.client.render.MapTableRenderer;
import za.co.neroland.neromaps.client.render.WallMapRenderer;
import za.co.neroland.neromaps.client.screen.CartographyDeskScreen;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.registry.ModMenus;

/** Fabric client entry point: minimap HUD element, key mappings, client tick and the client-only commands. */
public final class NeroMapsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroMapsCommon.LOGGER.debug("[NeroMaps] Fabric client bootstrap");
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "minimap"),
                (graphics, delta) -> MinimapHud.render(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, delta) -> MinimapHud.render(graphics));
        *///?}
        //? if >=26.1 {
        MapKeys.all().forEach(KeyMappingHelper::registerKeyMapping);
        //?} else {
        /*MapKeys.all().forEach(KeyBindingHelper::registerKeyBinding);
        *///?}
        net.minecraft.client.gui.screens.MenuScreens.register(ModMenus.CARTOGRAPHY_DESK.get(), CartographyDeskScreen::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.WALL_MAP.get(), ctx -> new WallMapRenderer());
        BlockEntityRendererRegistry.register(ModBlockEntities.MAP_TABLE.get(), ctx -> new MapTableRenderer());
        // Chunk layers: 26.x picks the layer per quad from the texture alpha; Fabric 1.21.x reads no model
        // render_type, so the glow-layered models go to the cutout layer here.
        //? if >=26.1 {
        //?} else if >=1.21.6 {
        /*for (net.minecraft.world.level.block.Block b : java.util.List.of(za.co.neroland.neromaps.registry.ModBlocks.CARTOGRAPHY_DESK.get(),
                za.co.neroland.neromaps.registry.ModBlocks.WALL_MAP.get(), za.co.neroland.neromaps.registry.ModBlocks.SIGNPOST.get(), za.co.neroland.neromaps.registry.ModBlocks.MAP_TABLE.get(),
                za.co.neroland.neromaps.registry.ModBlocks.TRAIL_MARKER.get())) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(b,
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
        }
        *///?} else {
        /*for (net.minecraft.world.level.block.Block b : java.util.List.of(za.co.neroland.neromaps.registry.ModBlocks.CARTOGRAPHY_DESK.get(),
                za.co.neroland.neromaps.registry.ModBlocks.WALL_MAP.get(), za.co.neroland.neromaps.registry.ModBlocks.SIGNPOST.get(), za.co.neroland.neromaps.registry.ModBlocks.MAP_TABLE.get(),
                za.co.neroland.neromaps.registry.ModBlocks.TRAIL_MARKER.get())) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(b,
                    net.minecraft.client.renderer.RenderType.cutout());
        }
        *///?}
        FabricMapNetwork.registerClient();
        za.co.neroland.neromaps.client.ClientState.install();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientMap.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                ClientCommands.register(dispatcher));
    }
}
