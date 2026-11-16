package za.co.neroland.neromaps.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.block.SignpostBlockEntity;
import za.co.neroland.neromaps.block.SimpleTickingBlockEntity;
import za.co.neroland.neromaps.block.WallMapBlockEntity;

/**
 * Block-entity types through Core's {@link BlockEntityTypeCompat} (1.21.1's three-argument constructor; Fabric 26.1.2
 * needs the access widener entries in {@code neromaps.accesswidener}). None stores an owner.
 */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroMapsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<WallMapBlockEntity>> WALL_MAP = BLOCK_ENTITIES.register(
            MapIds.WALL_MAP, key -> BlockEntityTypeCompat.create(WallMapBlockEntity::new, ModBlocks.WALL_MAP.get()));
    public static final RegistryEntry<BlockEntityType<SignpostBlockEntity>> SIGNPOST = BLOCK_ENTITIES.register(
            MapIds.SIGNPOST, key -> BlockEntityTypeCompat.create(SignpostBlockEntity::new, ModBlocks.SIGNPOST.get()));
    public static final RegistryEntry<BlockEntityType<SimpleTickingBlockEntity>> MAP_TABLE = BLOCK_ENTITIES.register(
            MapIds.MAP_TABLE, key -> BlockEntityTypeCompat.create(SimpleTickingBlockEntity::mapTable,
                    ModBlocks.MAP_TABLE.get()));
    public static final RegistryEntry<BlockEntityType<SimpleTickingBlockEntity>> TRAIL_MARKER =
            BLOCK_ENTITIES.register(MapIds.TRAIL_MARKER, key -> BlockEntityTypeCompat.create(
                    SimpleTickingBlockEntity::trailMarker, ModBlocks.TRAIL_MARKER.get()));

    private ModBlockEntities() {
    }

    /** Classload-forcing no-op (init step 3). */
    public static void init() {
    }
}
