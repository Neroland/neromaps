package za.co.neroland.neromaps.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.platform.NetworkPlatform;

/**
 * NeoForge side of the networking seam: registers every {@link MapNetwork} payload on NeroMaps'
 * own registrar during {@code RegisterPayloadHandlersEvent} and implements the send methods.
 * Registered as the {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>The registrar is {@code optional()}, so a vanilla (or NeroMaps-less) client can still connect —
 * it simply never receives a NeroMaps payload. Handlers run through {@code context.enqueueWork}, on
 * the game thread.
 */
public final class NeoForgeMapNetwork implements NetworkPlatform {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeMapNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MapNetwork.PROTOCOL_VERSION).optional();
        for (MapNetwork.Clientbound<?> cb : MapNetwork.clientbound()) {
            registerClientbound(registrar, cb);
        }
        for (MapNetwork.Serverbound<?> sb : MapNetwork.serverbound()) {
            registerServerbound(registrar, sb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, MapNetwork.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadRegistrar registrar, MapNetwork.Serverbound<T> sb) {
        registrar.playToServer(sb.type(), sb.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        sb.handler().accept(payload, serverPlayer);
                    }
                }));
    }

    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (!player.connection.hasChannel(payload.type())) return false;
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.6 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }
}
