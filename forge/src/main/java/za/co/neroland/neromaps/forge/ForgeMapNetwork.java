package za.co.neroland.neromaps.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.platform.NetworkPlatform;

/**
 * Forge side of the cross-loader packet seam, on NeroMaps' own {@code neromaps:main} channel.
 * Registered as the {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>Forge seals a channel at {@code build()}, so every payload must be declared before then — which
 * is why NeroMaps owns this channel rather than adding to Neroland Core's, and why
 * {@link #register()} must run <b>after</b> {@code NeroMapsCommon.init()}, where the payloads are
 * declared. {@code optional()} keeps a NeroMaps-less client connectable. While no payload is
 * declared the channel is not built; attempting to send then raises an error.
 */
public final class ForgeMapNetwork implements NetworkPlatform {

    private static Channel<CustomPacketPayload> channel;

    public static void register() {
        if (!MapNetwork.hasPayloads()) {
            NeroMapsCommon.LOGGER.debug("[NeroMaps] no payloads declared; neromaps:main channel not built");
            return;
        }
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, MapNetwork.CHANNEL))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (MapNetwork.Clientbound<?> cb : MapNetwork.clientbound()) {
            registerClientbound(play, cb);
        }
        for (MapNetwork.Serverbound<?> sb : MapNetwork.serverbound()) {
            registerServerbound(play, sb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            MapNetwork.Clientbound<T> cb) {
        // One bidirectional flow: a clientbound payload is only ever accepted from the server (no sender). A client
        // (a LAN guest on an integrated server) sending one is ignored.
        play.addMain(cb.type(), registryCodec(cb.codec()), (payload, context) -> {
            if (context.getSender() == null) {
                cb.handler().accept(payload);
            }
        });
    }

    /** Both directions share the one bidirectional flow; the sender is what distinguishes them. */
    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            MapNetwork.Serverbound<T> sb) {
        play.addMain(sb.type(), registryCodec(sb.codec()), (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer serverPlayer) {
                sb.handler().accept(payload, serverPlayer);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }

    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel == null) throw new IllegalStateException("NeroMaps network channel is not registered");
        if (!channel.isRemotePresent(player.connection.getConnection())) return false;
        channel.send(payload, PacketDistributor.PLAYER.with(player));
        return true;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel == null) throw new IllegalStateException("NeroMaps network channel is not registered");
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }
}
