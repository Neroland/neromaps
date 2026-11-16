package za.co.neroland.neromaps.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neromaps.menu.CartographyDeskMenu;
import za.co.neroland.neromaps.menu.MenuOpener;

/**
 * The Cartography Desk: copy, merge and label atlases, print wall maps, make signposts. It holds nothing: like a
 * vanilla cartography table, the menu's slots live with the open menu and go back to the player when it closes, so
 * breaking the desk can never lose an item. Opened only through {@link MenuOpener}.
 */
public class CartographyDeskBlock extends Block {

    public static final MapCodec<CartographyDeskBlock> CODEC = BlockCodecs.simple(CartographyDeskBlock::new);

    public CartographyDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MapBlockProperties.FACING, Direction.NORTH));
    }

    // No @Override: codec() is abstract up to 26.2 and no longer declared on the supertype in 26.3.
    protected MapCodec<CartographyDeskBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MapBlockProperties.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(MapBlockProperties.FACING, context.getHorizontalDirection().getOpposite());
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        return MenuOpener.openOrConsume(player, new SimpleMenuProvider(
                (id, inventory, p) -> new CartographyDeskMenu(id, inventory, access),
                Component.translatable("gui.neromaps.desk.title")));
    }
}
