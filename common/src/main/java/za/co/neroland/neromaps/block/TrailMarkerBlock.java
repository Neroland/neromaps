package za.co.neroland.neromaps.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.registry.ModSounds;

/**
 * The Trail Marker: a breadcrumb stake for caves and long trips (decision 013). It shows as a small dot on the map of
 * anyone nearby (layer {@code neromaps:trail_markers}, proximity 64). No label, no text, no owner. Use cycles the cap
 * colour through the seven marker colours (the map dot follows it).
 */
public class TrailMarkerBlock extends Block implements EntityBlock {

    public static final MapCodec<TrailMarkerBlock> CODEC = BlockCodecs.simple(TrailMarkerBlock::new);
    private static final VoxelShape FLOOR = Block.box(6, 0, 6, 10, 10, 10);
    private static final VoxelShape WALL_N = Block.box(6, 4, 10, 10, 12, 16);
    private static final VoxelShape WALL_S = Block.box(6, 4, 0, 10, 12, 6);
    private static final VoxelShape WALL_E = Block.box(0, 4, 6, 6, 12, 10);
    private static final VoxelShape WALL_W = Block.box(10, 4, 6, 16, 12, 10);

    public TrailMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MapBlockProperties.FACING, Direction.NORTH)
                .setValue(MapBlockProperties.WALL, false).setValue(MapBlockProperties.COLOUR, 0));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<TrailMarkerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MapBlockProperties.FACING, MapBlockProperties.WALL, MapBlockProperties.COLOUR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(MapBlockProperties.WALL)) {
            return FLOOR;
        }
        return switch (state.getValue(MapBlockProperties.FACING)) {
            case SOUTH -> WALL_S;
            case EAST -> WALL_E;
            case WEST -> WALL_W;
            default -> WALL_N;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            return defaultBlockState().setValue(MapBlockProperties.WALL, true).setValue(MapBlockProperties.FACING, face);
        }
        return defaultBlockState().setValue(MapBlockProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        SoundEvent e = ModSounds.get("trail_marker.place");
        if (!level.isClientSide() && e != null) {
            level.playSound(null, pos, e, SoundSource.BLOCKS, 0.7f, 1f);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide()) {
            int next = (state.getValue(MapBlockProperties.COLOUR) + 1) % MapBlockProperties.MARKER_COLOURS.length;
            level.setBlock(pos, state.setValue(MapBlockProperties.COLOUR, next), Block.UPDATE_CLIENTS);
            PlayerMessages.overlay(player, Component.translatable("block.neromaps.trail_marker.colour",
                    Component.translatable("gui.neromaps.colour." + MapBlockProperties.MARKER_COLOUR_NAMES[next])));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(MapBlockProperties.FACING, rotation.rotate(state.getValue(MapBlockProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(MapBlockProperties.FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SimpleTickingBlockEntity.trailMarker(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.TRAIL_MARKER.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<SimpleTickingBlockEntity>) (l, p, s, be) -> be.serverTick();
    }
}
