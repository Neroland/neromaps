package za.co.neroland.neromaps.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.store.DeathPoint;
import za.co.neroland.neromaps.store.ShareScope;
import za.co.neroland.neromaps.store.Waypoint;

/** JSON shapes and id formats of the {@code neromaps} link contract, schema 1. Pure. */
public final class MapsLinkJson {

    /** The seven marker colours (013), in the contract's index order. */
    public static final int[] COLOURS = {0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0xD55E00, 0xCC79A7, 0xF2F5F8};
    /** Waypoint glyphs the app knows (icon {@code neromaps:glyph/<glyph>}). */
    public static final java.util.List<String> GLYPHS = java.util.List.of("dot", "home", "bed", "pickaxe", "sword",
            "chest", "portal", "anchor", "wheat", "tower", "cave", "star", "heart", "flag", "question", "exclamation");
    public static final String GLYPH_PREFIX = "neromaps:glyph/";

    private MapsLinkJson() {
    }

    public static JsonObject waypoint(Waypoint w) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "w" + w.id());
        o.addProperty("name", w.name());
        o.addProperty("dim", w.dimension());
        o.addProperty("x", w.x());
        o.addProperty("y", w.y());
        o.addProperty("z", w.z());
        o.addProperty("colour", colourIndex(w.colour()));
        o.addProperty("glyph", glyph(w.icon()));
        o.addProperty("minimap", true);
        o.addProperty("beam", w.beam());
        o.addProperty("kind", "waypoint");
        return o;
    }

    public static JsonObject death(int index, DeathPoint d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "d" + index);
        o.addProperty("name", "Death " + (index + 1));
        o.addProperty("dim", d.dimension());
        o.addProperty("x", d.x());
        o.addProperty("y", d.y());
        o.addProperty("z", d.z());
        o.addProperty("colour", 4);
        o.addProperty("glyph", "exclamation");
        o.addProperty("minimap", true);
        o.addProperty("beam", false);
        o.addProperty("kind", "death");
        return o;
    }

    /** Nearest of the seven marker colours (exact for colours set through the screen or the app). */
    public static int colourIndex(int rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < COLOURS.length; i++) {
            int c = COLOURS[i];
            long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            long db = (c & 0xFF) - (rgb & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static String glyph(String icon) {
        if (icon != null && icon.startsWith(GLYPH_PREFIX)) {
            String g = icon.substring(GLYPH_PREFIX.length());
            if (GLYPHS.contains(g)) {
                return g;
            }
        }
        return "dot";
    }

    /** {@code w<n>} → n, or -1. */
    public static int waypointId(@Nullable String id) {
        if (id == null || id.length() < 2 || id.length() > 11 || id.charAt(0) != 'w') {
            return -1;
        }
        try {
            int v = Integer.parseInt(id.substring(1));
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** {@code d<n>} → n (a death point index), or -1. */
    public static int deathIndex(@Nullable String id) {
        if (id == null || id.length() != 2 || id.charAt(0) != 'd') {
            return -1;
        }
        int v = id.charAt(1) - '0';
        return v >= 0 && v < DeathPoint.KEEP ? v : -1;
    }

    public static String requestId(long id) {
        return "r" + Long.toString(id, 36);
    }

    /** {@code r<base36>} → the request id, or 0. */
    public static long requestId(@Nullable String s) {
        if (s == null || s.length() < 2 || s.length() > 16 || s.charAt(0) != 'r') {
            return 0;
        }
        try {
            long v = Long.parseLong(s.substring(1), 36);
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * A grant's id: a hash of (sharer, recipient, scope). Opaque and stable; it can only be resolved by one of the two
     * parties, against grants they are part of, so knowing it gives nobody else anything.
     */
    public static String grantId(UUID sharer, UUID recipient, ShareScope scope) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((sharer + "|" + recipient + "|" + scope.kind().name() + "|" + scope.arg())
                    .getBytes(StandardCharsets.UTF_8));
            byte[] h = md.digest();
            StringBuilder b = new StringBuilder("g");
            for (int i = 0; i < 8; i++) {
                b.append(Character.forDigit((h[i] >> 4) & 0xF, 16)).append(Character.forDigit(h[i] & 0xF, 16));
            }
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonObject grant(String id, ShareScope scope, String player, @Nullable String waypointName) {
        JsonObject e = new JsonObject();
        e.addProperty("id", id);
        e.addProperty("scope", scope.kind().name());
        if (scope.kind() == ShareScope.Kind.EXPLORED_MAP) {
            dim(e, scope);
        }
        if (scope.kind() == ShareScope.Kind.WAYPOINT && waypointName != null) {
            e.addProperty("label", waypointName);
        }
        e.addProperty("player", player);
        return e;
    }

    static void dim(JsonObject e, ShareScope scope) {
        if (ShareScope.ALL.equals(scope.arg())) {
            e.add("dim", com.google.gson.JsonNull.INSTANCE);
        } else {
            e.addProperty("dim", scope.arg());
        }
    }
}
