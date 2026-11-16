package za.co.neroland.neromaps.data;

import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * The one door every NeroMaps {@link SavedData} read goes through: Neroland Core's {@link SavedDataRecovery}
 * (Core's shared guard, not a local copy). A corrupt or truncated {@code .dat} then degrades to the last-known-good
 * backup, or a fresh store, instead of a crash on every tick.
 *
 * <p>Rules for every store:
 * <ul>
 *   <li>Read it with {@link #get}, never a bare {@code computeIfAbsent}.</li>
 *   <li>If it holds anything keyed by a player, register a {@link NeroMapsData.StoreEraser} whose
 *       {@code backupNow} calls {@link #backupNow} right after erasing, so the erased rows cannot come back from
 *       a stale backup.</li>
 *   <li>Store names are stable and non-identifying ({@code "neromaps:<store>"}); they label logs, telemetry and
 *       the backup file.</li>
 * </ul>
 *
 * <p>Both NeroMaps stores (the player store and the world id) are read through this seam. Declare types through Core's
 * {@code data.SavedDataCompat.type(..)} so the 1.21.1-1.21.4 cells resolve to Core's look-alikes.
 */
public final class NeroMapsSavedData {

    private NeroMapsSavedData() {
    }

    /** The stable recovery label for a NeroMaps store: {@code "neromaps:<store>"}. */
    public static String label(String store) {
        return NeroMapsCommon.MOD_ID + ":" + store;
    }

    /** Reads (or creates) a NeroMaps store through Core's recovery guard. */
    public static <T extends SavedData> T get(ServerLevel level, SavedDataType<T> type, Supplier<T> fresh,
            String store) {
        return SavedDataRecovery.get(level, type, fresh, label(store));
    }

    /** Forces the store's last-known-good backup to match it now (call straight after an erasure). */
    public static <T extends SavedData> void backupNow(ServerLevel level, SavedDataType<T> type, T instance,
            String store) {
        SavedDataRecovery.backupNow(level, type, instance, label(store));
    }
}
