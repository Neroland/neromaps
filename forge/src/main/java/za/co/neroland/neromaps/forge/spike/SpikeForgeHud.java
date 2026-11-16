package za.co.neroland.neromaps.forge.spike;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.spike.SpikeMinimapHud;

/**
 * SPIKE (Stage 0 minimap draw path). Registers the debug minimap HUD layer on Forge. {@code AddGuiOverlayLayersEvent}
 * is missing on some Forge builds, so the event class is checked before this class links it (the NeroArcade
 * pattern). Client only. Stage 2 replaces this with the real layer.
 */
public final class SpikeForgeHud {

    private static final String EVENT_CLASS = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";

    private SpikeForgeHud() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        if (!SpikeMinimapHud.ENABLED) {
            return;
        }
        try {
            Class.forName(EVENT_CLASS, false, SpikeForgeHud.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] This Forge build has no GUI overlay layer event; spike HUD off.");
            return;
        }
        Linked.register(context);
    }

    /** Only this nested class names the event, so the outer class links on every Forge build. */
    private static final class Linked {

        private Linked() {
        }

        static void register(FMLJavaModLoadingContext context) {
            Identifier id = Identifier.fromNamespaceAndPath("neromaps", "spike_minimap");
            //? if >=1.21.9 {
            AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(id,
                    (g, delta) -> SpikeMinimapHud.render(g)));
            //?} else if >=1.21.6 {
            /*AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(event -> event.getLayeredDraw().add(id,
                    (g, delta) -> SpikeMinimapHud.render(g)));
            *///?} else {
            /*context.getModEventBus().addListener((AddGuiOverlayLayersEvent event) -> event.getLayeredDraw().add(id,
                    (g, delta) -> SpikeMinimapHud.render(g)));
            *///?}
        }
    }
}
