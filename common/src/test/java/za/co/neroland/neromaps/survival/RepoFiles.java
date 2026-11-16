package za.co.neroland.neromaps.survival;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Locates the repository's shared sources and resources from a test run in any loader node. */
final class RepoFiles {

    private RepoFiles() {
    }

    static Path root() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("common/src/main/resources"))) {
                return p;
            }
        }
        throw new IllegalStateException("repository root not found");
    }

    static Path resources() {
        return root().resolve("common/src/main/resources");
    }

    static Path java() {
        return root().resolve("common/src/main/java/za/co/neroland/neromaps");
    }

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonObject json(Path p) {
        return JsonParser.parseString(read(p)).getAsJsonObject();
    }

    static JsonObject lang() {
        return json(resources().resolve("assets/neromaps/lang/en_us.json"));
    }
}
