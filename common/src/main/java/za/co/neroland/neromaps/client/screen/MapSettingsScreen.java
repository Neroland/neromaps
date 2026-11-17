package za.co.neroland.neromaps.client.screen;

import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ClientState;
import za.co.neroland.neromaps.client.hud.MinimapHud;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.colour.MapStyle;
import za.co.neroland.neromaps.engine.MinimapFrame;
import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.settings.MapPresets;
import za.co.neroland.neromaps.settings.MinimapSettings;

/**
 * The NeroMaps settings screen: tabs on the left, labelled controls in the middle, and a
 * live preview on the right that is the real minimap drawn with the current values. Every control has a label and a
 * tooltip; a control the server policy forbids stays visible, cannot be changed, and its tooltip says which policy
 * locked it. Changes apply at once to the preview and are written when the screen closes; "Reset tab" restores the
 * values the screen opened with. The Presets tab applies the five built-in presets and the player's own, saves,
 * renames and deletes them, and exports or imports a preset as JSON text through the clipboard. Layers and sharing
 * live in the world map's side panel. Client only; nothing here is player data on the server.
 */
public final class MapSettingsScreen extends Screen {

    private enum Tab { MINIMAP, STYLE, READOUTS, WAYPOINTS, PRESETS, ACCESSIBILITY }

    private static final int ROW = 22;
    /** Padding between the preview well's edge and the minimap's map area (room for the frame and north tab). */
    private static final int WELL_PAD = 12;
    private static Tab lastTab = Tab.MINIMAP;

    private final @Nullable Screen parent;
    private final Properties opened;
    private Tab tab = lastTab;
    private int left;
    private int top;
    private int controlsX;
    private int controlsW;
    private int previewX;
    private int previewY;
    private int row;
    private @Nullable Component status;
    private @Nullable EditBox presetName;

    public MapSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.neromaps.settings.title"));
        this.parent = parent;
        this.opened = ClientMap.SETTINGS.toProperties();
    }

    private static MinimapSettings s() {
        return ClientMap.SETTINGS;
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 8, 540);
        left = (width - panelW) / 2;
        top = 24;
        boolean narrow = panelW < 460;
        int tabW = narrow ? 70 : 96;
        controlsX = left + tabW + 6;
        int previewW = s().size + WELL_PAD * 2 + 12;
        controlsW = Math.max(150, Math.min(220, panelW - tabW - 12 - previewW));
        previewX = controlsX + controlsW + 12;
        previewY = top + 4;
        int ty = top;
        for (Tab t : Tab.values()) {
            String key = "gui.neromaps.settings.tab." + t.name().toLowerCase(java.util.Locale.ROOT);
            Button b = addRenderableWidget(Button.builder(Component.translatable(key), x -> {
                tab = t;
                lastTab = t;
                status = null;
                rebuildWidgets();
            }).bounds(left, ty, tabW, 20).tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).build());
            b.active = t != tab;
            ty += ROW;
        }
        row = top;
        presetName = null;
        notes.clear();
        switch (tab) {
            case MINIMAP -> minimapTab();
            case STYLE -> styleTab();
            case READOUTS -> readoutsTab();
            case WAYPOINTS -> waypointsTab();
            case PRESETS -> presetsTab();
            case ACCESSIBILITY -> accessibilityTab();
        }
        int fy = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.settings.reset_tab"), b -> {
            s().fromProperties(opened);
            s().changed();
            rebuildWidgets();
        }).bounds(width / 2 - 104, fy, 100, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.neromaps.settings.reset_tab.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.settings.done"), b -> onClose())
                .bounds(width / 2 + 4, fy, 100, 20).build());
    }

    // --- control builders -------------------------------------------------------------------------------------

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "neromaps.command.on" : "neromaps.command.off");
    }

    /** A change made by hand: the settings no longer match a preset. */
    private static void touched() {
        s().preset = "";
        s().changed();
    }

    private void lockable(AbstractWidget w, String tooltipKey, PolicySnapshot.@Nullable Feature lock) {
        if (lock != null && !ClientState.INSTANCE.allows(lock)) {
            w.active = false;
            w.setMessage(w.getMessage().copy().append(" ").append(Component.translatable("gui.neromaps.settings.locked")));
            w.setTooltip(Tooltip.create(Component.translatable(lock.lockKey())));
        } else {
            w.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
    }

    private void toggle(String key, Supplier<Boolean> get, Consumer<Boolean> set,
            PolicySnapshot.@Nullable Feature lock) {
        Component label = Component.translatable(key);
        Button b = addRenderableWidget(Button.builder(label.copy().append(": ").append(onOff(get.get())), x -> {
            set.accept(!get.get());
            touched();
            x.setMessage(label.copy().append(": ").append(onOff(get.get())));
        }).bounds(controlsX, row, controlsW, 20).build());
        lockable(b, key + ".tooltip", lock);
        row += ROW;
    }

    private <E extends Enum<E>> void cycle(String key, E[] values, Supplier<E> get, Consumer<E> set) {
        Component label = Component.translatable(key);
        String stem = key + ".";
        Button b = addRenderableWidget(Button.builder(label.copy().append(": ")
                .append(Component.translatable(stem + get.get().name().toLowerCase(java.util.Locale.ROOT))), x -> {
                    set.accept(values[(get.get().ordinal() + 1) % values.length]);
                    touched();
                    rebuildWidgets();
                }).bounds(controlsX, row, controlsW, 20).build());
        b.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        row += ROW;
    }

    private void slider(String key, double min, double max, double step, Supplier<Double> get, Consumer<Double> set,
            DoubleFunction<Component> text) {
        double span = max - min;
        double start = (Math.max(min, Math.min(max, get.get())) - min) / span;
        AbstractSliderButton b = new AbstractSliderButton(controlsX, row, controlsW, 20, Component.empty(), start) {
            {
                updateMessage();
            }

            private double current() {
                double v = min + value * span;
                return Math.round(v / step) * step;
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(key).append(": ").append(text.apply(current())));
            }

            @Override
            protected void applyValue() {
                set.accept(current());
                touched();
            }
        };
        b.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        addRenderableWidget(b);
        row += ROW;
    }

    private record Note(int x, int y, Component text) { }

    private final java.util.List<Note> notes = new java.util.ArrayList<>();

    // --- tabs --------------------------------------------------------------------------------------------------

    private void minimapTab() {
        String p = "gui.neromaps.settings.minimap.";
        toggle(p + "enabled", () -> s().enabled, v -> s().enabled = v, null);
        cycle(p + "shape", MinimapSettings.Shape.values(), () -> s().shape, v -> {
            s().shape = v;
            if (v == MinimapSettings.Shape.ROUND) {
                s().size = MinimapFrame.snapRound(s().size);
            }
        });
        slider(p + "size", MinimapSettings.MIN_SIZE, MinimapSettings.MAX_SIZE, 8, () -> (double) s().size,
                v -> s().size = s().shape == MinimapSettings.Shape.ROUND ? MinimapFrame.snapRound((int) Math.round(v))
                        : (int) Math.round(v),
                v -> Component.literal(Integer.toString((int) Math.round(v))));
        slider(p + "zoom", -2, 3, 1, () -> Math.log(s().zoom) / Math.log(2), v -> s().zoom = (float) Math.pow(2, v),
                v -> Component.literal(v >= 0 ? (int) Math.pow(2, v) + " px/b" : "1/" + (int) Math.pow(2, -v)));
        slider(p + "opacity", 0.3, 1.0, 0.05, () -> (double) s().opacity, v -> s().opacity = (float) (double) v,
                v -> Component.literal(Math.round(v * 100) + " %"));
        cycle(p + "border", MinimapSettings.Border.values(), () -> s().border, v -> s().border = v);
        toggle(p + "rotation", () -> s().rotate, v -> s().rotate = v, null);
        cycle(p + "anchor", MinimapSettings.Anchor.values(), () -> s().anchor, v -> s().anchor = v);
        slider(p + "margin", 0, 16, 1, () -> (double) s().margin, v -> s().margin = (int) Math.round(v),
                v -> Component.literal(Integer.toString((int) Math.round(v))));
    }

    private void styleTab() {
        String p = "gui.neromaps.settings.style.";
        cycle(p + "style", MapStyle.values(), () -> s().style, v -> s().style = v);
        toggle(p + "night", () -> s().nightDarkening, v -> s().nightDarkening = v, null);
        toggle(p + "grid", () -> s().grid, v -> s().grid = v, PolicySnapshot.Feature.OVERLAYS);
        toggle(p + "chunk_borders", () -> s().chunkBorders, v -> s().chunkBorders = v, PolicySnapshot.Feature.OVERLAYS);
        toggle(p + "cave", () -> s().caveMode, v -> s().caveMode = v, PolicySnapshot.Feature.CAVE_MODE);
    }

    private void readoutsTab() {
        String p = "gui.neromaps.settings.readouts.";
        toggle(p + "coords", () -> s().showCoords, v -> s().showCoords = v, null);
        toggle(p + "facing", () -> s().showFacing, v -> s().showFacing = v, null);
        toggle(p + "biome", () -> s().showBiome, v -> s().showBiome = v, null);
        toggle(p + "dimension", () -> s().showDimension, v -> s().showDimension = v, null);
        toggle(p + "time", () -> s().showTime, v -> s().showTime = v, null);
        toggle(p + "light", () -> s().showLight, v -> s().showLight = v, null);
    }

    private void waypointsTab() {
        String p = "gui.neromaps.settings.waypoints.";
        toggle(p + "on_minimap", () -> s().waypointsOnMinimap, v -> s().waypointsOnMinimap = v, null);
        toggle(p + "beams", () -> s().beams, v -> s().beams = v, null);
        slider(p + "beam_range", 64, 2048, 64, () -> (double) s().beamRange, v -> s().beamRange = (int) Math.round(v),
                v -> Component.literal((int) Math.round(v) + " m"));
        toggle(p + "distance_labels", () -> s().distanceLabels, v -> s().distanceLabels = v, null);
        toggle(p + "death_points", () -> s().deathPointsShown, v -> s().deathPointsShown = v,
                PolicySnapshot.Feature.DEATH_POINTS);
        int limit = ClientState.INSTANCE.policy().waypointLimit();
        Button count = addRenderableWidget(Button.builder(Component.translatable(p + "limit",
                ClientState.INSTANCE.own().size(), limit), b -> { }).bounds(controlsX, row, controlsW, 20).build());
        count.active = false;
        count.setTooltip(Tooltip.create(Component.translatable("gui.neromaps.locked.waypoint_limit", limit)));
        row += ROW;
    }

    private void presetsTab() {
        String p = "gui.neromaps.settings.presets.";
        int half = (controlsW - 4) / 2;
        int i = 0;
        for (String name : ClientMap.PRESETS.names()) {
            int x = controlsX + (i % 2) * (half + 4);
            boolean builtIn = ClientMap.PRESETS.isBuiltIn(name);
            Component label = ClientMap.presetName(name);
            if (name.equals(s().preset)) {
                label = label.copy().append(" *");
            }
            int w = builtIn ? half : half - 22;
            addRenderableWidget(Button.builder(label, b -> {
                ClientMap.PRESETS.apply(name, s());
                rebuildWidgets();
            }).bounds(x, row, w, 20).tooltip(Tooltip.create(builtIn ? Component.translatable(p + name + ".tooltip")
                    : Component.translatable(p + "user.tooltip"))).build());
            if (!builtIn) {
                addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                    ClientMap.PRESETS.delete(name);
                    ClientMap.savePresets();
                    rebuildWidgets();
                }).bounds(x + w + 2, row, 20, 20).tooltip(Tooltip.create(Component.translatable(p + "delete")))
                        .build());
            }
            if (i % 2 == 1) {
                row += ROW;
            }
            i++;
        }
        if (i % 2 == 1) {
            row += ROW;
        }
        row += 4;
        presetName = new EditBox(font, controlsX, row, half + 30, 20, Component.translatable(p + "name"));
        presetName.setMaxLength(MapPresets.MAX_NAME);
        presetName.setTooltip(Tooltip.create(Component.translatable(p + "name.tooltip")));
        addRenderableWidget(presetName);
        addRenderableWidget(Button.builder(Component.translatable(p + "save_as"), b -> {
            EditBox box = presetName;
            String n = box == null ? "" : box.getValue();
            if (!ClientMap.PRESETS.saveAs(n, s())) {
                status = Component.translatable(p + "save_failed");
                return;
            }
            ClientMap.savePresets();
            status = null;
            rebuildWidgets();
        }).bounds(controlsX + half + 34, row, controlsW - half - 34, 20)
                .tooltip(Tooltip.create(Component.translatable(p + "save_as.tooltip"))).build());
        row += ROW;
        String current = s().preset;
        Button rename = addRenderableWidget(Button.builder(Component.translatable(p + "rename"), b -> {
            EditBox box = presetName;
            if (box != null && ClientMap.PRESETS.rename(current, box.getValue())) {
                ClientMap.savePresets();
                status = null;
                rebuildWidgets();
            } else {
                status = Component.translatable(p + "save_failed");
            }
        }).bounds(controlsX, row, controlsW, 20).tooltip(Tooltip.create(Component.translatable(p + "rename.tooltip")))
                .build());
        rename.active = !current.isEmpty() && !ClientMap.PRESETS.isBuiltIn(current)
                && ClientMap.PRESETS.names().contains(current);
        row += ROW;
        addRenderableWidget(Button.builder(Component.translatable(p + "export"), b -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(MapPresets.export(s()));
            status = Component.translatable(p + "exported");
        }).bounds(controlsX, row, half, 20).tooltip(Tooltip.create(Component.translatable(p + "export.tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable(p + "import"), b -> {
            MinimapSettings copy = new MinimapSettings();
            copy.fromProperties(s().toProperties());
            String error = MapPresets.importInto(Minecraft.getInstance().keyboardHandler.getClipboard(), copy);
            if (error != null) {
                status = Component.translatable(p + "import_failed", error);
                return;
            }
            s().fromProperties(copy.toProperties());
            s().preset = "";
            s().changed();
            status = Component.translatable(p + "imported");
            rebuildWidgets();
        }).bounds(controlsX + half + 4, row, half, 20)
                .tooltip(Tooltip.create(Component.translatable(p + "import.tooltip"))).build());
        row += ROW;
        notes.add(new Note(controlsX, row + 2, Component.translatable(p + "cycle_hint")));
    }

    private void accessibilityTab() {
        String p = "gui.neromaps.settings.accessibility.";
        toggle(p + "high_contrast", () -> s().highContrast, v -> s().highContrast = v, null);
        cycle(p + "palette", MinimapSettings.MarkerPalette.values(), () -> s().markerPalette,
                v -> s().markerPalette = v);
        toggle(p + "reduce_motion", () -> s().reduceMotion, v -> s().reduceMotion = v, null);
        toggle(p + "keyline", () -> s().keyline, v -> s().keyline = v, null);
        toggle(p + "solid_plates", () -> s().solidPlates, v -> s().solidPlates = v, null);
        notes.add(new Note(controlsX, row + 4, Component.translatable(p + "subtitles_hint")));
    }

    // --- drawing -------------------------------------------------------------------------------------------------

    private void drawExtras(GuiGraphicsExtractor g) {
        g.text(font, title.getString(), left, 8, MapPalette.TEXT);
        // the preview well: the real minimap, with every value as set right now
        // The well holds the whole minimap as the HUD draws it: frame, north tab and the readout block under it, so
        // the caption below never shares space with the readouts however many are switched on.
        int size = s().size;
        int fw = MinimapFrame.frameWidth(s().border, s().highContrast || s().style == MapStyle.HIGH_CONTRAST);
        int well = size + WELL_PAD * 2;
        int wellH = well + (s().enabled ? fw + za.co.neroland.neromaps.engine.MinimapLayout.readoutHeight(
                za.co.neroland.neromaps.client.Readouts.count) : 0);
        g.fill(previewX - 1, previewY - 1, previewX + well + 1, previewY + wellH + 1, MapPalette.PANEL_EDGE);
        g.fill(previewX, previewY, previewX + well, previewY + wellH, MapPalette.PANEL);
        if (s().enabled) {
            MinimapHud.preview(g, previewX + WELL_PAD - fw, previewY + WELL_PAD - fw);
        } else {
            g.text(font, Component.translatable("gui.neromaps.settings.preview_off").getString(), previewX + 6,
                    previewY + well / 2, MapPalette.TEXT_SECONDARY);
        }
        g.text(font, Component.translatable("gui.neromaps.settings.preview").getString(), previewX,
                previewY + wellH + 5, MapPalette.TEXT_SECONDARY);
        for (Note n : notes) {
            g.text(font, n.text().getString(), n.x(), n.y(), MapPalette.TEXT_SECONDARY);
        }
        if (status != null) {
            g.text(font, status.getString(), controlsX, height - 40, MapPalette.M_YELLOW);
        }
    }

    @Override
    public void onClose() {
        ClientMap.saveSettings();
        ClientMap.setScreen(Minecraft.getInstance(), parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawExtras(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawExtras(g);
    }
    *///?}
}
