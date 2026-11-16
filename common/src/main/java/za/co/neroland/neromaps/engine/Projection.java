package za.co.neroland.neromaps.engine;

/**
 * Projects a world point onto the screen from the player's eye, yaw, pitch and vertical field of view, so waypoint
 * beams and distance labels can be drawn on the HUD the same way on every Minecraft line (no renderer internals).
 * Minecraft angles: yaw 0 faces south (+z) and grows clockwise seen from above; pitch is positive looking down.
 * Pure, allocation-free: results land in {@link #x}, {@link #y} and {@link #depth}.
 */
public final class Projection {

    public double x;
    public double y;
    public double depth;

    private double fx, fy, fz, rx, rz, ux, uy, uz, ex, ey, ez, scale, cx, cy;

    /** Sets the camera. {@code width}, {@code height} in GUI pixels; {@code fovDeg} vertical. */
    public void camera(double eyeX, double eyeY, double eyeZ, float yawDeg, float pitchDeg, double fovDeg,
            double width, double height) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        fx = -Math.sin(yaw) * cp;
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * cp;
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        // up = right x forward (right has no y component)
        ux = 0 * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - 0 * fx;
        ex = eyeX;
        ey = eyeY;
        ez = eyeZ;
        cx = width / 2.0;
        cy = height / 2.0;
        scale = cy / Math.tan(Math.toRadians(Math.max(1.0, Math.min(170.0, fovDeg))) / 2.0);
    }

    /** Projects a point; false when it is behind the camera (or too close to the eye plane). */
    public boolean project(double px, double py, double pz) {
        double dx = px - ex;
        double dy = py - ey;
        double dz = pz - ez;
        depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) {
            return false;
        }
        double sx = dx * rx + dz * rz;
        double sy = dx * ux + dy * uy + dz * uz;
        x = cx + sx / depth * scale;
        y = cy - sy / depth * scale;
        return true;
    }
}
