package za.co.neroland.neromaps.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.store.ExploredMap;
import za.co.neroland.neromaps.store.ShareScope;

/**
 * The map milestones NeroMaps records as player-scope Neroland Core gates (files under
 * {@code data/neromaps/neroland_gates/}, no {@code requires}). Other mods such as trophies or quests read them;
 * NeroMaps never reads them back to decide anything, and fog of war never depends on them.
 *
 * <p>Privacy (POPIA/GDPR): only the player's UUID goes into Core's progression store (purged by Core's erasure
 * hook). No coordinate, dimension, waypoint name or counterparty ever reaches a gate, and nothing here logs.
 * Server thread only; opening an open gate is a no-op.
 */
public final class MapMilestones {

    /** A waypoint share the player offered was accepted under live consent (credits the sharer). */
    public static final Identifier FIRST_SHARED_WAYPOINT =
            Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "first_shared_waypoint");

    private MapMilestones() {
    }

    /** The gate for explored rung {@code index} (0-based into {@link MapMilestoneRules#EXPLORED_RUNGS}). */
    public static Identifier exploredGate(int index) {
        return Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID,
                "explored/" + MapMilestoneRules.EXPLORED_RUNGS[index]);
    }

    /** Total explored chunks across every dimension, from the bitsets NeroMaps already stores. */
    public static long exploredChunks(ExploredMap map) {
        long n = 0;
        for (String dim : map.dimensions()) {
            n += map.chunkCount(dim);
        }
        return n;
    }

    /**
     * Opens every explored rung the player has reached that is above {@code alreadyCredited}, and returns the
     * new count of rungs reached (the caller keeps it in memory to skip the count next time).
     */
    public static int creditExplored(MinecraftServer server, UUID player, long exploredChunks, int alreadyCredited) {
        int reached = MapMilestoneRules.rungsReached(exploredChunks);
        for (int i = Math.max(0, alreadyCredited); i < reached; i++) {
            open(server, player, exploredGate(i));
        }
        return Math.max(reached, alreadyCredited);
    }

    /** A share request was accepted: credits the sharer when it shared a waypoint. */
    public static void shareAccepted(MinecraftServer server, UUID sharer, ShareScope scope) {
        if (scope != null && scope.kind() == ShareScope.Kind.WAYPOINT) {
            open(server, sharer, FIRST_SHARED_WAYPOINT);
        }
    }

    private static void open(MinecraftServer server, UUID player, Identifier gate) {
        if (server == null || player == null) {
            return;
        }
        try {
            ProgressionGates.open(server, player, gate);
        } catch (RuntimeException e) {
            NeroMapsCommon.LOGGER.debug("[NeroMaps] milestone not recorded: {}", e.getClass().getName());
        }
    }
}
