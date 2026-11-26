package za.co.neroland.neromaps.forge;

//? if >=1.20.5 {
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.client.hud.MinimapHud;

/** Only this class names the overlay event, so the client setup links on every Forge build. */
final class ForgeHudOverlays {

    static final String EVENT_CLASS = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";

    private ForgeHudOverlays() {
    }

    static void register(FMLJavaModLoadingContext context) {
        Identifier id = Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "minimap");
        //? if >=1.21.9 {
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(id, (g, delta) -> MinimapHud.render(g)));
        //?} else if >=1.21.6 {
        /*AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(event -> event.getLayeredDraw().add(id,
                (g, delta) -> MinimapHud.render(g)));
        *///?} else {
        /*context.getModEventBus().addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(id,
                (g, delta) -> MinimapHud.render(g)));
        *///?}
    }
}
//?}
