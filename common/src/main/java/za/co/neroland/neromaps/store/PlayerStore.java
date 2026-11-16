package za.co.neroland.neromaps.store;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.neromaps.data.NeroMapsData;
import za.co.neroland.neromaps.data.NeroMapsSavedData;

/**
 * Server access to {@link MapPlayerState}: always through Core's {@code SavedDataRecovery} on the overworld, and
 * registered with NeroMaps' composite eraser (decision 008) from {@link #init()}, before any server starts.
 */
public final class PlayerStore {

    /** Notified after an erasure with (erased player, players whose shares with them ended), same tick. */
    private static final List<BiConsumer<UUID, List<UUID>>> ERASE_LISTENERS = new CopyOnWriteArrayList<>();

    private static final NeroMapsData.StoreEraser ERASER = new NeroMapsData.StoreEraser() {
        @Override
        public String name() {
            return MapPlayerState.STORE;
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            MapPlayerState state = server != null ? get(server) : detached;
            if (state == null) {
                return;
            }
            List<UUID> touched = state.erase(player);
            for (BiConsumer<UUID, List<UUID>> l : ERASE_LISTENERS) {
                l.accept(player, touched);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            if (server == null) {
                return;
            }
            NeroMapsSavedData.backupNow(server.overworld(), MapPlayerState.TYPE, get(server), MapPlayerState.STORE);
        }
    };

    /** Test harness only: the store the eraser uses when it is called without a server. Never set in game. */
    private static volatile @org.jetbrains.annotations.Nullable MapPlayerState detached;

    private PlayerStore() {
    }

    /** Test harness only (Core's {@code ErasureConformance} runs with a null server). */
    public static void detachedForTest(@org.jetbrains.annotations.Nullable MapPlayerState state) {
        detached = state;
    }

    /** Registers the store with the composite eraser. Common init, before any server. */
    public static void init() {
        NeroMapsData.addStore(ERASER);
    }

    public static MapPlayerState get(MinecraftServer server) {
        return NeroMapsSavedData.get(server.overworld(), MapPlayerState.TYPE, MapPlayerState::new,
                MapPlayerState.STORE);
    }

    /** Runs after every erasure, on the thread that erased (the server thread for commands and Core's sweep). */
    public static void onErased(BiConsumer<UUID, List<UUID>> listener) {
        ERASE_LISTENERS.add(listener);
    }

    /** The store eraser (tests drive it directly). */
    public static NeroMapsData.StoreEraser eraser() {
        return ERASER;
    }
}
