package za.co.neroland.neromaps.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neromaps.NeroMapsCommon;

/**
 * Version-stable wire helpers for the {@code neromaps:main} payloads. Strings are length-capped on read, lists are
 * count-capped, enums travel as clamped ordinals: a malformed packet yields a harmless value or a decode error that
 * the loader drops, never an unbounded allocation.
 */
public final class Wire {

    public static final int MAX_STRING = 256;

    private Wire() {
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, path));
    }

    public static void string(FriendlyByteBuf buf, String value) {
        buf.writeUtf(value == null ? "" : value, MAX_STRING);
    }

    public static String string(FriendlyByteBuf buf) {
        return buf.readUtf(MAX_STRING);
    }

    public static void strings(FriendlyByteBuf buf, List<String> list, int max) {
        list(buf, list, max, Wire::string);
    }

    public static List<String> strings(FriendlyByteBuf buf, int max) {
        return list(buf, max, Wire::string);
    }

    public static <B extends FriendlyByteBuf, T> void list(B buf, List<T> list, int max, BiConsumer<B, T> writer) {
        int count = Math.min(max, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    public static <B extends FriendlyByteBuf, T> List<T> list(B buf, int max, Function<B, T> reader) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("list too long: " + count);
        }
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    public static void bytes(FriendlyByteBuf buf, byte[] data, int max) {
        int n = Math.min(max, data.length);
        buf.writeVarInt(n);
        buf.writeBytes(data, 0, n);
    }

    public static byte[] bytes(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max || n > buf.readableBytes()) {
            throw new IllegalArgumentException("byte array too long: " + n);
        }
        byte[] out = new byte[n];
        buf.readBytes(out);
        return out;
    }

    public static int ordinal(FriendlyByteBuf buf, int count) {
        int o = buf.readByte();
        return o < 0 || o >= count ? 0 : o;
    }
}
