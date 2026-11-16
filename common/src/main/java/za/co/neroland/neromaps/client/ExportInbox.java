package za.co.neroland.neromaps.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.store.PlayerExport;

/**
 * Where a server's {@code /neromaps export} answer waits until the player saves it. A server can send an export at any
 * time, so nothing is written on arrival: the newest one is held in memory (at most {@link Payloads.Export#MAX_BYTES}
 * compressed, {@link PlayerExport#MAX_JSON} inflated) and written only when the player clicks Save
 * ({@code /neromaps-client export save}) in this session. At most {@link #KEEP} export files are kept; older ones are
 * deleted. No Minecraft types, so the rules are tested directly.
 */
public final class ExportInbox {

    /** Export files kept in {@code neromaps/exports/}; the oldest go first. */
    public static final int KEEP = 10;
    public static final String PREFIX = "neromaps-export-";

    private byte @Nullable [] held;

    /** Holds an export the server sent; false (and nothing held) when it is over the size cap. */
    public synchronized boolean hold(byte[] deflated) {
        if (deflated == null || deflated.length == 0 || deflated.length > Payloads.Export.MAX_BYTES) {
            return false;
        }
        held = deflated;
        return true;
    }

    public synchronized boolean holding() {
        return held != null;
    }

    /** Forgets the held export (disconnect). */
    public synchronized void clear() {
        held = null;
    }

    /**
     * Writes the held export to {@code dir/<fileName>} and prunes old files. Returns the path written, or null when
     * nothing was held. The held export is consumed either way, so one click writes one file.
     */
    public synchronized @Nullable Path save(Path dir, String fileName) throws IOException {
        byte[] d = held;
        held = null;
        if (d == null) {
            return null;
        }
        byte[] json = PlayerExport.inflate(d); // throws past MAX_JSON
        Files.createDirectories(dir);
        Path out = dir.resolve(fileName);
        Files.write(out, json);
        prune(dir, KEEP);
        return out;
    }

    /** Deletes all but the newest {@code keep} export files in {@code dir} (names sort by time). */
    public static void prune(Path dir, int keep) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(PREFIX) && n.endsWith(".json");
            }).sorted().toList();
        }
        for (int i = 0; i < files.size() - keep; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }
}
