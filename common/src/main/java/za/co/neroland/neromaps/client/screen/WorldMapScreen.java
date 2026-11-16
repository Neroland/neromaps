package za.co.neroland.neromaps.client.screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ClientState;
import za.co.neroland.neromaps.client.ColourTables;
import za.co.neroland.neromaps.client.MapKeys;
import za.co.neroland.neromaps.client.texture.MapTexture;
import za.co.neroland.neromaps.client.texture.WorldMapTextures;
import za.co.neroland.neromaps.colour.MapPalette;
import za.co.neroland.neromaps.colour.RasterOptions;
import za.co.neroland.neromaps.engine.MapStore;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileKey;
import za.co.neroland.neromaps.tile.TileLod;

/**
 * The full-screen world map: pan (drag), zoom (wheel, about the cursor), dimension switcher over every dimension
 * this client has mapped on this server, follow-me, and a distance measure (click two points). Far zoom draws
 * level-of-detail tiles built on the I/O worker. Layer and waypoint panels are placeholders until their stages.
 * Shows only terrain this client captured itself. Client only.
 */
public final class WorldMapScreen extends Screen {

    /** GUI pixels per block at each zoom step. */
    private static final float[] ZOOMS = {1f / 16, 1f / 8, 1f / 4, 1f / 2, 1f, 2f, 4f, 8f};
    private static final Identifier PRISM = Identifier.fromNamespaceAndPath("neromaps", "textures/gui/map_player_prism.png");

    private final RasterOptions raster = new RasterOptions();
    private @Nullable WorldMapTextures textures;
    private int zoom = 4;
    private double camX;
    private double camZ;
    private boolean follow = true;
    private boolean measuring;
    private double measureAx = Double.NaN;
    private double measureAz;
    private double measureBx = Double.NaN;
    private double measureBz;
    private final List<String> dimensions = new ArrayList<>();
    private int dimension;
    private @Nullable MapStore foreign;
    private @Nullable Button dimButton;
    private @Nullable Button followButton;
    private @Nullable Button measureButton;
    private boolean dragging;

    public WorldMapScreen() {
        super(Component.translatable("gui.neromaps.worldmap.title"));
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            camX = p.getX();
            camZ = p.getZ();
        }
        zoom = Math.max(0, Math.min(ZOOMS.length - 1, zoomIndex(ClientMap.SETTINGS.zoom)));
    }

    private static int zoomIndex(float z) {
        for (int i = 0; i < ZOOMS.length; i++) {
            if (ZOOMS[i] >= z) {
                return i;
            }
        }
        return ZOOMS.length - 1;
    }

    @Override
    protected void init() {
        MapStore live = ClientMap.store();
        if (live != null) {
            live.flush(); // LOD builds read the cache; queue the saves ahead of them
        }
        int budgetTextures = (int) (ClientMap.SETTINGS.textureBudgetMb * 1048576L / (Tile.SIZE * Tile.SIZE * 8L)) - 8;
        if (textures == null) {
            textures = new WorldMapTextures(budgetTextures);
        }
        listDimensions();
        int y = 4;
        dimButton = addRenderableWidget(Button.builder(dimLabel(), b -> cycleDimension()).bounds(4, y, 150, 20).build());
        followButton = addRenderableWidget(Button.builder(followLabel(), b -> {
            follow = !follow;
            b.setMessage(followLabel());
        }).bounds(158, y, 90, 20).build());
        measureButton = addRenderableWidget(Button.builder(measureLabel(), b -> {
            measuring = !measuring;
            measureAx = Double.NaN;
            measureBx = Double.NaN;
            b.setMessage(measureLabel());
        }).bounds(252, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.worldmap.settings"), b ->
                ClientMap.setScreen(Minecraft.getInstance(),
                        new za.co.neroland.neromaps.client.screen.MapSettingsScreen(this)))
                .bounds(346, y, 70, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.neromaps.worldmap.settings.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.worldmap.layers"), b -> {
            panel = panel == Panel.LAYERS ? Panel.NONE : Panel.LAYERS;
            page = 0;
            rebuildWidgets();
        }).bounds(width - 98, y, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.worldmap.waypoints.header"), b -> {
            panel = panel == Panel.WAYPOINTS ? Panel.NONE : Panel.WAYPOINTS;
            page = 0;
            rebuildWidgets();
        }).bounds(width - 98, y + 24, 94, 20).build());
        if (panel == Panel.LAYERS) {
            panelRows = layerRows();
        } else if (panel == Panel.WAYPOINTS) {
            panelRows = waypointRows();
        } else {
            panelRows = 0;
        }
    }

    // --- side panels (waypoints, layers) -------------------------------------------------------------------

    private enum Panel { NONE, LAYERS, WAYPOINTS }

    private static final int ROW = 22;
    private static final int PANEL_W = 214;
    private Panel panel = Panel.NONE;
    private int page;
    private int panelRows;
    private int lastStateVersion = -1;

    private int panelX() {
        return width - PANEL_W - 4;
    }

    private int rowsPerPage() {
        return Math.max(3, (height - 80) / ROW - 1);
    }

    /** Adds the paging buttons and returns the first row index of this page. */
    private int pager(int total) {
        int per = rowsPerPage();
        int pages = Math.max(1, (total + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        if (pages > 1) {
            int y = 52 + per * ROW;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(panelX(), y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + "/" + pages), b -> { })
                    .bounds(panelX() + 22, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min(pages - 1, page + 1);
                rebuildWidgets();
            }).bounds(panelX() + 64, y, 20, 20).build());
        }
        return page * per;
    }

    private int layerRows() {
        List<za.co.neroland.neromaps.network.Payloads.LayerInfo> infos =
                new ArrayList<>(ClientState.INSTANCE.catalogue().values());
        int first = pager(infos.size());
        int per = rowsPerPage();
        MinimapSettings s = ClientMap.SETTINGS;
        int y = 52;
        for (int i = first; i < Math.min(infos.size(), first + per); i++) {
            za.co.neroland.neromaps.network.Payloads.LayerInfo info = infos.get(i);
            boolean w = s.layerOnWorld(info.id(), info.defaultVisible());
            boolean m = s.layerOnMinimap(info.id(), info.defaultOnMinimap());
            addRenderableWidget(Button.builder(Component.translatable(w ? "gui.neromaps.layers.world_on"
                    : "gui.neromaps.layers.world_off"), b -> {
                        s.setLayerOnWorld(info.id(), info.defaultVisible(), !w);
                        ClientMap.saveSettings();
                        rebuildWidgets();
                    }).bounds(panelX(), y, 34, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(m ? "gui.neromaps.layers.minimap_on"
                    : "gui.neromaps.layers.minimap_off"), b -> {
                        s.setLayerOnMinimap(info.id(), info.defaultOnMinimap(), !m);
                        ClientMap.saveSettings();
                        rebuildWidgets();
                    }).bounds(panelX() + 36, y, 34, 20).build());
            y += ROW;
        }
        return Math.min(infos.size() - first, per);
    }

    /** One waypoint panel row: what it is and what clicking it does. */
    private record WpRow(Component text, int colour, @Nullable Runnable edit, @Nullable Runnable delete,
            @Nullable Runnable go) {

        WpRow(Component text, int colour, @Nullable Runnable edit, @Nullable Runnable delete) {
            this(text, colour, edit, delete, null);
        }
    }

    /** The waypoint panel's search text (case-insensitive, matched against the row text). */
    private String search = "";
    private @Nullable net.minecraft.client.gui.components.EditBox searchBox;
    private boolean searchChanged;

    private void centreOn(int x, int z) {
        follow = false;
        if (followButton != null) {
            followButton.setMessage(followLabel());
        }
        camX = x + 0.5;
        camZ = z + 0.5;
    }

    private List<WpRow> wpRowList() {
        ClientState st = ClientState.INSTANCE;
        List<WpRow> rows = new ArrayList<>();
        za.co.neroland.neromaps.network.Payloads.Shares sh = st.shares();
        if (sh != null) {
            for (za.co.neroland.neromaps.network.Payloads.ShareLine l : sh.pending()) {
                rows.add(new WpRow(Component.translatable("gui.neromaps.sharing.pending_row", l.name(),
                        Component.translatable("neromaps.share.kind." + kind(l.scope()))), MapPalette.M_YELLOW,
                        () -> answer(l.requestId(), true), () -> answer(l.requestId(), false)));
            }
        }
        for (za.co.neroland.neromaps.store.Waypoint w : st.own()) {
            rows.add(new WpRow(Component.literal(w.name()), w.colour(), () -> edit(w, false), () -> {
                za.co.neroland.neromaps.client.ClientWaypoints.delete(w);
            }, () -> centreOn(w.x(), w.z())));
        }
        for (za.co.neroland.neromaps.store.Waypoint w : st.temporary()) {
            rows.add(new WpRow(Component.translatable("gui.neromaps.waypoint.temp_row", w.name()), w.colour(),
                    () -> edit(w, true), () -> za.co.neroland.neromaps.client.ClientWaypoints.delete(w),
                    () -> centreOn(w.x(), w.z())));
        }
        for (za.co.neroland.neromaps.network.Payloads.SharedWaypoint sw : st.shared()) {
            rows.add(new WpRow(Component.translatable("gui.neromaps.waypoint.shared_row", sw.waypoint().name(),
                    sw.sharerName()), sw.waypoint().colour(), null, null));
        }
        for (int i = 0; i < st.deaths().size(); i++) {
            int index = i;
            za.co.neroland.neromaps.store.DeathPoint d = st.deaths().get(i);
            rows.add(new WpRow(Component.translatable("gui.neromaps.waypoint.death_row", i + 1, d.x(), d.z()),
                    0xD55E00, null, () -> za.co.neroland.neromaps.client.ClientWaypoints.deleteDeath(index)));
        }
        if (!search.isBlank()) {
            String q = search.trim().toLowerCase(java.util.Locale.ROOT);
            rows.removeIf(r -> !r.text().getString().toLowerCase(java.util.Locale.ROOT).contains(q));
        }
        return rows;
    }

    private static String kind(za.co.neroland.neromaps.store.ShareScope s) {
        return switch (s.kind()) {
            case WAYPOINT -> "waypoint";
            case EXPLORED_MAP -> "map";
            case LIVE_POSITION -> "position";
        };
    }

    private void answer(long requestId, boolean accept) {
        za.co.neroland.neromaps.platform.Services.NETWORK.sendToServer(
                new za.co.neroland.neromaps.network.Payloads.ShareAction(accept
                        ? za.co.neroland.neromaps.network.Payloads.ShareAction.ACCEPT
                        : za.co.neroland.neromaps.network.Payloads.ShareAction.DECLINE, "", requestId,
                        za.co.neroland.neromaps.store.ShareScope.livePosition(), false));
    }

    private void edit(za.co.neroland.neromaps.store.Waypoint w, boolean temporary) {
        ClientMap.setScreen(Minecraft.getInstance(), new WaypointEditScreen(w, false, temporary, this));
    }

    private int waypointRows() {
        List<WpRow> rows = wpRowList();
        int per = rowsPerPage() - 2;
        int first = pager(rows.size() + 1);
        int y = 52;
        net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(font,
                panelX(), y, PANEL_W, 18, Component.translatable("gui.neromaps.worldmap.search"));
        box.setMaxLength(32);
        box.setValue(search);
        box.setHint(Component.translatable("gui.neromaps.worldmap.search"));
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.neromaps.worldmap.search.tooltip")));
        box.setResponder(text -> {
            if (!text.equals(search)) {
                search = text;
                page = 0;
                searchChanged = true;
            }
        });
        searchBox = addRenderableWidget(box);
        y += ROW;
        if (first == 0) {
            addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.worldmap.waypoints.new"), b -> {
                LocalPlayer p = Minecraft.getInstance().player;
                int py = p == null ? 64 : p.getBlockY();
                newWaypointAt((int) Math.floor(camX), py, (int) Math.floor(camZ));
            }).bounds(panelX(), y, PANEL_W, 20).build());
            y += ROW;
        }
        int start = Math.max(0, first - 1);
        int shown = 0;
        for (int i = start; i < rows.size() && shown < per; i++, shown++) {
            WpRow r = rows.get(i);
            if (r.go() != null) {
                Runnable go = r.go();
                addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.worldmap.go"),
                        b -> go.run()).bounds(panelX() + PANEL_W - 114, y, 36, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.neromaps.worldmap.go.tooltip"))).build());
            }
            if (r.edit() != null) {
                Runnable e = r.edit();
                addRenderableWidget(Button.builder(Component.translatable("gui.neromaps.waypoint.edit"),
                        b -> e.run()).bounds(panelX() + PANEL_W - 76, y, 36, 20).build());
            }
            if (r.delete() != null) {
                Runnable d = r.delete();
                addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                    d.run();
                }).bounds(panelX() + PANEL_W - 38, y, 36, 20).build());
            }
            y += ROW;
        }
        return shown;
    }

    private void newWaypointAt(int x, int y, int z) {
        String name = za.co.neroland.neromaps.text.NameFilter.clean(
                Component.translatable("neromaps.waypoint.default_name", x, z).getString());
        String dim = dimensions.isEmpty() ? ClientMap.dimensionId() : dimensions.get(dimension);
        ClientMap.setScreen(Minecraft.getInstance(), new WaypointEditScreen(new za.co.neroland.neromaps.store.Waypoint(
                0, za.co.neroland.neromaps.store.Waypoint.DEFAULT_SET, name,
                za.co.neroland.neromaps.client.ClientWaypoints.PALETTE[0],
                za.co.neroland.neromaps.client.ClientWaypoints.glyph(0), dim, x, y, z, false, true, true), true, false,
                this));
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        if (panel == Panel.NONE) {
            return;
        }
        int x = panelX();
        g.fill(x - 4, 48, width, height - 16, (MapPalette.PANEL & 0xFFFFFF) | 0xD0000000);
        int y = 52;
        if (panel == Panel.LAYERS) {
            List<za.co.neroland.neromaps.network.Payloads.LayerInfo> infos =
                    new ArrayList<>(ClientState.INSTANCE.catalogue().values());
            if (infos.isEmpty()) {
                g.text(font, Component.translatable("gui.neromaps.layers.none").getString(), x, y + 6,
                        MapPalette.TEXT_SECONDARY);
                return;
            }
            int first = page * rowsPerPage();
            for (int i = first; i < Math.min(infos.size(), first + panelRows); i++) {
                za.co.neroland.neromaps.network.Payloads.LayerInfo info = infos.get(i);
                int[] uv = atlas().uv(info.icon());
                icon(g, x + 74, y + 4, 12, uv);
                g.text(font, Component.translatable(info.titleKey()).getString(), x + 90, y + 6, MapPalette.TEXT);
                y += ROW;
            }
            return;
        }
        List<WpRow> rows = wpRowList();
        int per = rowsPerPage() - 2;
        int first = page * rowsPerPage();
        y += ROW; // the search field
        if (first == 0) {
            y += ROW;
        }
        int start = Math.max(0, first - 1);
        int shown = 0;
        for (int i = start; i < rows.size() && shown < per; i++, shown++) {
            WpRow r = rows.get(i);
            g.fill(x, y + 4, x + 10, y + 14, 0xFF000000 | r.colour());
            String t = font.plainSubstrByWidth(r.text().getString(), PANEL_W - 134);
            g.text(font, t, x + 14, y + 6, MapPalette.TEXT);
            y += ROW;
        }
        if (rows.isEmpty()) {
            g.text(font, Component.translatable("gui.neromaps.waypoint.none").getString(), x, y + 6,
                    MapPalette.TEXT_SECONDARY);
        }
    }

    // --- map overlays: waypoints, positions, layers -------------------------------------------------------------

    private static final Identifier ICONS = Identifier.fromNamespaceAndPath("neromaps", "textures/gui/map_icons.png");
    private static za.co.neroland.neromaps.engine.@Nullable IconAtlas atlas;

    private static za.co.neroland.neromaps.engine.IconAtlas atlas() {
        za.co.neroland.neromaps.engine.IconAtlas a = atlas;
        if (a == null) {
            a = za.co.neroland.neromaps.engine.IconAtlas.builtin();
            atlas = a;
        }
        return a;
    }

    private static void icon(GuiGraphicsExtractor g, int x, int y, int size, int[] uv) {
        //? if >=1.21.2 {
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, uv[0], uv[1], size, size, uv[2], uv[3], 256, 256);
        //?} else {
        /*g.blit(ICONS, x, y, size, size, uv[0], uv[1], uv[2], uv[3], 256, 256);
        *///?}
    }

    private @Nullable String hover;

    private void overlays(GuiGraphicsExtractor g, double left, double top, float ppb, String dim, int mouseX,
            int mouseY) {
        hover = null;
        ClientState st = ClientState.INSTANCE;
        MinimapSettings s = ClientMap.SETTINGS;
        // layer areas first, then markers on top
        for (za.co.neroland.neromaps.network.Payloads.LayerItem it : st.items(dim).values()) {
            za.co.neroland.neromaps.network.Payloads.LayerInfo info = st.catalogue().get(it.layer());
            if (info == null || !s.layerOnWorld(it.layer(), info.defaultVisible()) || it.shape() == 0) {
                continue;
            }
            area(g, it, left, top, ppb);
        }
        for (za.co.neroland.neromaps.network.Payloads.LayerItem it : st.items(dim).values()) {
            za.co.neroland.neromaps.network.Payloads.LayerInfo info = st.catalogue().get(it.layer());
            if (info == null || !s.layerOnWorld(it.layer(), info.defaultVisible()) || it.shape() != 0) {
                continue;
            }
            String label = it.label().isEmpty() ? Component.translatable(info.titleKey()).getString()
                    : it.literal() ? it.label() : Component.translatable(it.label(), it.args().toArray()).getString();
            mark(g, it.x(), it.z(), atlas().uv(it.icon().isEmpty() ? info.icon() : it.icon()), label, left, top, ppb,
                    mouseX, mouseY);
            if (!it.badge().isEmpty()) {
                // the standing badge sits on the marker's top-right corner (no information by colour alone: a glyph)
                int bx = (int) Math.round((it.x() + 0.5 - left) * ppb);
                int by = (int) Math.round((it.z() + 0.5 - top) * ppb);
                icon(g, bx + 2, by - 8, 7, atlas().uv(it.badge()));
            }
        }
        for (za.co.neroland.neromaps.store.Waypoint w : st.own()) {
            if (w.dimension().equals(dim)) {
                mark(g, w.x(), w.z(), atlas().uv(w.icon()), w.name(), left, top, ppb, mouseX, mouseY);
            }
        }
        for (za.co.neroland.neromaps.store.Waypoint w : st.temporary()) {
            if (w.dimension().equals(dim)) {
                mark(g, w.x(), w.z(), atlas().uv("neromaps:waypoint_temp"), w.name(), left, top, ppb, mouseX, mouseY);
            }
        }
        for (za.co.neroland.neromaps.network.Payloads.SharedWaypoint sw : st.shared()) {
            if (sw.waypoint().dimension().equals(dim)) {
                mark(g, sw.waypoint().x(), sw.waypoint().z(), atlas().uv(sw.waypoint().icon()),
                        sw.waypoint().name() + " (" + sw.sharerName() + ")", left, top, ppb, mouseX, mouseY);
            }
        }
        if (s.deathPointsShown) {
            for (za.co.neroland.neromaps.store.DeathPoint d : st.deaths()) {
                if (d.dimension().equals(dim)) {
                    mark(g, d.x(), d.z(), atlas().uv("neromaps:death"),
                            Component.translatable("neromaps.waypoint.death_label").getString(), left, top, ppb,
                            mouseX, mouseY);
                }
            }
        }
        for (za.co.neroland.neromaps.network.Payloads.Position pos : st.positions()) {
            if (pos.known() && pos.dimension().equals(dim)) {
                mark(g, pos.x(), pos.z(), atlas().uv("neromaps:shared_player"), pos.name(), left, top, ppb, mouseX,
                        mouseY);
            }
        }
        // dimension badges (no position): a row beside the dimension button, label on hover
        int bx = 4;
        for (za.co.neroland.neromaps.network.Payloads.DimensionBadge b : st.badges()) {
            if (!b.dimension().equals(dim) && !b.id().startsWith("nerospace:visited")) {
                continue;
            }
            icon(g, bx, 28, 12, atlas().uv(b.icon()));
            if (mouseX >= bx && mouseX < bx + 12 && mouseY >= 28 && mouseY < 40) {
                hover = b.label().isEmpty() ? b.dimension() : b.literal() ? b.label()
                        : Component.translatable(b.label()).getString();
            }
            bx += 14;
            if (bx > 300) {
                break;
            }
        }
        String h = hover;
        if (h != null) {
            int tw = font.width(h);
            g.fill(mouseX + 8, mouseY - 12, mouseX + 12 + tw, mouseY, (MapPalette.PANEL & 0xFFFFFF) | 0xE0000000);
            g.text(font, h, mouseX + 10, mouseY - 10, MapPalette.TEXT);
        }
    }

    private void mark(GuiGraphicsExtractor g, int wx, int wz, int[] uv, String label, double left, double top,
            float ppb, int mouseX, int mouseY) {
        int x = (int) Math.round((wx + 0.5 - left) * ppb);
        int y = (int) Math.round((wz + 0.5 - top) * ppb);
        if (x < -8 || y < -8 || x > width + 8 || y > height + 8) {
            return;
        }
        icon(g, x - 6, y - 6, 12, uv);
        if (Math.abs(mouseX - x) <= 6 && Math.abs(mouseY - y) <= 6) {
            hover = label;
        }
    }

    private void area(GuiGraphicsExtractor g, za.co.neroland.neromaps.network.Payloads.LayerItem it, double left,
            double top, float ppb) {
        int fill = it.fill() == 0 ? 0x30F2F5F8 : it.fill();
        int border = it.border() == 0 ? 0xC0F2F5F8 : it.border();
        int[] c = it.coords();
        switch (it.shape()) {
            case 1 -> {
                if (c.length < 4) {
                    return;
                }
                rect(g, c[0], c[1], c[2] + 1, c[3] + 1, left, top, ppb, fill, border);
            }
            case 3 -> {
                for (int i = 0; i + 1 < c.length; i += 2) {
                    rect(g, c[i] * 16, c[i + 1] * 16, c[i] * 16 + 16, c[i + 1] * 16 + 16, left, top, ppb, fill, 0);
                }
            }
            case 2 -> {
                if (c.length < 3) {
                    return;
                }
                int steps = 48;
                for (int i = 0; i < steps; i++) {
                    double a = Math.PI * 2 * i / steps;
                    int x = (int) Math.round((c[0] + Math.cos(a) * c[2] - left) * ppb);
                    int y = (int) Math.round((c[1] + Math.sin(a) * c[2] - top) * ppb);
                    g.fill(x, y, x + 2, y + 2, border);
                }
            }
            case 4 -> {
                int n = c.length / 2;
                for (int i = 0; i < n; i++) {
                    int ax = (int) Math.round((c[i * 2] - left) * ppb);
                    int az = (int) Math.round((c[i * 2 + 1] - top) * ppb);
                    int bx = (int) Math.round((c[((i + 1) % n) * 2] - left) * ppb);
                    int bz = (int) Math.round((c[((i + 1) % n) * 2 + 1] - top) * ppb);
                    int steps = Math.max(1, Math.max(Math.abs(bx - ax), Math.abs(bz - az)) / 2);
                    for (int k = 0; k <= steps; k++) {
                        int x = ax + (bx - ax) * k / steps;
                        int y = az + (bz - az) * k / steps;
                        g.fill(x, y, x + 1, y + 1, border);
                    }
                }
            }
            default -> { }
        }
    }

    private void rect(GuiGraphicsExtractor g, int x0, int z0, int x1, int z1, double left, double top, float ppb,
            int fill, int border) {
        int ax = (int) Math.round((x0 - left) * ppb);
        int az = (int) Math.round((z0 - top) * ppb);
        int bx = (int) Math.round((x1 - left) * ppb);
        int bz = (int) Math.round((z1 - top) * ppb);
        if (bx < 0 || bz < 0 || ax > width || az > height) {
            return;
        }
        g.fill(ax, az, bx, bz, fill);
        if (border != 0) {
            g.fill(ax, az, bx, az + 1, border);
            g.fill(ax, bz - 1, bx, bz, border);
            g.fill(ax, az, ax + 1, bz, border);
            g.fill(bx - 1, az, bx, bz, border);
        }
    }

    // --- server traffic: layer view and shared tiles ----------------------------------------------------------

    private long lastViewSent;
    private int sentMinX = Integer.MIN_VALUE;
    private int sentMinZ;
    private int sentMaxX;
    private int sentMaxZ;
    private long lastTileRequest;

    private void serverTraffic(double left, double top, float ppb, int lod, String dim) {
        ClientState st = ClientState.INSTANCE;
        if (!st.serverHasMod() || dimension != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int minX = (int) Math.floor(left);
        int minZ = (int) Math.floor(top);
        int maxX = (int) Math.ceil(left + width / ppb);
        int maxZ = (int) Math.ceil(top + height / ppb);
        if (now - lastViewSent > 1000 && (Math.abs(minX - sentMinX) > 64 || Math.abs(minZ - sentMinZ) > 64
                || Math.abs(maxX - sentMaxX) > 64 || Math.abs(maxZ - sentMaxZ) > 64)) {
            lastViewSent = now;
            sentMinX = minX;
            sentMinZ = minZ;
            sentMaxX = maxX;
            sentMaxZ = maxZ;
            za.co.neroland.neromaps.platform.Services.NETWORK.sendToServer(
                    new za.co.neroland.neromaps.network.Payloads.LayerView(dim, minX, minZ, maxX, maxZ, true));
        }
        if (lod > za.co.neroland.neromaps.server.tiles.TileService.MAX_LOD || !st.hasSharedMap(dim)
                || now - lastTileRequest < 1000) {
            return;
        }
        lastTileRequest = now;
        MapStore store = viewed();
        int span = Tile.SIZE << lod;
        int tx0 = (int) Math.floor(left / span);
        int tz0 = (int) Math.floor(top / span);
        int tx1 = (int) Math.floor((left + width / ppb) / span);
        int tz1 = (int) Math.floor((top + height / ppb) / span);
        long[] keys = new long[za.co.neroland.neromaps.network.Payloads.MAX_TILE_REQUEST];
        long[] etags = new long[keys.length];
        int n = 0;
        for (int tz = tz0; tz <= tz1 && n < keys.length; tz++) {
            for (int tx = tx0; tx <= tx1 && n < keys.length; tx++) {
                if (store != null && store.request(lod, tx, tz) != null) {
                    continue;
                }
                keys[n] = TileKey.tile(lod, tx, tz);
                etags[n++] = st.sharedEtag(dim, lod, tx, tz);
            }
        }
        if (n > 0) {
            za.co.neroland.neromaps.platform.Services.NETWORK.sendToServer(
                    new za.co.neroland.neromaps.network.Payloads.TileRequest(dim, lod,
                            java.util.Arrays.copyOf(keys, n), java.util.Arrays.copyOf(etags, n)));
        }
    }

    private Component dimLabel() {
        String id = dimensions.isEmpty() ? ClientMap.dimensionId() : dimensions.get(dimension);
        int c = id.indexOf(':');
        String path = c < 0 ? id : id.substring(c + 1);
        return Component.translatable("gui.neromaps.worldmap.dimension",
                Component.translatableWithFallback("gui.neromaps.dimension." + id.replace(':', '.'), path));
    }

    private Component followLabel() {
        return Component.translatable(follow ? "gui.neromaps.worldmap.follow.on" : "gui.neromaps.worldmap.follow.off");
    }

    private Component measureLabel() {
        return Component.translatable(measuring ? "gui.neromaps.worldmap.measure.on" : "gui.neromaps.worldmap.measure.off");
    }

    /** The current dimension first, then every other dimension cached for this server and world. */
    private void listDimensions() {
        dimensions.clear();
        String current = ClientMap.dimensionId();
        dimensions.add(current);
        Path world = ClientMap.worldDir();
        if (world == null || !Files.isDirectory(world)) {
            return;
        }
        try (Stream<Path> ns = Files.list(world)) {
            for (Path n : (Iterable<Path>) ns::iterator) {
                if (!Files.isDirectory(n)) {
                    continue;
                }
                try (Stream<Path> ps = Files.list(n)) {
                    for (Path p : (Iterable<Path>) ps::iterator) {
                        String id = n.getFileName() + ":" + p.getFileName();
                        if (Files.isDirectory(p) && !id.equals(za.co.neroland.neromaps.cache.TileDiskCache.safe(
                                current.substring(0, Math.max(0, current.indexOf(':')))) + ":"
                                + za.co.neroland.neromaps.cache.TileDiskCache.safe(current.substring(current.indexOf(':') + 1)))) {
                            dimensions.add(id);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // listing is best effort
        }
    }

    private void cycleDimension() {
        if (dimensions.size() < 2) {
            return;
        }
        dimension = (dimension + 1) % dimensions.size();
        MapStore f = foreign;
        if (f != null) {
            f.close();
            foreign = null;
        }
        if (dimension > 0) {
            String id = dimensions.get(dimension);
            Path world = ClientMap.worldDir();
            var cache = ClientMap.cache();
            var io = ClientMap.io();
            if (world != null && cache != null && io != null) {
                int c = id.indexOf(':');
                foreign = new MapStore(cache, world.resolve(id.substring(0, c)).resolve(id.substring(c + 1)), io, 64);
            }
            follow = false;
            if (followButton != null) {
                followButton.setMessage(followLabel());
            }
        }
        WorldMapTextures t = textures;
        if (t != null) {
            t.close();
        }
        textures = new WorldMapTextures(64);
        if (dimButton != null) {
            dimButton.setMessage(dimLabel());
        }
    }

    private @Nullable MapStore viewed() {
        return dimension == 0 ? ClientMap.store() : foreign;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (ClientState.INSTANCE.serverHasMod() && sentMinX != Integer.MIN_VALUE) {
            za.co.neroland.neromaps.platform.Services.NETWORK.sendToServer(
                    new za.co.neroland.neromaps.network.Payloads.LayerView(ClientMap.dimensionId(), 0, 0, 0, 0, false));
        }
        WorldMapTextures t = textures;
        if (t != null) {
            t.close();
        }
        textures = null;
        MapStore f = foreign;
        if (f != null) {
            f.close();
        }
        foreign = null;
    }

    // --- drawing ------------------------------------------------------------------------------------------

    private float ppb() {
        return ZOOMS[zoom];
    }

    private int lod() {
        float p = ppb();
        int l = 0;
        while (l < TileLod.MAX_LOD && p * (1 << l) < 1f) {
            l++;
        }
        return l;
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, MapPalette.VOID);
        LocalPlayer player = Minecraft.getInstance().player;
        if (follow && player != null && dimension == 0) {
            camX = player.getX();
            camZ = player.getZ();
        }
        MapStore store = viewed();
        WorldMapTextures tex = textures;
        if (store == null || tex == null) {
            return;
        }
        tex.beginFrame();
        MinimapSettings s = ClientMap.SETTINGS;
        boolean overlays = ClientMap.overlaysAllowed();
        raster.set(s.effectiveStyle(), s.chunkBorders && overlays, s.grid && overlays, 63);
        int rasterVersion = raster.version * 31 + ColourTables.current().version();
        float ppb = ppb();
        int lod = lod();
        int span = Tile.SIZE << lod; // blocks per tile
        int tilePx = Math.round(span * ppb);
        double left = camX - width / 2.0 / ppb;
        double top = camZ - height / 2.0 / ppb;
        int tx0 = (int) Math.floor(left / span);
        int tz0 = (int) Math.floor(top / span);
        int tx1 = (int) Math.floor((left + width / ppb) / span);
        int tz1 = (int) Math.floor((top + height / ppb) / span);
        int ox = (int) Math.floor((tx0 * (double) span - left) * ppb);
        int oy = (int) Math.floor((tz0 * (double) span - top) * ppb);
        for (int tz = tz0; tz <= tz1; tz++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int x = ox + (tx - tx0) * tilePx;
                int y = oy + (tz - tz0) * tilePx;
                Tile t = store.request(lod, tx, tz);
                if (t == null && dimension == 0) {
                    t = ClientState.INSTANCE.sharedTile(ClientMap.dimensionId(), lod, tx, tz);
                }
                if (t != null) {
                    MapTexture mt = tex.texture(TileKey.tile(lod, tx, tz), t, store, ColourTables.current(), raster,
                            rasterVersion);
                    if (mt != null) {
                        blit(g, mt.id, x, y, tilePx, tilePx, Tile.SIZE);
                    }
                }
                // the survey grid over everything (6 % accent)
                g.fill(x, y, x + tilePx, y + 1, 0x0F7C8CFF);
                g.fill(x, y, x + 1, y + tilePx, 0x0F7C8CFF);
            }
        }
        String dimId = dimensions.isEmpty() ? ClientMap.dimensionId() : dimensions.get(dimension);
        overlays(g, left, top, ppb, dimId, mouseX, mouseY);
        serverTraffic(left, top, ppb, lod, dimId);
        // the player
        if (player != null && dimension == 0) {
            int px = (int) Math.round((player.getX() - left) * ppb);
            int pz = (int) Math.round((player.getZ() - top) * ppb);
            blit(g, PRISM, px - 5, pz - 6, 11, 11, 11);
            double a = Math.toRadians(player.getYRot());
            int nx = px + (int) Math.round(-Math.sin(a) * 9);
            int nz = pz + (int) Math.round(Math.cos(a) * 9);
            g.fill(nx - 1, nz - 1, nx + 2, nz + 2, MapPalette.MAP_ACCENT_BRIGHT);
        }
        // measure line
        if (!Double.isNaN(measureAx)) {
            double bx = Double.isNaN(measureBx) ? worldX(mouseX) : measureBx;
            double bz = Double.isNaN(measureBx) ? worldZ(mouseY) : measureBz;
            int ax = (int) Math.round((measureAx - left) * ppb);
            int az = (int) Math.round((measureAz - top) * ppb);
            int ex = (int) Math.round((bx - left) * ppb);
            int ez = (int) Math.round((bz - top) * ppb);
            int steps = Math.max(1, Math.max(Math.abs(ex - ax), Math.abs(ez - az)) / 2);
            for (int i = 0; i <= steps; i++) {
                int x = ax + (ex - ax) * i / steps;
                int y = az + (ez - az) * i / steps;
                g.fill(x, y, x + 1, y + 1, MapPalette.M_YELLOW);
            }
            double d = Math.sqrt((bx - measureAx) * (bx - measureAx) + (bz - measureAz) * (bz - measureAz));
            g.text(font, Component.translatable("gui.neromaps.worldmap.measure_result", (long) Math.floor(d)).getString(),
                    ex + 6, ez - 4, MapPalette.TEXT);
        }
    }

    private void drawOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int v = ClientState.INSTANCE.version();
        if (v != lastStateVersion) {
            lastStateVersion = v;
            if (panel != Panel.NONE) {
                rebuildWidgets();
            }
        }
        drawPanel(g);
        int bx = (int) Math.floor(worldX(mouseX));
        int bz = (int) Math.floor(worldZ(mouseY));
        String line = Component.translatable("gui.neromaps.worldmap.cursor", bx, bz).getString();
        g.fill(2, height - 14, 8 + font.width(line), height - 2, (MapPalette.PANEL & 0xFFFFFF) | 0xC0000000);
        g.text(font, line, 5, height - 12, MapPalette.TEXT);
        PerfCounters.textureBytes = Math.max(0, PerfCounters.textureBytes);
    }

    private double worldX(double mouseX) {
        return camX + (mouseX - width / 2.0) / ppb();
    }

    private double worldZ(double mouseY) {
        return camZ + (mouseY - height / 2.0) / ppb();
    }

    private static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int tex) {
        //? if >=1.21.2 {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, tex, tex, tex, tex);
        //?} else {
        /*g.blit(id, x, y, w, h, 0f, 0f, tex, tex, tex, tex);
        *///?}
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMap(g, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawOverlay(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMap(g, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawOverlay(g, mouseX, mouseY);
    }
    *///?}

    // --- input --------------------------------------------------------------------------------------------

    private boolean click(double mx, double my, int button) {
        if (button == 1 && (panel == Panel.NONE || mx < panelX() - 4)) {
            int wx = (int) Math.floor(worldX(mx));
            int wz = (int) Math.floor(worldZ(my));
            LocalPlayer p = Minecraft.getInstance().player;
            int wy = p == null ? 64 : p.getBlockY();
            MapStore st = viewed();
            Tile t = st == null ? null : st.get(0, wx >> 8, wz >> 8);
            if (t != null && t.present(Tile.index(wx & 255, wz & 255))) {
                wy = t.height[Tile.index(wx & 255, wz & 255)] + 1;
            }
            newWaypointAt(wx, wy, wz);
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (!measuring && (panel == Panel.NONE || mx < panelX() - 4)) {
            String dim = dimensions.isEmpty() ? ClientMap.dimensionId() : dimensions.get(dimension);
            za.co.neroland.neromaps.store.Waypoint hit = null;
            boolean temp = false;
            double best = 7 * 7;
            for (za.co.neroland.neromaps.store.Waypoint w : ClientState.INSTANCE.own()) {
                double d = markerDistSq(w, dim, mx, my);
                if (d < best) {
                    best = d;
                    hit = w;
                    temp = false;
                }
            }
            for (za.co.neroland.neromaps.store.Waypoint w : ClientState.INSTANCE.temporary()) {
                double d = markerDistSq(w, dim, mx, my);
                if (d < best) {
                    best = d;
                    hit = w;
                    temp = true;
                }
            }
            if (hit != null) {
                edit(hit, temp);
                return true;
            }
        }
        if (measuring) {
            if (Double.isNaN(measureAx) || !Double.isNaN(measureBx)) {
                measureAx = worldX(mx);
                measureAz = worldZ(my);
                measureBx = Double.NaN;
            } else {
                measureBx = worldX(mx);
                measureBz = worldZ(my);
            }
            return true;
        }
        dragging = true;
        return true;
    }

    /** Squared screen distance from the cursor to a waypoint's marker, or infinity in another dimension. */
    private double markerDistSq(za.co.neroland.neromaps.store.Waypoint w, String dim, double mx, double my) {
        if (!w.dimension().equals(dim)) {
            return Double.POSITIVE_INFINITY;
        }
        double sx = (w.x() + 0.5 - camX) * ppb() + width / 2.0;
        double sy = (w.z() + 0.5 - camZ) * ppb() + height / 2.0;
        return (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
    }

    @Override
    public void tick() {
        super.tick();
        if (searchChanged) {
            searchChanged = false;
            rebuildWidgets();
            net.minecraft.client.gui.components.EditBox box = searchBox;
            if (box != null) {
                setFocused(box);
                box.setFocused(true);
            }
        }
    }

    private void drag(double dx, double dy) {
        if (!dragging) {
            return;
        }
        follow = false;
        if (followButton != null) {
            followButton.setMessage(followLabel());
        }
        camX -= dx / ppb();
        camZ -= dy / ppb();
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        return click(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        drag(dx, dy);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(event); // typing in the search field never closes the map
        }
        if (MapKeys.WORLD_MAP.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        return click(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        drag(dx, dy);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(key, scan, modifiers); // typing in the search field never closes the map
        }
        if (MapKeys.WORLD_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        double wx = worldX(mx);
        double wz = worldZ(my);
        int next = Math.max(0, Math.min(ZOOMS.length - 1, zoom + (scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0)));
        if (next != zoom) {
            zoom = next;
            // keep the block under the cursor in place
            camX = wx - (mx - width / 2.0) / ppb();
            camZ = wz - (my - height / 2.0) / ppb();
            follow = false;
            if (followButton != null) {
                followButton.setMessage(followLabel());
            }
        }
        return true;
    }
}
