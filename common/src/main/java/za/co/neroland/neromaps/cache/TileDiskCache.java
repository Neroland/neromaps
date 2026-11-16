package za.co.neroland.neromaps.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.perf.PerfCounters;

/**
 * The client's terrain cache on disk:
 * {@code <root>/<serverKey>/<worldId>/<dimension ns>/<dimension path>/<lod>/<tx>.<tz>.nmt}, in the tile codec
 * format. It holds only terrain this client captured itself: no positions, no names, nothing a player typed.
 * Size-capped with least-recently-used eviction (file modification time is the access stamp, so the order
 * survives restarts). Writes are atomic (temp file, then move). Every method except {@link #exists} and the
 * key helpers runs on the I/O worker.
 */
public final class TileDiskCache {

    public static final String EXT = ".nmt";

    private final Path root;
    private volatile long capBytes;
    private final ConcurrentHashMap<Path, long[]> index = new ConcurrentHashMap<>();
    private final AtomicLong total = new AtomicLong();
    private volatile boolean scanned;
    private volatile java.util.function.@Nullable Consumer<Path> deleteListener;

    public TileDiskCache(Path root, long capBytes) {
        this.root = root;
        this.capBytes = capBytes;
    }

    public Path root() {
        return root;
    }

    public void setCap(long bytes) {
        capBytes = bytes;
    }

    public long totalBytes() {
        return total.get();
    }

    /** Walks the cache once to learn its size and access order. Worker only. */
    public void scan() {
        if (scanned) {
            return;
        }
        scanned = true;
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.forEach(p -> {
                if (p.getFileName().toString().endsWith(EXT)) {
                    try {
                        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                        index.put(p, new long[] {a.size(), a.lastModifiedTime().toMillis()});
                        total.addAndGet(a.size());
                    } catch (IOException e) {
                        // vanished meanwhile
                    }
                }
            });
        } catch (IOException | RuntimeException e) {
            // an unreadable cache is an empty cache
        }
        PerfCounters.cacheBytes.set(total.get());
        evict();
    }

    /** The directory of one server, world and dimension. */
    public Path dimensionDir(String serverKey, String worldId, String dimNamespace, String dimPath) {
        return root.resolve(safe(serverKey)).resolve(safe(worldId)).resolve(safe(dimNamespace))
                .resolve(safe(dimPath));
    }

    public static Path tileFile(Path dimensionDir, int lod, int tx, int tz) {
        return dimensionDir.resolve(Integer.toString(lod)).resolve(tx + "." + tz + EXT);
    }

    /** True when the file is in the cache. Any thread. */
    public boolean exists(Path file) {
        return index.containsKey(file);
    }

    /** Reads a tile file, stamping its access time; null when absent or unreadable. */
    public byte @Nullable [] read(Path file) {
        try {
            byte[] b = Files.readAllBytes(file);
            long now = System.currentTimeMillis();
            try {
                Files.setLastModifiedTime(file, FileTime.fromMillis(now));
            } catch (IOException e) {
                // read-only cache still works
            }
            long[] e = index.get(file);
            if (e != null) {
                e[1] = now;
            }
            PerfCounters.tilesRead.incrementAndGet();
            return b;
        } catch (NoSuchFileException e) {
            forget(file);
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads the first {@code n} bytes of a tile file (its header) without stamping the access time; null if short. */
    public byte @Nullable [] readHead(Path file, int n) {
        try (java.io.InputStream in = Files.newInputStream(file)) {
            byte[] b = in.readNBytes(n);
            return b.length == n ? b : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Told the path of every file this cache deletes (corrupt, evicted or cleared). Server cache only. */
    public void onDelete(java.util.function.@Nullable Consumer<Path> listener) {
        deleteListener = listener;
    }

    /** Writes atomically, then evicts least-recently-used files until under the cap. */
    public void write(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        long[] old = index.put(file, new long[] {data.length, System.currentTimeMillis()});
        total.addAndGet(data.length - (old != null ? old[0] : 0));
        PerfCounters.tilesWritten.incrementAndGet();
        evict();
        PerfCounters.cacheBytes.set(total.get());
    }

    /** Deletes one file (a corrupt tile). */
    public void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // best effort
        }
        forget(file);
    }

    /** Deletes everything under {@code dir} (the "clear map cache" action). */
    public void deleteTree(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // best effort
                }
                forget(p);
            });
        } catch (IOException e) {
            // best effort
        }
    }

    private void forget(Path p) {
        long[] e = index.remove(p);
        if (e != null) {
            total.addAndGet(-e[0]);
            java.util.function.Consumer<Path> l = deleteListener;
            if (l != null) {
                l.accept(p);
            }
        }
    }

    /** Lists the cached tiles of a dimension at one LOD as {tx, tz} pairs. Worker or screen-open only. */
    public ArrayList<int[]> list(Path dimensionDir, int lod) {
        ArrayList<int[]> out = new ArrayList<>();
        String prefix = dimensionDir.resolve(Integer.toString(lod)).toString();
        for (Path p : index.keySet()) {
            if (p.getParent() != null && p.getParent().toString().equals(prefix)) {
                String n = p.getFileName().toString();
                n = n.substring(0, n.length() - EXT.length());
                int dot = n.indexOf('.');
                try {
                    out.add(new int[] {Integer.parseInt(n.substring(0, dot)), Integer.parseInt(n.substring(dot + 1))});
                } catch (RuntimeException e) {
                    // foreign file
                }
            }
        }
        return out;
    }

    void evict() {
        long cap = capBytes;
        if (total.get() <= cap) {
            return;
        }
        // down to 90 % of the cap, so a full cache sorts its index once per many writes, not on every write
        long target = cap - cap / 10;
        ArrayList<Map.Entry<Path, long[]>> all = new ArrayList<>(index.entrySet());
        all.sort((a, b) -> Long.compare(a.getValue()[1], b.getValue()[1]));
        for (Map.Entry<Path, long[]> e : all) {
            if (total.get() <= target) {
                break;
            }
            delete(e.getKey());
        }
    }

    /** Keeps a path segment to {@code [a-z0-9_.-]}, never {@code .} or {@code ..}. */
    public static String safe(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' ? c : c == '.' ? '.' : '_');
        }
        String r = b.toString().replace("..", "__");
        return r.isEmpty() || r.equals(".") ? "_" : r;
    }

    /** Hex of the first 8 bytes of SHA-256 over {@code s}: the server key, so no address is ever written. */
    public static String hashKey(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
