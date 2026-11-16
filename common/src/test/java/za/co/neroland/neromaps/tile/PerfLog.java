package za.co.neroland.neromaps.tile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends benchmark lines to {@code build/neromaps-perf/bench.txt} of the running cell (copied into the performance notes). */
public final class PerfLog {

    private PerfLog() {
    }

    public static synchronized void line(String s) {
        String full = "[java " + Runtime.version().feature() + "] " + s;
        System.out.println(full);
        try {
            Path dir = Path.of("build", "neromaps-perf");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("bench.txt"), full + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // console output is enough
        }
    }
}
