package za.co.neroland.neromaps.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.registry.ModBlockEntities;

/**
 * The Signpost: a public waypoint anyone nearby sees on their map (layer {@code neromaps:signposts}, proximity
 * scoped). Use opens the label editor; sneak-use adds a plate (one to three). Stores no owner.
 */
public class SignpostBlock extends Block implements EntityBlock {

    public static final MapCodec<SignpostBlock> CODEC = BlockCodecs.simple(SignpostBlock::new);
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public SignpostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MapBlockProperties.ROTATION, 0)
                .setValue(MapBlockProperties.PLATES, 1));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<SignpostBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MapBlockProperties.ROTATION, MapBlockProperties.PLATES);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the plate points the way the player faces, in 45-degree steps (0 = north)
        int r = Mth.floor((context.getRotation() + 180f) * 8f / 360f + 0.5f) & 7;
        return defaultBlockState().setValue(MapBlockProperties.ROTATION, r);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            int next = state.getValue(MapBlockProperties.PLATES) % 3 + 1;
            level.setBlock(pos, state.setValue(MapBlockProperties.PLATES, next), Block.UPDATE_CLIENTS);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SignpostBlockEntity be) {
            MapNetwork.send(sp, new Payloads.SignpostOpen(pos.asLong(), be.label(), be.glyph(), be.colour(),
                    be.shown()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        int r = state.getValue(MapBlockProperties.ROTATION);
        int turn = switch (rotation) {
            case CLOCKWISE_90 -> 2;
            case CLOCKWISE_180 -> 4;
            case COUNTERCLOCKWISE_90 -> 6;
            default -> 0;
        };
        return state.setValue(MapBlockProperties.ROTATION, (r + turn) & 7);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        int r = state.getValue(MapBlockProperties.ROTATION);
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(MapBlockProperties.ROTATION, (8 - r) & 7);
            case FRONT_BACK -> state.setValue(MapBlockProperties.ROTATION, (12 - r) & 7);
            default -> state;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignpostBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SIGNPOST.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<SignpostBlockEntity>) (l, p, s, be) -> be.serverTick();
    }
}
