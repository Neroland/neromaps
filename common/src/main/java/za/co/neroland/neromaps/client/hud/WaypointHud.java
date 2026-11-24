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
 *
 * <p>The projection uses the same camera the world was drawn with this frame (its interpolated position, rotation
 * and field of view), not the player's last-tick values, and beams and labels are placed at sub-pixel positions: a
 * marker therefore glides with the world instead of stepping once a tick or once a GUI pixel.
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
        //? if >=26.2 {
        net.minecraft.client.Camera cam = mc.gameRenderer.mainCamera();
        //?} else if >=26.1 {
        /*net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        *///?} else if >=1.21.2 {
        /*float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        *///?} else if >=1.21 {
        /*float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        *///?} else {
        /*float partial = mc.getFrameTime();
        *///?}
        //? if >=26.1 {
        if (!cam.isInitialized()) {
            return;
        }
        // the camera the world was rendered with this frame: interpolated, and with the live field of view
        Vec3 eye = cam.position();
        float yaw = cam.yRot();
        float pitch = cam.xRot();
        double fov = cam.getFov();
        //?} else {
        /*Vec3 eye = player.getEyePosition(partial);
        float yaw = player.getViewYRot(partial);
        float pitch = player.getViewXRot(partial);
        double fov = mc.options.fov().get();
        *///?}
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        P.camera(eye.x, eye.y, eye.z, yaw, pitch, fov, w, h);
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
            double px = P.x;
            int bx = (int) Math.floor(px);
            int by = (int) Math.round(P.y);
            double top = Math.max(y + 64, eye.y + 48);
            int ty = P.project(x, top, z) ? (int) Math.round(P.y) : 0;
            if (bx > -4 && bx < w + 4) {
                int y0 = Math.max(0, Math.min(by, ty));
                int y1 = Math.min(h, Math.max(by, ty));
                int beamAlpha = Math.max(32, alpha / 2);
                push(g, px - bx, 0);
                g.fill(bx - 1, y0, bx + 1, y1, (beamAlpha << 24) | (rgb & 0xFFFFFF));
                pop(g);
            }
        }
        if (s.distanceLabels && label && P.project(x, y + 2.2, z)) {
            String text = name + "  " + (long) Math.floor(dist) + " m";
            int tw = font.width(text);
            double px = P.x - tw / 2.0;
            double py = P.y - 4;
            int lx = (int) Math.floor(px);
            int ly = (int) Math.floor(py);
            if (lx > -tw && lx < w && ly > -10 && ly < h) {
                push(g, px - lx, py - ly);
                g.fill(lx - 2, ly - 1, lx + tw + 2, ly + 9, 0x80000000);
                g.text(font, text, lx, ly, colour | 0xFF000000);
                pop(g);
            }
        }
    }

    /** Shifts what is drawn next by a fraction of a GUI pixel, so a marker moves smoothly at any GUI scale. */
    private static void push(GuiGraphicsExtractor g, double dx, double dy) {
        //? if >=1.21.6 {
        g.pose().pushMatrix();
        g.pose().translate((float) dx, (float) dy);
        //?} else {
        /*g.pose().pushPose();
        g.pose().translate((float) dx, (float) dy, 0f);
        *///?}
    }

    private static void pop(GuiGraphicsExtractor g) {
        //? if >=1.21.6 {
        g.pose().popMatrix();
        //?} else {
        /*g.pose().popPose();
        *///?}
    }
}
