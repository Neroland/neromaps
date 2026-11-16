package za.co.neroland.neromaps.neoforge;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.ClientCommands;
import za.co.neroland.neromaps.client.ClientMap;
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
        modEventBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "minimap"), (g, delta) -> MinimapHud.render(g)));
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> MapKeys.all().forEach(event::register));
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(
                za.co.neroland.neromaps.registry.ModMenus.CARTOGRAPHY_DESK.get(),
                za.co.neroland.neromaps.client.screen.CartographyDeskScreen::new));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(za.co.neroland.neromaps.registry.ModBlockEntities.WALL_MAP.get(),
                    ctx -> new za.co.neroland.neromaps.client.render.WallMapRenderer());
            event.registerBlockEntityRenderer(za.co.neroland.neromaps.registry.ModBlockEntities.MAP_TABLE.get(),
                    ctx -> new za.co.neroland.neromaps.client.render.MapTableRenderer());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientMap.tick());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                ClientCommands.register(event.getDispatcher()));
    }
}
