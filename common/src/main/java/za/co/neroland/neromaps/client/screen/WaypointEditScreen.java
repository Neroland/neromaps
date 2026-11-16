package za.co.neroland.neromaps.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ClientState;
import za.co.neroland.neromaps.client.ClientWaypoints;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.store.Waypoint;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * Create or edit one waypoint: name (32 characters, filtered on the server too), position, colour, glyph, beam and
 * distance label, and "temporary" (client memory only) when creating. Client only.
 */
public final class WaypointEditScreen extends Screen {

    private final Waypoint original;
    private final boolean create;
    private boolean temporary;
    private int colour;
    private int glyph;
    private boolean beam;
    private boolean label;
    private @Nullable EditBox name;
    private @Nullable EditBox x;
    private @Nullable EditBox y;
    private @Nullable EditBox z;
    private final @Nullable Screen parent;

    public WaypointEditScreen(Waypoint w, boolean create, boolean temporary) {
        this(w, create, temporary, null);
    }

    public WaypointEditScreen(Waypoint w, boolean create, boolean temporary, @Nullable Screen parent) {
        super(Component.translatable("gui.neromaps.waypoint.edit_title"));
        this.original = w;
        this.create = create;
        this.temporary = temporary || w.id() < 0;
        this.parent = parent;
        this.beam = w.beam();
        this.label = w.distanceLabel();
        for (int i = 0; i < ClientWaypoints.PALETTE.length; i++) {
            if (ClientWaypoints.PALETTE[i] == w.colour()) {
                colour = i;
            }
        }
        for (int i = 0; i < ClientWaypoints.GLYPHS.length; i++) {
            if (ClientWaypoints.glyph(i).equals(w.icon())) {
                glyph = i;
            }
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int top = Math.max(20, height / 2 - 90);
        name = new EditBox(font, cx - 100, top + 12, 200, 20, Component.translatable("gui.neromaps.waypoint.name"));
        name.setMaxLength(NameFilter.MAX_NAME);
        name.setValue(original.name());
        addRenderableWidget(name);
        x = coord(cx - 100, top + 46, original.x());
        y = coord(cx - 32, top + 46, original.y());
        z = coord(cx + 36, top + 46, original.z());
        int row = top + 74;
        addRenderableWidget(Button.builder(colourLabel(), b -> {
            colour = (colour + 1) % ClientWaypoints.PALETTE.length;
            b.setMessage(colourLabel());
        }).bounds(cx - 100, row, 98, 20).build());
        addRenderableWidget(Button.builder(glyphLabel(), b -> {
            glyph = (glyph + 1) % ClientWaypoints.GLYPHS.length;
            b.setMessage(glyphLabel());
        }).bounds(cx + 2, row, 98, 20).build());
        row += 24;
        addRenderableWidget(Button.builder(toggle("gui.neromaps.waypoint.beam", beam), b -> {
            beam = !beam;
            b.setMessage(toggle("gui.neromaps.waypoint.beam", beam));
        }).bounds(cx - 100, row, 98, 20).build());
        addRenderableWidget(Button.builder(toggle("gui.neromaps.waypoint.show_label", label), b -> {
            label = !label;
            b.setMessage(toggle("gui.neromaps.waypoint.show_label", label));
        }).bounds(cx + 2, row, 98, 20).build());
        row += 24;
        if (create && ClientState.INSTANCE.serverHasMod()) {
            addRenderableWidget(Button.builder(toggle("gui.neromaps.waypoint.temporary", temporary), b -> {
                temporary = !temporary;
                b.setMessage(toggle("gui.neromaps.waypoint.temporary", temporary));
            }).bounds(cx - 100, row, 200, 20).build());
            row += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.waypoint.save"), b -> save())
                .bounds(cx - 100, row, 64, 20).build());
        if (!create) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.waypoint.delete"), b -> {
                ClientWaypoints.delete(original);
                close();
            }).bounds(cx - 32, row, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.waypoint.cancel"), b -> close())
                .bounds(cx + 36, row, 64, 20).build());
    }

    private EditBox coord(int bx, int by, int value) {
        EditBox b = new EditBox(font, bx, by, 64, 20, Component.literal(""));
        b.setMaxLength(9);
        b.setValue(Integer.toString(value));
        addRenderableWidget(b);
        return b;
    }

    private Component colourLabel() {
        return Component.translatable("gui.neromaps.waypoint.colour").append(": ").append(
                Component.translatable("gui.neromaps.colour." + ClientWaypoints.PALETTE_NAMES[colour]));
    }

    private Component glyphLabel() {
        return Component.translatable("gui.neromaps.waypoint.glyph_named",
                Component.translatable("gui.neromaps.glyph." + ClientWaypoints.GLYPHS[glyph]));
    }

    private static Component toggle(String key, boolean on) {
        return Component.translatable(key).append(": ")
                .append(Component.translatable(on ? "neromaps.command.on" : "neromaps.command.off"));
    }

    private static int parse(@Nullable EditBox box, int fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        String n = NameFilter.clean(name == null ? original.name() : name.getValue());
        if (n.isEmpty()) {
            n = original.name();
        }
        Waypoint w = new Waypoint(original.id(), original.set(), n, ClientWaypoints.PALETTE[colour],
                ClientWaypoints.glyph(glyph), original.dimension(), parse(x, original.x()), parse(y, original.y()),
                parse(z, original.z()), original.shared(), beam, label);
        ClientWaypoints.save(w, temporary, create);
        close();
    }

    private void close() {
        ClientMap.setScreen(Minecraft.getInstance(), parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawLabels(GuiGraphicsExtractor g) {
        int cx = width / 2;
        int top = Math.max(20, height / 2 - 90);
        g.text(font, title.getString(), cx - 100, top - 4, MapPalette.TEXT);
        g.text(font, Component.translatable("gui.neromaps.waypoint.name").getString(), cx - 100, top + 2,
                MapPalette.TEXT);
        g.text(font, "X", cx - 100, top + 36, MapPalette.TEXT);
        g.text(font, "Y", cx - 32, top + 36, MapPalette.TEXT);
        g.text(font, "Z", cx + 36, top + 36, MapPalette.TEXT);
        int sw = ClientWaypoints.PALETTE[colour];
        g.fill(cx + 104, top + 74, cx + 124, top + 94, 0xFF000000 | sw);
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
