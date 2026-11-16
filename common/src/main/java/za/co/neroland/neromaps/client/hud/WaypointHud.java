package za.co.neroland.neromaps.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neromaps.client.ClientMap;
import za.co.neroland.neromaps.client.ClientState;
import za.co.neroland.neromaps.engine.Projection;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.Waypoint;

/**
 * Waypoint beams and distance labels, drawn on the HUD by projecting each waypoint from the player's eye (the same
 * code on every Minecraft line, no world-renderer hook). A beam is a thin vertical column from the waypoint up to the
 * top of the view, faded with distance; the label shows the name and the distance in metres. Only waypoints in this
 * dimension within {@code beamRange} are drawn. Client only.
 */
public final class WaypointHud {

    private static final Projection P = new Projection();

    private WaypointHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MinimapSettings s = ClientMap.SETTINGS;
        if (player == null || mc.level == null || (!s.beams && !s.distanceLabels) || ClientMap.hudHidden(mc)
                || ClientMap.screen(mc) != null) {
            return;
        }
        Vec3 eye = player.getEyePosition(1.0f);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        P.camera(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot(), mc.options.fov().get(), w, h);
        String dim = ClientMap.dimensionId();
        ClientState st = ClientState.INSTANCE;
        Font font = mc.font;
        double range = s.beamRange;
        for (Waypoint wp : st.own()) {
            draw(g, font, wp, dim, eye, range, s, w, h);
        }
        for (Waypoint wp : st.temporary()) {
            draw(g, font, wp, dim, eye, range, s, w, h);
        }
        for (Payloads.SharedWaypoint sw : st.shared()) {
            draw(g, font, sw.waypoint(), dim, eye, range, s, w, h);
        }
        if (s.deathPointsShown) {
            for (DeathPoint d : st.deaths()) {
                if (d.dimension().equals(dim)) {
                    one(g, font, d.x() + 0.5, d.y(), d.z() + 0.5, 0xD55E00, "\u2020", eye, range, s, true, true, w, h);
                }
            }
        }
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Waypoint wp, String dim, Vec3 eye, double range,
            MinimapSettings s, int w, int h) {
        if (!wp.dimension().equals(dim)) {
            return;
        }
        one(g, font, wp.x() + 0.5, wp.y(), wp.z() + 0.5, wp.colour(), wp.name(), eye, range, s, wp.beam(),
                wp.distanceLabel(), w, h);
    }

    private static void one(GuiGraphicsExtractor g, Font font, double x, double y, double z, int rgb, String name,
            Vec3 eye, double range, MinimapSettings s, boolean beam, boolean label, int w, int h) {
        double dx = x - eye.x;
        double dz = z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz + (y - eye.y) * (y - eye.y));
        if (dist > range || dist < 2.0) {
            return;
        }
        int alpha = (int) Math.round(255 * Math.max(0.25, 1.0 - dist / range));
        int colour = (alpha << 24) | (rgb & 0xFFFFFF);
        if (s.beams && beam && P.project(x, y, z)) {
            int bx = (int) Math.round(P.x);
            int by = (int) Math.round(P.y);
            double top = Math.max(y + 64, eye.y + 48);
            int ty = P.project(x, top, z) ? (int) Math.round(P.y) : 0;
            if (bx > -4 && bx < w + 4) {
                int y0 = Math.max(0, Math.min(by, ty));
                int y1 = Math.min(h, Math.max(by, ty));
                int beamAlpha = Math.max(32, alpha / 2);
                g.fill(bx - 1, y0, bx + 1, y1, (beamAlpha << 24) | (rgb & 0xFFFFFF));
            }
        }
        if (s.distanceLabels && label && P.project(x, y + 2.2, z)) {
            String text = name + "  " + (long) Math.floor(dist) + " m";
            int tw = font.width(text);
            int lx = (int) Math.round(P.x) - tw / 2;
            int ly = (int) Math.round(P.y) - 4;
            if (lx > -tw && lx < w && ly > -10 && ly < h) {
                g.fill(lx - 2, ly - 1, lx + tw + 2, ly + 9, 0x80000000);
                g.text(font, text, lx, ly, colour | 0xFF000000);
            }
        }
    }
}
