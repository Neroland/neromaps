package za.co.neroland.neromaps.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.block.CartographyDeskBlock;
import za.co.neroland.neromaps.block.MapTableBlock;
import za.co.neroland.neromaps.block.SignpostBlock;
import za.co.neroland.neromaps.block.TrailMarkerBlock;
import za.co.neroland.neromaps.block.WallMapBlock;

/** NeroMaps' five blocks (the whole block budget). */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroMapsCommon.MOD_ID);

    public static final RegistryEntry<Block> CARTOGRAPHY_DESK = BLOCKS.register(MapIds.CARTOGRAPHY_DESK,
            key -> new CartographyDeskBlock(metal(key, 2.5F).noOcclusion()));
    public static final RegistryEntry<Block> WALL_MAP = BLOCKS.register(MapIds.WALL_MAP,
            key -> new WallMapBlock(metal(key, 1.0F).noOcclusion()));
    public static final RegistryEntry<Block> SIGNPOST = BLOCKS.register(MapIds.SIGNPOST,
            key -> new SignpostBlock(metal(key, 1.0F).noOcclusion()));
    public static final RegistryEntry<Block> MAP_TABLE = BLOCKS.register(MapIds.MAP_TABLE,
            key -> new MapTableBlock(metal(key, 2.0F).noOcclusion().lightLevel(s -> 4)));
    public static final RegistryEntry<Block> TRAIL_MARKER = BLOCKS.register(MapIds.TRAIL_MARKER,
            key -> new TrailMarkerBlock(metal(key, 0.3F).noOcclusion().lightLevel(s -> 5)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties metal(ResourceKey<Block> key, float strength) {
        return BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_GRAY).strength(strength, 6.0F)
                .sound(SoundType.METAL);
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
