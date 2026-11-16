package za.co.neroland.neromaps.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.registry.ModBlockEntities;

/**
 * The Map Table: a horizontal map of its own surroundings, drawn flat in the glass top (decision 013's written
 * fallback; the holographic relief is cut for 0.1.0). Each viewer sees only what is inside their own fog of war.
 * Use cycles the span (32, 64 or 128 blocks across). Stores the span only.
 */
public class MapTableBlock extends Block implements EntityBlock {

    public static final MapCodec<MapTableBlock> CODEC = BlockCodecs.simple(MapTableBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 11, 16);

    public MapTableBlock(Properties properties) {
        super(properties);
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<MapTableBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SimpleTickingBlockEntity be) {
            be.cycleSpan();
            PlayerMessages.overlay(player, Component.translatable("block.neromaps.map_table.span", be.span()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SimpleTickingBlockEntity.mapTable(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.MAP_TABLE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<SimpleTickingBlockEntity>) (l, p, s, be) -> be.serverTick();
    }
}
