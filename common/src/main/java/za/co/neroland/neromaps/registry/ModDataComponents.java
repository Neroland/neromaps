package za.co.neroland.neromaps.registry;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
//?}
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.atlas.AtlasData;
import za.co.neroland.neromaps.atlas.WallPrint;

/**
 * Item data components (persistent codec only; network sync falls back to it). Neither carries an identity: an atlas
 * holds region coverage, a print holds an area and a dimension.
 */
public final class ModDataComponents {

    //? if >=1.20.5 {
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroMapsCommon.MOD_ID);

    public static final RegistryEntry<DataComponentType<AtlasData>> ATLAS = COMPONENTS.register("atlas",
            key -> DataComponentType.<AtlasData>builder().persistent(AtlasData.CODEC).build());

    public static final RegistryEntry<DataComponentType<WallPrint>> WALL_PRINT = COMPONENTS.register("wall_print",
            key -> DataComponentType.<WallPrint>builder().persistent(WallPrint.CODEC).build());

    //?} else {
    /*public static final LegacyComponent<AtlasData> ATLAS = new LegacyComponent<>("neromaps:atlas", AtlasData.CODEC);
    public static final LegacyComponent<WallPrint> WALL_PRINT =
            new LegacyComponent<>("neromaps:wall_print", WallPrint.CODEC);

    public record LegacyComponent<T>(String key, com.mojang.serialization.Codec<T> codec) {
        public T read(net.minecraft.world.item.ItemStack stack) {
            var tag = stack.getTag();
            var value = tag == null ? null : tag.get(key);
            return value == null ? null : codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, value).result().orElse(null);
        }

        public void write(net.minecraft.world.item.ItemStack stack, T value) {
            var tag = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value).result()
                    .orElseThrow(() -> new IllegalArgumentException("Cannot write item key " + key));
            stack.getOrCreateTag().put(key, tag);
        }

        public void clear(net.minecraft.world.item.ItemStack stack) {
            stack.removeTagKey(key);
        }
    }
    *///?}

    private ModDataComponents() {
    }

    /** Classload-forcing no-op (init step 3, first). */
    public static void init() {
    }
}
