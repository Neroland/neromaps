package za.co.neroland.neromaps.menu;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import za.co.neroland.neromaps.atlas.AtlasData;
import za.co.neroland.neromaps.atlas.AtlasItem;
import za.co.neroland.neromaps.atlas.WallPrint;
import za.co.neroland.neromaps.config.NeroMapsConfig;
import za.co.neroland.neromaps.registry.ModBlocks;
import za.co.neroland.neromaps.registry.ModDataComponents;
import za.co.neroland.neromaps.registry.ModItems;
import za.co.neroland.neromaps.registry.ModMenus;
import za.co.neroland.neromaps.registry.ModSounds;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * The Cartography Desk menu. Four tabs share three input slots and one output:
 * <ul>
 *   <li><b>Copy</b>: a filled atlas (kept) + a blank atlas (used) gives a copy at the blank's capacity;</li>
 *   <li><b>Merge</b>: two filled atlases of one dimension (both used) give their union at the first's capacity;</li>
 *   <li><b>Label</b>: an atlas + the typed label (filtered like a waypoint name) gives the labelled atlas;</li>
 *   <li><b>Print</b>: a filled atlas (kept) + paper gives a Wall Map printed with the atlas's area; a sign instead of
 *       paper gives a Signpost.</li>
 * </ul>
 * The inputs belong to the open menu, never to the desk, and go back to the player when it closes. The label arrives
 * in a validated, rate-limited payload; the tab and print span are vanilla menu buttons.
 *
 * <p><b>Slots:</b> 0 atlas A, 1 atlas B, 2 paper/sign, 3 output, 4..30 inventory, 31..39 hotbar.
 */
public class CartographyDeskMenu extends AbstractContainerMenu {

    public static final int MODE_COPY = 0;
    public static final int MODE_MERGE = 1;
    public static final int MODE_LABEL = 2;
    public static final int MODE_PRINT = 3;
    public static final int BUTTON_SPAN = 10;

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;
    public static final int SLOT_PAPER = 2;
    public static final int SLOT_OUT = 3;
    public static final int INPUTS = 3;

    public static final int WIDTH = 176;
    public static final int HEIGHT = 186;

    private static final int DATA_MODE = 0;
    private static final int DATA_SPAN = 1;

    private final ContainerLevelAccess access;
    private final SimpleContainer input = new SimpleContainer(INPUTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final ContainerData data = new SimpleContainerData(2);
    private String label = "";

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public CartographyDeskMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public CartographyDeskMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.CARTOGRAPHY_DESK.get(), containerId);
        this.access = access;
        data.set(DATA_SPAN, 1);
        addSlot(new Slot(input, SLOT_A, 15, 41) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof AtlasItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(input, SLOT_B, 15, 63) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof AtlasItem;
            }
        });
        addSlot(new Slot(input, SLOT_PAPER, 37, 63) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER) || stack.is(ItemTags.SIGNS);
            }
        });
        addSlot(new Slot(result, 0, 151, 61) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consume(player);
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 106 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 164));
        }
        addDataSlots(data);
    }

    public int mode() {
        return data.get(DATA_MODE);
    }

    /** Blocks each panel of a printed wall map covers. */
    public int span() {
        return WallPrint.SPANS[Math.floorMod(data.get(DATA_SPAN), WallPrint.SPANS.length)];
    }

    public Container inputs() {
        return input;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= MODE_COPY && id <= MODE_PRINT) {
            data.set(DATA_MODE, id);
        } else if (id == BUTTON_SPAN) {
            data.set(DATA_SPAN, (data.get(DATA_SPAN) + 1) % WallPrint.SPANS.length);
        } else {
            return false;
        }
        slotsChanged(input);
        return true;
    }

    /** The label typed on the Label tab (server: from the validated payload). */
    public void setLabel(String raw) {
        String clean = NameFilter.clean(raw == null ? "" : raw, AtlasData.MAX_LABEL);
        if (!clean.isEmpty() && NameFilter.check(clean, NeroMapsConfig.nameDenyList()) != NameFilter.Verdict.OK) {
            clean = "";
        }
        label = clean;
        slotsChanged(input);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == input) {
            result.setItem(0, compute());
            broadcastChanges();
        }
    }

    /** The output for the current tab and inputs, or empty. Pure over the slot contents. */
    ItemStack compute() {
        ItemStack a = input.getItem(SLOT_A);
        ItemStack b = input.getItem(SLOT_B);
        ItemStack p = input.getItem(SLOT_PAPER);
        AtlasData da = a.getItem() instanceof AtlasItem ? AtlasItem.data(a) : null;
        AtlasData db = b.getItem() instanceof AtlasItem ? AtlasItem.data(b) : null;
        switch (mode()) {
            case MODE_COPY -> {
                if (da != null && da.filled() && db != null && b.is(ModItems.ATLAS_BLANK.get())) {
                    return atlas(new AtlasData(da.dimension(), da.regions(), db.pages(), db.binding(), da.label()));
                }
            }
            case MODE_MERGE -> {
                if (da != null && db != null && da.filled() && db.filled() && da.dimension().equals(db.dimension())) {
                    return atlas(da.merge(db));
                }
            }
            case MODE_LABEL -> {
                if (da != null && !label.isEmpty()) {
                    ItemStack out = a.copyWithCount(1);
                    out.set(ModDataComponents.ATLAS.get(), da.withLabel(label));
                    return out;
                }
            }
            case MODE_PRINT -> {
                if (p.is(ItemTags.SIGNS)) {
                    return new ItemStack(ModBlocks.SIGNPOST.get());
                }
                int[] e = da == null ? null : da.extent();
                if (da != null && e != null && p.is(Items.PAPER)) {
                    ItemStack out = new ItemStack(ModItems.WALL_MAP.get());
                    int cx = ((e[0] + e[2] + 1) * 16) / 2;
                    int cz = ((e[1] + e[3] + 1) * 16) / 2;
                    out.set(ModDataComponents.WALL_PRINT.get(), new WallPrint(da.dimension(), cx, cz, span()));
                    return out;
                }
            }
            default -> {
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack atlas(AtlasData d) {
        ItemStack out = new ItemStack(ModItems.ATLAS.get());
        out.set(ModDataComponents.ATLAS.get(), d);
        return out;
    }

    /** Uses the inputs for the output just taken, and plays the tab's sound. */
    private void consume(Player player) {
        String sound;
        switch (mode()) {
            case MODE_COPY -> {
                input.removeItem(SLOT_B, 1);
                sound = "desk.copy";
            }
            case MODE_MERGE -> {
                input.removeItem(SLOT_A, 1);
                input.removeItem(SLOT_B, 1);
                sound = "atlas.merge";
            }
            case MODE_LABEL -> {
                input.removeItem(SLOT_A, 1);
                sound = "signpost.write";
            }
            default -> {
                input.removeItem(SLOT_PAPER, 1);
                sound = "desk.print";
            }
        }
        SoundEvent e = ModSounds.get(sound);
        access.execute((level, pos) -> {
            if (e != null) {
                level.playSound(null, pos, e, SoundSource.BLOCKS, 0.8f, 1f);
            }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CARTOGRAPHY_DESK.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        result.removeItemNoUpdate(0);
        access.execute((level, pos) -> clearContainer(player, input));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int end = slots.size();
        if (index == SLOT_OUT) {
            if (!moveItemStackTo(stack, SLOT_OUT + 1, end, true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, copy);
            return copy;
        }
        if (index < INPUTS) {
            if (!moveItemStackTo(stack, SLOT_OUT + 1, end, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
