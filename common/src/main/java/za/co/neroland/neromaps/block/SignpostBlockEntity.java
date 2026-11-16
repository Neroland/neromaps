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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.registry.ModBlockEntities;
import za.co.neroland.neromaps.survival.PlacedIndex;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * A Signpost: a public waypoint. Stores a label (filtered like a waypoint name, at most {@value #MAX_LABEL}
 * characters), a glyph, a colour and whether it shows on maps. Never an owner, never who wrote the label.
 */
public class SignpostBlockEntity extends BlockEntity {

    public static final int MAX_LABEL = 24;
    public static final int GLYPHS = 16;

    private String label = "";
    private int glyph;
    private int colour = 6;
    private boolean shown = true;
    private int ticks;

    public SignpostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNPOST.get(), pos, state);
    }

    public String label() {
        return label;
    }

    public int glyph() {
        return glyph;
    }

    public int colour() {
        return colour;
    }

    public boolean shown() {
        return shown;
    }

    /** Validated server-side edit; false when the label is refused by the server's deny list. */
    public boolean edit(String rawLabel, int newGlyph, int newColour, boolean newShown) {
        String clean = NameFilter.clean(rawLabel == null ? "" : rawLabel, MAX_LABEL);
        if (!clean.isEmpty() && NameFilter.check(clean, NeroMapsConfig.nameDenyList()) != NameFilter.Verdict.OK) {
            return false;
        }
        label = clean;
        glyph = Math.floorMod(newGlyph, GLYPHS);
        colour = Math.floorMod(newColour, MapBlockProperties.MARKER_COLOURS.length);
        shown = newShown;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public void serverTick() {
        if (level instanceof ServerLevel sl && ++ticks % 20 == 0) {
            PlacedIndex.touchSignpost(sl, worldPosition, label, glyph, colour, shown);
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
        output.putString("label", label);
        output.putInt("glyph", glyph);
        output.putInt("colour", colour);
        output.putBoolean("shown", shown);
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
        label = NameFilter.clean(input.getStringOr("label", ""), MAX_LABEL);
        glyph = Math.floorMod(input.getIntOr("glyph", 0), GLYPHS);
        colour = Math.floorMod(input.getIntOr("colour", 6), MapBlockProperties.MARKER_COLOURS.length);
        shown = input.getBooleanOr("shown", true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("label", label);
        tag.putInt("glyph", glyph);
        tag.putInt("colour", colour);
        tag.putBoolean("shown", shown);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
