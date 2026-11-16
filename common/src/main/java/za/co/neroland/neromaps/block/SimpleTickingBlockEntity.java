package za.co.neroland.neromaps.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.survival.PlacedIndex;

/**
 * The block entity of the Trail Marker and the Map Table: it exists so the block is indexed while loaded (decision
 * 013's request: the cheapest index is the signpost index). The Map Table also stores its span (blocks shown across
 * the table top). No owner and nothing a player typed.
 */
public class SimpleTickingBlockEntity extends BlockEntity {

    private int span = 64;
    private int ticks;

    public SimpleTickingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static SimpleTickingBlockEntity trailMarker(BlockPos pos, BlockState state) {
        return new SimpleTickingBlockEntity(ModBlockEntities.TRAIL_MARKER.get(), pos, state);
    }

    public static SimpleTickingBlockEntity mapTable(BlockPos pos, BlockState state) {
        return new SimpleTickingBlockEntity(ModBlockEntities.MAP_TABLE.get(), pos, state);
    }

    public int span() {
        return span;
    }

    public void cycleSpan() {
        span = WallPrint.nextSpan(span);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) {
            return;
        }
        BlockState st = getBlockState();
        if (st.hasProperty(MapBlockProperties.COLOUR)) {
            PlacedIndex.touchTrailMarker(sl, worldPosition, st.getValue(MapBlockProperties.COLOUR));
        } else {
            PlacedIndex.touchWallMap(sl, worldPosition, true);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putInt("span", span);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        span = WallPrint.snap(input.getIntOr("span", 64));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("span", span);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
