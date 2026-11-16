package za.co.neroland.neromaps.store;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code /neromaps export}: everything NeroMaps holds about one player and nothing about anyone else.
 * That is the player's own record, plus the shares other players gave <em>them</em> (the grant lives on the sharer's
 * record, but it is a consent this player accepted, so it is theirs to see: scope and the sharer's id, never the
 * sharer's waypoints, map or anything else), and the share offers this player made that still wait on a recipient's
 * record (recipient id, scope, day). Live position is never stored, so it is never in an export.
 */
public final class PlayerExport {

    /** Largest export a client will inflate (bitsets of a capped record are far below this). */
    public static final int MAX_JSON = 16 << 20;

    private PlayerExport() {
    }

    public static JsonObject build(MapPlayerState state, UUID player) {
        JsonObject out = state.export(player);
        JsonArray received = new JsonArray();
        state.forEach((sharer, r) -> {
            if (sharer.equals(player)) {
                return;
            }
            for (Grant g : r.grants()) {
                if (g.to().equals(player)) {
                    JsonObject e = new JsonObject();
                    e.addProperty("from", sharer.toString());
                    e.addProperty("scope", g.scope().kind().name());
                    e.addProperty("arg", g.scope().arg());
                    received.add(e);
                }
            }
        });
        out.add("sharedWithYou", received);
        // offers this player made that wait on someone else's record (the request lives with the recipient)
        JsonArray offers = new JsonArray();
        state.forEach((recipient, r) -> {
            for (ShareRequest q : r.incoming()) {
                if (q.from().equals(player)) {
                    JsonObject e = new JsonObject();
                    e.addProperty("to", recipient.toString());
                    e.addProperty("scope", q.scope().kind().name());
                    e.addProperty("arg", q.scope().arg());
                    e.addProperty("day", q.day());
                    offers.add(e);
                }
            }
        });
        out.add("offersYouMade", offers);
        return out;
    }

    @SuppressWarnings("resource") // Deflater is AutoCloseable only from Java 25; end() runs in finally
    public static byte[] deflate(String json) {
        byte[] in = json.getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(in);
            d.finish();
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, in.length / 4));
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                bo.write(buf, 0, d.deflate(buf));
            }
            return bo.toByteArray();
        } finally {
            d.end();
        }
    }

    @SuppressWarnings("resource") // Inflater is AutoCloseable only from Java 25; end() runs in finally
    public static byte[] inflate(byte[] data) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(data.length * 4);
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                bo.write(buf, 0, n);
                if (bo.size() > MAX_JSON) {
                    throw new IllegalArgumentException("export too large");
                }
            }
            return bo.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("malformed export", e);
        } finally {
            inf.end();
        }
    }
}
