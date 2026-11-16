package za.co.neroland.neromaps.policy;

import za.co.neroland.neromaps.config.NeroMapsConfig;

/**
 * The server's map policy as one immutable value. The server builds it from config; the client receives
 * it in the hello payload, resolves {@code effective = client setting AND policy}, and shows why a control is locked.
 * The server enforces every value again on its own paths; this copy only drives the client's display.
 *
 * @param revision bumps on every reload, so a client can tell a fresh policy from a repeat
 */
public record PolicySnapshot(int revision, boolean caveMode, boolean radarHostile, boolean radarPassive,
        boolean radarTamed, boolean radarVehicles, boolean radarItems, boolean radarPlayers, boolean waypointTeleport,
        boolean deathWaypoints, int waypointLimit, boolean publicMap, int exploreRadiusChunks, boolean serverCapture,
        boolean debugOverlays) {

    /** What a client assumes on a server without NeroMaps: its own features allowed, nothing server-side. */
    public static final PolicySnapshot CLIENT_ONLY = new PolicySnapshot(0, true, true, true, true, true, true, false,
            false, false, 256, false, 8, false, true);

    /** The controls a policy can lock, each with its explanation key {@code gui.neromaps.locked.<key>}. */
    public enum Feature {
        CAVE_MODE("cave_mode"), RADAR_HOSTILE("radar_category"), RADAR_PASSIVE("radar_category"),
        RADAR_TAMED("radar_category"), RADAR_VEHICLES("radar_category"), RADAR_ITEMS("radar_category"),
        RADAR_PLAYERS("radar_players"), TELEPORT("teleport"), DEATH_POINTS("death_points"), OVERLAYS("overlays");

        private final String key;

        Feature(String key) {
            this.key = key;
        }

        /** The lang key explaining the lock. */
        public String lockKey() {
            return "gui.neromaps.locked." + key;
        }
    }

    public PolicySnapshot {
        waypointLimit = Math.max(1, Math.min(4096, waypointLimit));
        exploreRadiusChunks = Math.max(1, Math.min(32, exploreRadiusChunks));
    }

    /** The live server policy from {@link NeroMapsConfig}. */
    public static PolicySnapshot fromConfig(int revision) {
        return new PolicySnapshot(revision, NeroMapsConfig.caveModeAllowed(), NeroMapsConfig.radarHostile(),
                NeroMapsConfig.radarPassive(), NeroMapsConfig.radarTamed(), NeroMapsConfig.radarVehicles(),
                NeroMapsConfig.radarItems(), NeroMapsConfig.radarPlayers(), NeroMapsConfig.waypointTeleport(),
                NeroMapsConfig.deathWaypoints(), NeroMapsConfig.waypointLimit(), NeroMapsConfig.publicMap(),
                NeroMapsConfig.exploreRadiusChunks(), NeroMapsConfig.serverCapture(), NeroMapsConfig.debugOverlays());
    }

    /** True when the policy allows {@code f}. */
    public boolean allows(Feature f) {
        return switch (f) {
            case CAVE_MODE -> caveMode;
            case RADAR_HOSTILE -> radarHostile;
            case RADAR_PASSIVE -> radarPassive;
            case RADAR_TAMED -> radarTamed;
            case RADAR_VEHICLES -> radarVehicles;
            case RADAR_ITEMS -> radarItems;
            case RADAR_PLAYERS -> radarPlayers;
            case TELEPORT -> waypointTeleport;
            case DEATH_POINTS -> deathWaypoints;
            case OVERLAYS -> debugOverlays;
        };
    }

    /** The booleans packed for the wire, in declaration order. */
    public int flags() {
        boolean[] b = {caveMode, radarHostile, radarPassive, radarTamed, radarVehicles, radarItems, radarPlayers,
                waypointTeleport, deathWaypoints, publicMap, serverCapture, debugOverlays};
        int f = 0;
        for (int i = 0; i < b.length; i++) {
            if (b[i]) {
                f |= 1 << i;
            }
        }
        return f;
    }

    /** The inverse of {@link #flags()}. */
    public static PolicySnapshot of(int revision, int flags, int waypointLimit, int exploreRadiusChunks) {
        return new PolicySnapshot(revision, bit(flags, 0), bit(flags, 1), bit(flags, 2), bit(flags, 3), bit(flags, 4),
                bit(flags, 5), bit(flags, 6), bit(flags, 7), bit(flags, 8), waypointLimit, bit(flags, 9),
                exploreRadiusChunks, bit(flags, 10), bit(flags, 11));
    }

    private static boolean bit(int f, int i) {
        return (f & (1 << i)) != 0;
    }
}
