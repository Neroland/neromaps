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

    private NeroMapsConfig() {
    }

    /**
     * Whether anonymous NeroMaps-only crash reporting is on (default true, opt-out). Read once at bootstrap by
     * {@code NeroMapsTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
