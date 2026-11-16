package za.co.neroland.neromaps.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.block.MapBlockProperties;
import za.co.neroland.neromaps.block.SignpostBlockEntity;
import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ClientWaypoints;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.platform.Services;

/**
 * The signpost editor: label (24 characters, filtered on the server like a waypoint name),
 * glyph, colour (named, never colour alone) and "Show on maps (public)". No owner name is shown anywhere, because
 * none exists. Client only; the server validates reach, the block and the label.
 */
public final class SignpostEditScreen extends Screen {

    private final Payloads.SignpostOpen open;
    private int glyph;
    private int colour;
    private boolean shown;
    private @Nullable EditBox label;

    public SignpostEditScreen(Payloads.SignpostOpen open) {
        super(Component.translatable("gui.neromaps.signpost.title"));
        this.open = open;
        this.glyph = Math.floorMod(open.glyph(), SignpostBlockEntity.GLYPHS);
        this.colour = Math.floorMod(open.colour(), MapBlockProperties.MARKER_COLOURS.length);
        this.shown = open.shown();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = Math.max(20, height / 2 - 60);
        label = new EditBox(font, cx - 100, top + 14, 200, 20, Component.translatable("gui.neromaps.signpost.label"));
        label.setMaxLength(SignpostBlockEntity.MAX_LABEL);
        label.setValue(open.label());
        addRenderableWidget(label);
        int row = top + 40;
        addRenderableWidget(Button.builder(glyphLabel(), b -> {
            glyph = (glyph + 1) % SignpostBlockEntity.GLYPHS;
            b.setMessage(glyphLabel());
        }).bounds(cx - 100, row, 98, 20).build());
        addRenderableWidget(Button.builder(colourLabel(), b -> {
            colour = (colour + 1) % MapBlockProperties.MARKER_COLOURS.length;
            b.setMessage(colourLabel());
        }).bounds(cx + 2, row, 98, 20).build());
        row += 24;
        addRenderableWidget(Button.builder(publicLabel(), b -> {
            shown = !shown;
            b.setMessage(publicLabel());
        }).bounds(cx - 100, row, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.neromaps.signpost.public.tooltip"))).build());
        row += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.signpost.save"), b -> save())
                .bounds(cx - 100, row, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.signpost.cancel"), b -> close())
                .bounds(cx + 2, row, 98, 20).build());
    }

    private Component glyphLabel() {
        return Component.translatable("gui.neromaps.waypoint.glyph_named",
                Component.translatable("gui.neromaps.glyph." + ClientWaypoints.GLYPHS[glyph]));
    }

    private Component colourLabel() {
        return Component.translatable("gui.neromaps.waypoint.colour").append(": ").append(
                Component.translatable("gui.neromaps.colour." + MapBlockProperties.MARKER_COLOUR_NAMES[colour]));
    }

    private Component publicLabel() {
        return Component.translatable("gui.neromaps.signpost.public").append(": ")
                .append(Component.translatable(shown ? "neromaps.command.on" : "neromaps.command.off"));
    }

    private void save() {
        String text = label == null ? open.label() : label.getValue();
        Services.NETWORK.sendToServer(new Payloads.SignpostEdit(open.pos(), text, glyph, colour, shown));
        close();
    }

    private void close() {
        ClientMap.setScreen(Minecraft.getInstance(), null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawLabels(GuiGraphicsExtractor g) {
        int cx = width / 2;
        int top = Math.max(20, height / 2 - 60);
        g.text(font, title.getString(), cx - 100, top - 4, MapPalette.TEXT);
        g.text(font, Component.translatable("gui.neromaps.signpost.label").getString(), cx - 100, top + 4,
                MapPalette.TEXT);
        int sw = MapBlockProperties.MARKER_COLOURS[colour];
        g.fill(cx + 104, top + 40, cx + 124, top + 60, 0xFF000000 | sw);
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawLabels(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawLabels(g);
    }
    *///?}
}
