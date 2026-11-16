package za.co.neroland.neromaps.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Blockstate properties of NeroMaps' five blocks. */
public final class MapBlockProperties {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Wall map: which neighbours continue the panel, seen from the front (1 up, 2 right, 4 down, 8 left). */
    public static final IntegerProperty MASK = IntegerProperty.create("mask", 0, 15);
    /** Wall map: an area is set (the lower-right piece shows the status pixel). */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Signpost: eight-way rotation (0 = north, clockwise in 45-degree steps). */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);
    /** Signpost: one to three direction plates. */
    public static final IntegerProperty PLATES = IntegerProperty.create("plates", 1, 3);
    /** Trail marker: attached to a wall rather than standing on the floor. */
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    /** Trail marker: cap colour, an index into the seven marker colours. */
    public static final IntegerProperty COLOUR = IntegerProperty.create("colour", 0, 6);

    /** The seven colour-blind-safe marker colours (decision 013 section 1), in picker order. */
    public static final int[] MARKER_COLOURS = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0xD55E00, 0xCC79A7, 0xF2F5F8};
    public static final String[] MARKER_COLOUR_NAMES = {"orange", "sky", "green", "yellow", "vermillion", "purple",
        "white"};

    private MapBlockProperties() {
    }
}
