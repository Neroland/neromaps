package za.co.neroland.neromaps.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader seam for NeroMaps' own payloads ({@code neromaps:main}). One implementation per loader. */
public interface NetworkPlatform {

    /** Sends to one player; false when that client has no NeroMaps channel (nothing is sent). */
    boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client only: sends to the server. Callers check {@code ClientState.serverHasMod()} first. */
    void sendToServer(CustomPacketPayload payload);
}
