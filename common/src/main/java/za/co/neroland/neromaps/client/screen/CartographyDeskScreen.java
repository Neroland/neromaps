package za.co.neroland.neromaps.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.atlas.AtlasData;
import za.co.neroland.neromaps.atlas.AtlasItem;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.menu.CartographyDeskMenu;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.platform.Services;
import za.co.neroland.neromaps.store.ExploredMap;

/**
 * The Cartography Desk screen on Fable's {@code cartography_desk.png}: four tabs (Copy,
 * Merge, Label, Print), the slots each tab uses (the others dimmed), the label field on the Label tab, the span
 * button on the Print tab and a 64 x 64 preview of the atlas coverage. The preview draws only what the atlas holds,
 * which is only what its recorder could see. Every tab and button has a tooltip. CLIENT-ONLY.
 */
public class CartographyDeskScreen extends AbstractContainerScreen<CartographyDeskMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("neromaps",
            "textures/gui/cartography_desk.png");
    private static final String[] TABS = {"copy", "merge", "label", "print"};
    private static final int PREVIEW_X = 81;
    private static final int PREVIEW_Y = 37;
    private static final int PREVIEW = 64;

    private final Button[] tabs = new Button[4];
    private @Nullable EditBox labelBox;
    private @Nullable Button spanButton;
    private @Nullable AtlasData cachedA;
    private @Nullable AtlasData cachedB;
    private final boolean[] gridA = new boolean[PREVIEW * PREVIEW];
    private final boolean[] gridB = new boolean[PREVIEW * PREVIEW];

    public CartographyDeskScreen(CartographyDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CartographyDeskMenu.WIDTH, CartographyDeskMenu.HEIGHT);
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 95;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 4; i++) {
            int id = i;
            tabs[i] = addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.desk.tab." + TABS[i]),
                    b -> click(id)).bounds(leftPos + 7 + i * 27, topPos + 19, 26, 14)
                    .tooltip(Tooltip.create(Component.translatable("gui.neromaps.desk.tab." + TABS[i] + ".tooltip")))
                    .build());
        }
        labelBox = new EditBox(font, leftPos + 14, topPos + 84, 60, 14, Component.translatable("gui.neromaps.desk.label_hint"));
        labelBox.setMaxLength(AtlasData.MAX_LABEL);
        labelBox.setTooltip(Tooltip.create(Component.translatable("gui.neromaps.desk.label_hint")));
        labelBox.setResponder(text -> Services.NETWORK.sendToServer(new Payloads.DeskLabel(text)));
        addRenderableWidget(labelBox);
        spanButton = addRenderableWidget(Button.builder(spanLabel(), b -> click(CartographyDeskMenu.BUTTON_SPAN))
                .bounds(leftPos + 14, topPos + 84, 60, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.neromaps.desk.span.tooltip"))).build());
        refresh();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private Component spanLabel() {
        return Component.translatable("gui.neromaps.desk.span", menu.span());
    }

    private void refresh() {
        int mode = menu.mode();
        for (int i = 0; i < 4; i++) {
            if (tabs[i] != null) {
                tabs[i].active = i != mode;
            }
        }
        if (labelBox != null) {
            labelBox.visible = mode == CartographyDeskMenu.MODE_LABEL;
            labelBox.active = labelBox.visible;
        }
        if (spanButton != null) {
            spanButton.visible = mode == CartographyDeskMenu.MODE_PRINT;
            spanButton.setMessage(spanLabel());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        //? if >=1.21.2 {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u, v, w, h, w, h, 256, 256);
        //?} else {
        /*g.blit(TEXTURE, x, y, w, h, u, v, w, h, 256, 256);
        *///?}
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos;
        int y = topPos;
        blit(g, x, y, 0, 0, CartographyDeskMenu.WIDTH, CartographyDeskMenu.HEIGHT);
        int mode = menu.mode();
        blit(g, x + 7 + mode * 27, y + 19, 176, 0, 26, 14);
        // dim the slots this tab does not use
        boolean usesB = mode == CartographyDeskMenu.MODE_COPY || mode == CartographyDeskMenu.MODE_MERGE;
        boolean usesPaper = mode == CartographyDeskMenu.MODE_PRINT;
        if (!usesB) {
            g.fill(x + 14, y + 62, x + 32, y + 80, 0x80141C26);
        }
        if (!usesPaper) {
            g.fill(x + 36, y + 62, x + 54, y + 80, 0x80141C26);
        }
        drawPreview(g, x + PREVIEW_X, y + PREVIEW_Y, mode);
        super.extractContents(g, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphicsExtractor g, int px, int py, int mode) {
        g.fill(px, py, px + PREVIEW, py + PREVIEW, MapPalette.VOID);
        AtlasData a = atlasIn(CartographyDeskMenu.SLOT_A);
        AtlasData b = mode == CartographyDeskMenu.MODE_MERGE ? atlasIn(CartographyDeskMenu.SLOT_B) : null;
        if (a != cachedA || b != cachedB) {
            cachedA = a;
            cachedB = b;
            int[] box = union(a == null ? null : a.extent(), b == null ? null : b.extent());
            rasterise(a, box, gridA);
            rasterise(b, box, gridB);
        }
        for (int row = 0; row < PREVIEW; row++) {
            for (int col = 0; col < PREVIEW; col++) {
                int i = row * PREVIEW + col;
                if (gridA[i] || gridB[i]) {
                    int c = gridA[i] && gridB[i] ? MapPalette.MAP_ACCENT_BRIGHT
                            : gridA[i] ? MapPalette.MAP_ACCENT : MapPalette.M_SKY;
                    int end = col;
                    while (end + 1 < PREVIEW && (gridA[row * PREVIEW + end + 1] || gridB[row * PREVIEW + end + 1])
                            && gridA[row * PREVIEW + end + 1] == gridA[i] && gridB[row * PREVIEW + end + 1] == gridB[i]) {
                        end++;
                    }
                    g.fill(px + col, py + row, px + end + 1, py + row + 1, c);
                    col = end;
                }
            }
        }
    }

    private @Nullable AtlasData atlasIn(int slot) {
        ItemStack s = menu.inputs().getItem(slot);
        if (!(s.getItem() instanceof AtlasItem)) {
            return null;
        }
        AtlasData d = AtlasItem.data(s);
        return d.filled() ? d : null;
    }

    private static int @Nullable [] union(int @Nullable [] a, int @Nullable [] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    /** Downsamples covered chunks to the 64 x 64 preview (a cell is lit when any chunk in it is covered). */
    private static void rasterise(@Nullable AtlasData d, int @Nullable [] box, boolean[] grid) {
        java.util.Arrays.fill(grid, false);
        if (d == null || box == null) {
            return;
        }
        int w = box[2] - box[0] + 1;
        int h = box[3] - box[1] + 1;
        int side = Math.max(1, Math.max(w, h));
        for (AtlasData.Region r : d.regions()) {
            long[] bits = r.bits();
            for (int i = 0; i < ExploredMap.WORDS * 64; i++) {
                if ((bits[i >>> 6] & (1L << (i & 63))) == 0) {
                    continue;
                }
                int cx = (r.regionX() << ExploredMap.REGION_SHIFT) + (i & (ExploredMap.REGION_CHUNKS - 1)) - box[0];
                int cz = (r.regionZ() << ExploredMap.REGION_SHIFT) + (i >>> ExploredMap.REGION_SHIFT) - box[1];
                int gx = (int) ((long) cx * PREVIEW / side);
                int gz = (int) ((long) cz * PREVIEW / side);
                if (gx >= 0 && gz >= 0 && gx < PREVIEW && gz < PREVIEW) {
                    grid[gz * PREVIEW + gx] = true;
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, MapPalette.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MapPalette.TEXT_SECONDARY, false);
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (labelBox != null && labelBox.visible && labelBox.isFocused() && !event.isEscape()) {
            labelBox.keyPressed(event);
            return true; // typing "e" never closes the desk
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (labelBox != null && labelBox.visible && labelBox.isFocused() && keyCode != 256) {
            labelBox.keyPressed(keyCode, scanCode, modifiers);
            return true; // typing "e" never closes the desk
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}
}
