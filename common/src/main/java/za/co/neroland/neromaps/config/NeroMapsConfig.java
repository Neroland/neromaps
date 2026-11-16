package za.co.neroland.neromaps.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * NeroMaps config schema, built on Neroland Core's config framework (file {@code config/neromaps.properties},
 * hot-reloadable via {@code /neroland config reload}). Registered once from {@link NeroMapsCommon#init()},
 * before anything else; every other subsystem reads it, including telemetry's opt-out flag.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative: anonymous
 * crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroMapsConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroMapsCommon.MOD_ID, "NeroMaps configuration.");

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroMaps-only crash reports (Sentry, EU servers) - ON by default. Sends the "
                    + "stack trace, mod/MC/loader/OS/Java versions and your other installed mods' ids "
                    + "and versions; never your IP, username, UUID, position, world data or chat; file paths "
                    + "are scrubbed of your account name. Set false to opt out of all of it (takes effect "
                    + "on restart). See PRIVACY.md");

    // --- server policy. "S" values are server-authoritative: Core pushes them to clients at join and
    // after a reload, and NeroMaps' hello payload repeats them so lock states reset when a client leaves. ---------

    private static final ConfigValue<Boolean> CAVE_MODE = SCHEMA.bool(
            "caveMode", true, true,
            "allow the client-only cave-mode slice on the minimap (server policy; default allowed)");
    private static final ConfigValue<Boolean> RADAR_HOSTILE = SCHEMA.bool("radarHostile", true, true,
            "allow hostile mobs on the entity radar (server policy)");
    private static final ConfigValue<Boolean> RADAR_PASSIVE = SCHEMA.bool("radarPassive", true, true,
            "allow passive mobs on the entity radar (server policy)");
    private static final ConfigValue<Boolean> RADAR_TAMED = SCHEMA.bool("radarTamed", true, true,
            "allow tamed animals on the entity radar (server policy)");
    private static final ConfigValue<Boolean> RADAR_VEHICLES = SCHEMA.bool("radarVehicles", true, true,
            "allow boats and minecarts on the entity radar (server policy)");
    private static final ConfigValue<Boolean> RADAR_ITEMS = SCHEMA.bool("radarItems", true, true,
            "allow dropped items on the entity radar (server policy)");
    private static final ConfigValue<Boolean> RADAR_PLAYERS = SCHEMA.bool("radarPlayers", false, true,
            "show other players on the entity radar (server policy; off by default; never sneaking or invisible "
                    + "players)");
    private static final ConfigValue<Boolean> WAYPOINT_TELEPORT = SCHEMA.bool("waypointTeleport", false, true,
            "let operators teleport to their own waypoints (server policy; off by default; operators only even "
                    + "when on)");
    private static final ConfigValue<Boolean> DEATH_WAYPOINTS = SCHEMA.bool("deathWaypoints", true, true,
            "record each player's last three death points as waypoints (server policy; players can still turn "
                    + "their own off)");
    private static final ConfigValue<Integer> WAYPOINT_LIMIT = SCHEMA.intRange("waypointLimit", 256, 1, 4096, true,
            "most waypoints one player may keep (server policy)");
    private static final ConfigValue<Boolean> PUBLIC_MAP = SCHEMA.bool("publicMap", false, true,
            "public map: every player sees all terrain the server has captured, without fog of war. Players are "
                    + "told on join while this is on (server policy; off by default)");
    private static final ConfigValue<Integer> EXPLORE_RADIUS = SCHEMA.intRange("exploreRadiusChunks", 8, 1, 32,
            true, "chunks around a player that count as explored each second (server policy; clamped to the "
                    + "view distance)");
    private static final ConfigValue<Boolean> SERVER_CAPTURE = SCHEMA.bool("serverCapture", true, true,
            "capture terrain tiles on the server for the app, wall maps and shared maps (server policy). Only "
                    + "already-loaded chunks are read; nothing is ever loaded or generated for the map");
    private static final ConfigValue<Boolean> DEBUG_OVERLAYS = SCHEMA.bool("debugOverlays", false, true,
            "allow the grid, chunk-border and slime-chunk debug overlays (server policy)");
    private static final ConfigValue<String> NAME_DENY_LIST = SCHEMA.string("nameDenyList", "", false,
            "comma-separated words rejected in waypoint and signpost names (case-insensitive; empty = none)");

    // --- server-only tuning (not synced) -----------------------------------------------------------------------

    private static final ConfigValue<Integer> CAPTURE_BUDGET = SCHEMA.intRange("captureBudgetMicros", 1000, 50,
            20000, false, "server-thread time per tick for terrain snapshots, in microseconds");
    private static final ConfigValue<Integer> CAPTURE_MIN_TPS = SCHEMA.intRange("captureMinTps", 15, 1, 20, false,
            "capture pauses entirely while the server runs below this many ticks per second");
    private static final ConfigValue<Integer> CAPTURE_DEMAND = SCHEMA.intRange("captureDemandMinutes", 30, 1, 1440,
            false, "minutes capture keeps running after the last request for server tiles");
    private static final ConfigValue<Integer> TILE_CACHE_MB = SCHEMA.intRange("serverTileCacheMb", 32, 4, 1024,
            false, "memory for decoded and encoded server tiles, in MB");
    private static final ConfigValue<Integer> TILE_DISK_MB = SCHEMA.intRange("serverTileDiskMb", 2048, 16, 65536,
            false, "disk cap for server tiles under the world's data folder, in MB (least recently read go first)");
    private static final ConfigValue<Integer> MAX_EXPLORED_REGIONS = SCHEMA.intRange("maxExploredRegions", 4096, 16,
            65536, false, "most explored 32 x 32-chunk regions recorded per player per dimension (128 bytes each)");

    private NeroMapsConfig() {
    }

    /**
     * Whether anonymous NeroMaps-only crash reporting is on (default true, opt-out). Read once at bootstrap by
     * {@code NeroMapsTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Server policy: cave mode allowed. */
    public static boolean caveModeAllowed() {
        return CAVE_MODE.get();
    }

    public static boolean radarHostile() {
        return RADAR_HOSTILE.get();
    }

    public static boolean radarPassive() {
        return RADAR_PASSIVE.get();
    }

    public static boolean radarTamed() {
        return RADAR_TAMED.get();
    }

    public static boolean radarVehicles() {
        return RADAR_VEHICLES.get();
    }

    public static boolean radarItems() {
        return RADAR_ITEMS.get();
    }

    public static boolean radarPlayers() {
        return RADAR_PLAYERS.get();
    }

    public static boolean waypointTeleport() {
        return WAYPOINT_TELEPORT.get();
    }

    public static boolean deathWaypoints() {
        return DEATH_WAYPOINTS.get();
    }

    public static int waypointLimit() {
        return WAYPOINT_LIMIT.get();
    }

    public static boolean publicMap() {
        return PUBLIC_MAP.get();
    }

    public static int exploreRadiusChunks() {
        return EXPLORE_RADIUS.get();
    }

    public static boolean serverCapture() {
        return SERVER_CAPTURE.get();
    }

    public static boolean debugOverlays() {
        return DEBUG_OVERLAYS.get();
    }

    public static String nameDenyList() {
        return NAME_DENY_LIST.get();
    }

    public static int captureBudgetMicros() {
        return CAPTURE_BUDGET.get();
    }

    public static int captureMinTps() {
        return CAPTURE_MIN_TPS.get();
    }

    public static int captureDemandMinutes() {
        return CAPTURE_DEMAND.get();
    }

    public static int serverTileCacheMb() {
        return TILE_CACHE_MB.get();
    }

    public static int serverTileDiskMb() {
        return TILE_DISK_MB.get();
    }

    public static int maxExploredRegions() {
        return MAX_EXPLORED_REGIONS.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
