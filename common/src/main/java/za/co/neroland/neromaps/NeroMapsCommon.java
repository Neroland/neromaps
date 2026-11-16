package za.co.neroland.neromaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.data.NeroMapsData;
import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.registry.ModCreativeTab;
import za.co.neroland.neromaps.telemetry.NeroMapsTelemetry;

/**
 * Loader-agnostic entry point for NeroMaps. Each loader entry point (Fabric / Forge / NeoForge) calls
 * {@link #init()} once during mod construction, then wires its own event hooks.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> (the moment a registry class is
 * touched), so anything that must exist before something else has to be listed before it, on every loader.
 * The numbered steps are the Neroland convention; later work fills the reserved slots rather than inserting
 * itself wherever is convenient.
 */
public final class NeroMapsCommon {

    public static final String MOD_ID = "neromaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroMaps");

    private NeroMapsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroMaps] common init");

        // 0. Platform seam (PlatformInfo), resolved here during construction and never lazily on a tick
        //    path: a late ServiceLoader read can throw ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroMapsConfig.init();

        // 2. Anonymous, NeroMaps-only crash reporting. Follows the config registration and precedes the rest
        //    of init so early failures are still reported. On by default (opt-out via telemetryEnabled=false);
        //    a build whose DSN is the placeholder stays inert (see NeroMapsTelemetry's PLACEHOLDER_DSN guard).
        NeroMapsTelemetry.init();

        // 3. Content registries: items first, NeroMaps' own creative tab last, after every contributor has queued
        //    its items.
        za.co.neroland.neromaps.registry.ModDataComponents.init();
        za.co.neroland.neromaps.registry.ModSounds.init();
        za.co.neroland.neromaps.registry.ModBlocks.init();
        za.co.neroland.neromaps.registry.ModBlockEntities.init();
        za.co.neroland.neromaps.registry.ModItems.init();
        za.co.neroland.neromaps.registry.ModMenus.init();
        ModCreativeTab.init();

        // 4. Player-data erasure registration, before any store can exist: registering late is how an
        //    erasure request silently misses a store (POPIA/GDPR). The one player-keyed store, neromaps:players,
        //    adds itself here and is read through NeroMapsSavedData (Core's SavedDataRecovery).
        NeroMapsData.init();
        za.co.neroland.neromaps.store.PlayerStore.init();
        za.co.neroland.neromaps.store.PlayerStore.onErased(za.co.neroland.neromaps.server.MapServer::erased);

        // 5. Compat: NeroMaps consumes Core's map layer API (no sibling is ever referenced). The event listener only
        //    marks viewers dirty.
        za.co.neroland.neromaps.server.layers.LayerService.init();
        //    NeroMaps' own public layers (signposts, trail markers) go through the same Core API as any sibling's.
        za.co.neroland.neromaps.survival.SurvivalLayers.register();

        // 6. Network payloads on NeroMaps' own channel, before any loader builds it.
        za.co.neroland.neromaps.network.MapNetwork.init();

        // 7. Server hooks are wired by each loader (server tick, commands, server stopping) into MapServer and
        //    MapCommands.

        // 8. The NeroLink module (Core's link API) goes last and swallows its own failures.
        za.co.neroland.neromaps.link.MapsLinkModule.init();
    }
}
