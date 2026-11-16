package za.co.neroland.neromaps.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.server.ServerHandlers;

/**
 * NeroMaps' own payload channel, {@code neromaps:main} (Forge channel name; NeoForge and Fabric register each payload
 * type under {@code neromaps:<name>}). Every payload is declared in {@link #init()} (common init step 6), before any
 * loader builds its channel. Core's network is never reused. Serverbound handlers receive the sending
 * {@link ServerPlayer}, run on the server thread, and validate everything against that sender.
 */
public final class MapNetwork {

    public record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, Consumer<T> handler) { }

    public record Serverbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) { }

    public static final String CHANNEL = "main";
    public static final String PROTOCOL_VERSION = "1";
    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();
    private static final AtomicBoolean DECLARED = new AtomicBoolean();

    private MapNetwork() {
    }

    private static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    private static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    public static List<Clientbound<?>> clientbound() {
        return Collections.unmodifiableList(CLIENTBOUND);
    }

    public static List<Serverbound<?>> serverbound() {
        return Collections.unmodifiableList(SERVERBOUND);
    }

    public static boolean hasPayloads() {
        return !CLIENTBOUND.isEmpty() || !SERVERBOUND.isEmpty();
    }

    public static void init() {
        if (!DECLARED.compareAndSet(false, true)) {
            return;
        }
        serverbound(Payloads.WaypointEdit.TYPE, Payloads.WaypointEdit.STREAM_CODEC, ServerHandlers::waypointEdit);
        serverbound(Payloads.ShareAction.TYPE, Payloads.ShareAction.STREAM_CODEC, ServerHandlers::shareAction);
        serverbound(Payloads.PrivacySet.TYPE, Payloads.PrivacySet.STREAM_CODEC, ServerHandlers::privacy);
        serverbound(Payloads.LayerView.TYPE, Payloads.LayerView.STREAM_CODEC, ServerHandlers::layerView);
        serverbound(Payloads.TileRequest.TYPE, Payloads.TileRequest.STREAM_CODEC, ServerHandlers::tileRequest);
        serverbound(Payloads.DeskLabel.TYPE, Payloads.DeskLabel.STREAM_CODEC,
                za.co.neroland.neromaps.survival.SurvivalHandlers::deskLabel);
        serverbound(Payloads.SignpostEdit.TYPE, Payloads.SignpostEdit.STREAM_CODEC,
                za.co.neroland.neromaps.survival.SurvivalHandlers::signpostEdit);

        clientbound(Payloads.Hello.TYPE, Payloads.Hello.STREAM_CODEC, p -> ClientSink.Holder.get().hello(p));
        clientbound(Payloads.Waypoints.TYPE, Payloads.Waypoints.STREAM_CODEC,
                p -> ClientSink.Holder.get().waypoints(p));
        clientbound(Payloads.Shares.TYPE, Payloads.Shares.STREAM_CODEC, p -> ClientSink.Holder.get().shares(p));
        clientbound(Payloads.Positions.TYPE, Payloads.Positions.STREAM_CODEC,
                p -> ClientSink.Holder.get().positions(p));
        clientbound(Payloads.Notice.TYPE, Payloads.Notice.STREAM_CODEC, p -> ClientSink.Holder.get().notice(p));
        clientbound(Payloads.LayerCatalogue.TYPE, Payloads.LayerCatalogue.STREAM_CODEC,
                p -> ClientSink.Holder.get().layerCatalogue(p));
        clientbound(Payloads.LayerDelta.TYPE, Payloads.LayerDelta.STREAM_CODEC,
                p -> ClientSink.Holder.get().layerDelta(p));
        clientbound(Payloads.DimensionBadges.TYPE, Payloads.DimensionBadges.STREAM_CODEC,
                p -> ClientSink.Holder.get().dimensionBadges(p));
        clientbound(Payloads.TileData.TYPE, Payloads.TileData.STREAM_CODEC, p -> ClientSink.Holder.get().tile(p));
        clientbound(Payloads.Perf.TYPE, Payloads.Perf.STREAM_CODEC, p -> ClientSink.Holder.get().perf(p));
        clientbound(Payloads.SignpostOpen.TYPE, Payloads.SignpostOpen.STREAM_CODEC,
                p -> ClientSink.Holder.get().signpostOpen(p));
        clientbound(Payloads.Export.TYPE, Payloads.Export.STREAM_CODEC, p -> ClientSink.Holder.get().export(p));
    }

    /** Sends to one player; false when the client has no NeroMaps channel. Never throws. */
    public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        try {
            return Services.NETWORK.sendToPlayer(player, payload);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
