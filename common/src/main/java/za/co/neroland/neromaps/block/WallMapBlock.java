package za.co.neroland.neromaps.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
//? if >=1.21.2 {
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neromaps.registry.ModBlockEntities;

/**
 * A Wall Map panel. Panels of the same facing that touch in the wall's plane form one array (up to 8 x 8) whose
 * frame is drawn by the connection-mask models; the map itself is drawn by {@code WallMapRenderer} from server tiles,
 * per viewer, inside that viewer's fog of war. The block entity stores an area and a dimension, never an owner.
 */
public class WallMapBlock extends Block implements EntityBlock {

    public static final MapCodec<WallMapBlock> CODEC = BlockCodecs.simple(WallMapBlock::new);

    private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape WEST = Block.box(15, 0, 0, 16, 16, 16);

    public WallMapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MapBlockProperties.FACING, Direction.NORTH)
                .setValue(MapBlockProperties.MASK, 0).setValue(MapBlockProperties.LIT, false));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<WallMapBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MapBlockProperties.FACING, MapBlockProperties.MASK, MapBlockProperties.LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MapBlockProperties.FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    /** The viewer's right as seen from the front of a panel facing {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The connection bit a neighbour in {@code dir} sets for a panel facing {@code facing}, or 0. */
    public static int bit(Direction facing, Direction dir) {
        if (dir == Direction.UP) {
            return 1;
        }
        if (dir == Direction.DOWN) {
            return 4;
        }
        if (dir == right(facing)) {
            return 2;
        }
        if (dir == right(facing).getOpposite()) {
            return 8;
        }
        return 0;
    }

    private boolean joins(BlockState self, BlockState other) {
        return other.is(this) && other.getValue(MapBlockProperties.FACING) == self.getValue(MapBlockProperties.FACING);
    }

    private BlockState withMask(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(MapBlockProperties.FACING);
        int mask = 0;
        for (Direction d : new Direction[] {Direction.UP, Direction.DOWN, right(facing), right(facing).getOpposite()}) {
            if (joins(state, level.getBlockState(pos.relative(d)))) {
                mask |= bit(facing, d);
            }
        }
        return state.setValue(MapBlockProperties.MASK, mask);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return withMask(defaultBlockState().setValue(MapBlockProperties.FACING, facing), context.getLevel(),
                context.getClickedPos());
    }

    private BlockState updateMask(BlockState state, Direction dir, BlockState neighbour) {
        int b = bit(state.getValue(MapBlockProperties.FACING), dir);
        if (b == 0) {
            return state;
        }
        int mask = state.getValue(MapBlockProperties.MASK);
        mask = joins(state, neighbour) ? mask | b : mask & ~b;
        return state.setValue(MapBlockProperties.MASK, mask);
    }

    // 1.21.2 reordered updateShape and added the tick-access + random arguments.
    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return updateMask(state, dir, neighbourState);
    }
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return updateMask(state, dir, neighbourState);
    }
    *///?}

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WallMapBlockEntity be) {
            be.placed(stack);
        }
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
        return new WallMapBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.WALL_MAP.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<WallMapBlockEntity>) (l, p, s, be) -> be.serverTick();
    }
}
