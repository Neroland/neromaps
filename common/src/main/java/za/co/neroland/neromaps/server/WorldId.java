package za.co.neroland.neromaps.server;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.data.NeroMapsSavedData;

/**
 * A random id for this world, {@code neromaps:world} (decision 010), sent in the hello payload so a client keeps a
 * separate terrain cache per world on the same server address. Not player data: it identifies the save, not anyone.
 */
public final class WorldId extends SavedData {

    public static final Codec<WorldId> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(w -> w.id)
    ).apply(i, WorldId::new));

    public static final SavedDataType<WorldId> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "world"), WorldId::new, CODEC);

    private final String id;

    public WorldId() {
        this(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        setDirty();
    }

    private WorldId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static String get(MinecraftServer server) {
        return NeroMapsSavedData.get(server.overworld(), TYPE, WorldId::new, "world").id;
    }
}
