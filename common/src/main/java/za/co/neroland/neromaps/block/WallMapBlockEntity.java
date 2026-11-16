package za.co.neroland.neromaps.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.registry.ModDataComponents;
import za.co.neroland.neromaps.registry.ModSounds;
import za.co.neroland.neromaps.survival.PlacedIndex;

/**
 * One panel of a wall-map array. Stores the array's {@link WallPrint} (an area and a dimension), the print's stamp
 * (game time of placement, so the newest print wins when two arrays join) and this panel's place in the array. No
 * owner, no player name, nothing a player typed. The server re-lays the array out when a panel's connection mask
 * changes; the client reads the same fields from the update tag.
 */
public class WallMapBlockEntity extends BlockEntity {

    /** Largest array side. */
    public static final int MAX_SIDE = 8;

    private @Nullable WallPrint print;
    private long stamp;
    private int col;
    private int row;
    private int cols = 1;
    private int rows = 1;
    private boolean dirty = true;
    private int lastMask = -1;
    private int ticks;

    public WallMapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALL_MAP.get(), pos, state);
    }

    public @Nullable WallPrint print() {
        return print;
    }

    public int col() {
        return col;
    }

    public int row() {
        return row;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    /** Placed from an item: a printed wall map brings its print (and wins the array). */
    public void placed(ItemStack stack) {
        WallPrint p = stack.get(ModDataComponents.WALL_PRINT.get());
        if (p != null && level != null) {
            print = p;
            stamp = level.getGameTime() + 1;
            setChanged();
            SoundEvent e = ModSounds.get("wall_map.set");
            if (e != null) {
                level.playSound(null, worldPosition, e, SoundSource.BLOCKS, 0.8f, 1f);
            }
        }
        dirty = true;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (++ticks % 20 == 0) {
            PlacedIndex.touchWallMap(sl, worldPosition, print != null);
        }
        int mask = getBlockState().getValue(MapBlockProperties.MASK);
        if (dirty || mask != lastMask) {
            dirty = false;
            lastMask = mask;
            relayout(sl);
        }
    }

    /** Flood the coplanar array this panel belongs to and give every member its place and the newest print. */
    private void relayout(ServerLevel sl) {
        BlockState self = getBlockState();
        Direction facing = self.getValue(MapBlockProperties.FACING);
        Direction right = WallMapBlock.right(facing);
        List<WallMapBlockEntity> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(worldPosition);
        seen.add(worldPosition);
        while (!open.isEmpty() && members.size() < MAX_SIDE * MAX_SIDE) {
            BlockPos p = open.poll();
            BlockState st = sl.getBlockState(p);
            if (!st.is(self.getBlock()) || st.getValue(MapBlockProperties.FACING) != facing
                    || !(sl.getBlockEntity(p) instanceof WallMapBlockEntity be)) {
                continue;
            }
            members.add(be);
            for (Direction d : new Direction[] {Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                BlockPos n = p.relative(d);
                if (Math.abs(along(n, right) - along(worldPosition, right)) < MAX_SIDE
                        && Math.abs(n.getY() - worldPosition.getY()) < MAX_SIDE && seen.add(n)
                        && sl.isLoaded(n)) {
                    open.add(n);
                }
            }
        }
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        WallPrint best = null;
        long bestStamp = Long.MIN_VALUE;
        for (WallMapBlockEntity m : members) {
            int u = along(m.worldPosition, right);
            int v = m.worldPosition.getY();
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
            if (m.print != null && m.stamp > bestStamp) {
                best = m.print;
                bestStamp = m.stamp;
            }
        }
        int c = maxU - minU + 1;
        int r = maxV - minV + 1;
        for (WallMapBlockEntity m : members) {
            m.apply(best, bestStamp == Long.MIN_VALUE ? 0 : bestStamp, along(m.worldPosition, right) - minU,
                    maxV - m.worldPosition.getY(), c, r);
            m.lastMask = m.getBlockState().getValue(MapBlockProperties.MASK);
            m.dirty = false;
        }
    }

    private static int along(BlockPos p, Direction right) {
        return p.getX() * right.getStepX() + p.getZ() * right.getStepZ();
    }

    private void apply(@Nullable WallPrint p, long s, int c, int r, int cs, int rs) {
        boolean changed = !java.util.Objects.equals(print, p) || col != c || row != r || cols != cs || rows != rs;
        print = p;
        stamp = s;
        col = c;
        row = r;
        cols = cs;
        rows = rs;
        if (!changed || level == null) {
            return;
        }
        setChanged();
        BlockState st = getBlockState();
        boolean lit = p != null;
        if (st.getValue(MapBlockProperties.LIT) != lit) {
            level.setBlock(worldPosition, st.setValue(MapBlockProperties.LIT, lit), Block.UPDATE_CLIENTS);
        } else {
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
    }

    private void write(CompoundTag tag) {
        if (print != null) {
            tag.putString("dim", print.dimension());
            tag.putInt("x", print.centreX());
            tag.putInt("z", print.centreZ());
            tag.putInt("span", print.span());
        }
        tag.putLong("stamp", stamp);
        tag.putInt("col", col);
        tag.putInt("row", row);
        tag.putInt("cols", cols);
        tag.putInt("rows", rows);
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
        if (print != null) {
            output.putString("dim", print.dimension());
            output.putInt("x", print.centreX());
            output.putInt("z", print.centreZ());
            output.putInt("span", print.span());
        }
        output.putLong("stamp", stamp);
        output.putInt("col", col);
        output.putInt("row", row);
        output.putInt("cols", cols);
        output.putInt("rows", rows);
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
        String dim = input.getStringOr("dim", "");
        print = dim.isEmpty() ? null : new WallPrint(dim, input.getIntOr("x", 0), input.getIntOr("z", 0),
                input.getIntOr("span", 64));
        stamp = input.getLongOr("stamp", 0L);
        cols = Math.max(1, Math.min(MAX_SIDE, input.getIntOr("cols", 1)));
        rows = Math.max(1, Math.min(MAX_SIDE, input.getIntOr("rows", 1)));
        col = Math.max(0, Math.min(cols - 1, input.getIntOr("col", 0)));
        row = Math.max(0, Math.min(rows - 1, input.getIntOr("row", 0)));
        dirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
