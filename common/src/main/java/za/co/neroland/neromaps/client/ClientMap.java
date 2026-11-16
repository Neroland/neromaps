package za.co.neroland.neromaps.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.cache.TileDiskCache;
import za.co.neroland.neromaps.client.hud.MinimapHud;
import za.co.neroland.neromaps.client.screen.WorldMapScreen;
import za.co.neroland.neromaps.compat.PlayerMessages;
import za.co.neroland.neromaps.engine.LongObjMap;
import za.co.neroland.neromaps.engine.MapStore;
import za.co.neroland.neromaps.engine.ScanResult;
import za.co.neroland.neromaps.perf.PerfCounters;
import za.co.neroland.neromaps.policy.PolicySnapshot;
import za.co.neroland.neromaps.scan.ChunkColumns;
import za.co.neroland.neromaps.scan.ColumnScanner;
import za.co.neroland.neromaps.settings.MinimapSettings;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * The client map engine's main-thread side. Built only from chunks this client has loaded: no server-side
 * NeroMaps data is needed, so it works on a vanilla or Core-only server. Each tick it notices newly loaded chunks
 * (by polling the client chunk cache, identical on every loader), coalesces block-change dirty marks, snapshots a
 * budgeted number of chunks and hands them to the scan worker through a bounded queue, applies finished scans,
 * and flushes tiles to the disk cache on the I/O worker. Nothing here records a position: the cache holds terrain
 * only, keyed by a hash of the server address.
 */
public final class ClientMap {

    public static final MinimapSettings SETTINGS = new MinimapSettings();
    /** Scan-queue bound. */
    public static final int SCAN_QUEUE = 256;
    /** Chunks snapshotted per tick, and the time cap for that work. */
    public static final int SNAPSHOTS_PER_TICK = 16;
    public static final long SNAPSHOT_BUDGET_NANOS = 1_000_000L;
    /** Block-change coalescing: rescan after this many quiet ticks, or this many ticks after the first mark. */
    public static final int QUIET_TICKS = 10;
    public static final int MAX_WAIT_TICKS = 40;
    public static final int FLUSH_TICKS = 600;

    private static @Nullable Path settingsFile;
    private static @Nullable ThreadPoolExecutor scanExec;
    private static @Nullable ExecutorService ioExec;
    private static @Nullable TileDiskCache cache;
    private static @Nullable MapStore store;
    private static @Nullable ClientLevel level;
    private static String dimensionId = "";
    private static int epoch;
    private static long ticks;
    private static int pollRow;
    private static boolean listenerRegistered;
    private static boolean roofed;
    private static int logicalTop;
    private static int caveSlice = Integer.MIN_VALUE;
    private static boolean caveActive;
    private static int effectRows;

    private static final LongObjMap<long[]> DIRTY = new LongObjMap<>(1024);
    private static final LongObjMap<long[]> CAVE_DIRTY = new LongObjMap<>(256);
    private static final LongObjMap<int[]> SEEN = new LongObjMap<>(2048);
    private static final ConcurrentLinkedQueue<ScanResult> RESULTS = new ConcurrentLinkedQueue<>();
    private static final long[] PICK_KEYS = new long[SNAPSHOTS_PER_TICK];
    private static final long[] PICK_DIST = new long[SNAPSHOTS_PER_TICK];

    private ClientMap() {
    }

    /** Client setup: settings file and workers. */
    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        Path game = mc.gameDirectory.toPath();
        settingsFile = game.resolve("config").resolve("neromaps-client.properties");
        SETTINGS.load(settingsFile);
        presetsFile = game.resolve("config").resolve("neromaps-presets.json");
        PRESETS.load(presetsFile);
        cache = new TileDiskCache(game.resolve("neromaps").resolve("cache").resolve("v1"), SETTINGS.cacheMb * 1024L * 1024L);
        scanExec = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(SCAN_QUEUE),
                r -> daemon(r, "NeroMaps scan"));
        ioExec = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> daemon(r, "NeroMaps io"));
        TileDiskCache c = cache;
        ioExec.execute(c::scan);
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    }

    /** Built-in and the player's own display presets (client preferences, never sent anywhere). */
    public static final za.co.neroland.neromaps.settings.MapPresets PRESETS =
            new za.co.neroland.neromaps.settings.MapPresets();
    private static @Nullable Path presetsFile;

    public static void savePresets() {
        Path f = presetsFile;
        if (f == null) {
            return;
        }
        try {
            PRESETS.save(f);
        } catch (IOException e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] could not save presets ({})", e.getClass().getSimpleName());
        }
    }

    /** The cycle-preset key: applies the next preset and says which. */
    public static void cyclePreset(Minecraft mc) {
        String next = PRESETS.next(SETTINGS.preset);
        if (PRESETS.apply(next, SETTINGS)) {
            saveSettings();
            say(mc, Component.translatable("gui.neromaps.settings.presets.applied", presetName(next)));
        }
    }

    /** A preset's display name: built-ins are translated, the player's own are shown as typed. */
    public static Component presetName(String name) {
        return PRESETS.isBuiltIn(name) ? Component.translatable("gui.neromaps.settings.presets." + name)
                : Component.literal(name);
    }

    public static void saveSettings() {
        Path f = settingsFile;
        if (f == null) {
            return;
        }
        try {
            SETTINGS.save(f);
        } catch (IOException e) {
            NeroMapsCommon.LOGGER.warn("[NeroMaps] could not save client settings ({})", e.getClass().getSimpleName());
        }
    }

    public static @Nullable MapStore store() {
        return store;
    }

    public static @Nullable TileDiskCache cache() {
        return cache;
    }

    public static String dimensionId() {
        return dimensionId;
    }

    public static boolean roofed() {
        return roofed;
    }

    public static int effectRows() {
        return effectRows;
    }

    /** Cave mode as drawn: the player's setting AND the server policy. */
    public static boolean caveActive() {
        return caveActive;
    }

    /** The cache directory of the current server and world (for the world map's dimension switcher). */
    public static @Nullable Path worldDir() {
        TileDiskCache c = cache;
        Path d = dimensionDir;
        return c == null || d == null ? null : d.getParent().getParent();
    }

    private static @Nullable Path dimensionDir;
    private static boolean reopen;
    private static boolean caveLockShown;

    /** Grid and chunk-border overlays, when the server's policy allows them ({@code debugOverlays}). */
    public static boolean overlaysAllowed() {
        return ClientState.INSTANCE.allows(PolicySnapshot.Feature.OVERLAYS);
    }

    /** The server sent a world id: reopen the disk cache under it on the next tick. */
    public static void reopenStore() {
        reopen = true;
    }

    // --- tick ----------------------------------------------------------------------------------------------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (settingsFile == null) {
            init();
        }
        if (!listenerRegistered && mc.getResourceManager() instanceof ReloadableResourceManager rm) {
            listenerRegistered = true;
            rm.registerReloadListener(ColourTables.INSTANCE);
            ColourTables.reload(rm);
        }
        ClientState.INSTANCE.tick(mc);
        handleKeys(mc);
        ClientLevel lvl = mc.level;
        if (reopen) {
            reopen = false;
            level = null; // forces switchLevel below with the new world id
        }
        LocalPlayer player = mc.player;
        if (lvl != level) {
            switchLevel(mc, lvl);
        }
        MapStore s = store;
        if (lvl == null || player == null || s == null) {
            return;
        }
        ticks++;
        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        updateCave(lvl, player, pcx, pcz);
        pollChunks(lvl, pcx, pcz);
        submitDirty(lvl, pcx, pcz);
        ScanResult r;
        int applied = 0;
        while (applied < 64 && (r = RESULTS.poll()) != null) {
            if (r.cave ? caveActive && r.epoch == epochCave : r.epoch == epoch) {
                s.apply(r);
            }
            applied++;
        }
        s.tick(pcx >> 4, pcz >> 4);
        if (ticks % FLUSH_TICKS == 0) {
            s.flush();
        }
        effectRows = effectRows(player);
        Readouts.update(mc, lvl, player);
        PerfCounters.scanQueue.set(scanExec != null ? scanExec.getQueue().size() : 0);
        PerfCounters.dirtyChunks = DIRTY.size() + CAVE_DIRTY.size();
    }

    private static void handleKeys(Minecraft mc) {
        while (MapKeys.WORLD_MAP.consumeClick()) {
            if (screen(mc) == null && mc.level != null) {
                setScreen(mc, new WorldMapScreen());
            }
        }
        while (MapKeys.TOGGLE_MINIMAP.consumeClick()) {
            SETTINGS.enabled = !SETTINGS.enabled;
            SETTINGS.changed();
            saveSettings();
        }
        while (MapKeys.ZOOM_IN.consumeClick()) {
            SETTINGS.zoomStep(true);
            saveSettings();
        }
        while (MapKeys.ZOOM_OUT.consumeClick()) {
            SETTINGS.zoomStep(false);
            saveSettings();
        }
        while (MapKeys.NEW_WAYPOINT.consumeClick()) {
            if (screen(mc) == null) {
                ClientWaypoints.key(mc);
            }
        }
        while (MapKeys.TOGGLE_BEAMS.consumeClick()) {
            SETTINGS.beams = !SETTINGS.beams;
            SETTINGS.changed();
            saveSettings();
            say(mc, Component.translatable(SETTINGS.beams ? "neromaps.beams.on" : "neromaps.beams.off"));
        }
        while (MapKeys.CYCLE_PRESET.consumeClick()) {
            cyclePreset(mc);
        }
        while (MapKeys.OPEN_SETTINGS.consumeClick()) {
            if (screen(mc) == null) {
                setScreen(mc, new za.co.neroland.neromaps.client.screen.MapSettingsScreen(null));
            }
        }
    }

    // --- the screen and the HUD flag moved to Gui in 26.2 ---------------------------------------------------

    public static net.minecraft.client.gui.screens.@Nullable Screen screen(Minecraft mc) {
        //? if <26.2 {
        /*return mc.screen;
        *///?} else {
        return mc.gui.screen();
        //?}
    }

    public static void setScreen(Minecraft mc, net.minecraft.client.gui.screens.@Nullable Screen screen) {
        //? if <26.2 {
        /*mc.setScreen(screen);
        *///?} else {
        mc.gui.setScreen(screen);
        //?}
    }

    public static boolean hudHidden(Minecraft mc) {
        //? if <26.2 {
        /*return mc.options.hideGui;
        *///?} else {
        return mc.gui.hud.isHidden();
        //?}
    }

    private static void say(Minecraft mc, Component c) {
        if (mc.player != null) {
            PlayerMessages.overlay(mc.player, c);
        }
    }

    private static void switchLevel(Minecraft mc, @Nullable ClientLevel lvl) {
        MapStore old = store;
        if (old != null) {
            old.close();
        }
        store = null;
        level = lvl;
        epoch++;
        DIRTY.clear();
        CAVE_DIRTY.clear();
        SEEN.clear();
        RESULTS.clear();
        caveSlice = Integer.MIN_VALUE;
        dimensionDir = null;
        dimensionId = "";
        if (lvl == null || ioExec == null) {
            return;
        }
        var dim = lvl.dimension().identifier();
        dimensionId = dim.toString();
        roofed = lvl.dimensionType().hasCeiling();
        //? if >=1.21.4 {
        int minY = lvl.getMinY();
        //?} else {
        /*int minY = lvl.getMinBuildHeight();
        *///?}
        logicalTop = minY + lvl.dimensionType().logicalHeight() - 1;
        TileDiskCache c = cache;
        if (c != null) {
            c.setCap(SETTINGS.cacheMb * 1024L * 1024L);
            if (SETTINGS.cacheMb > 0) {
                dimensionDir = c.dimensionDir(serverKey(mc), ClientState.INSTANCE.worldId(), dim.getNamespace(),
                        dim.getPath());
            }
        }
        store = new MapStore(dimensionDir != null ? c : null, dimensionDir, ioExec, SETTINGS.memoryTiles);
        MinimapHud.reset();
    }

    /** Hash of the server address (or of the save folder in single player): no address is ever written. */
    private static String serverKey(Minecraft mc) {
        var server = mc.getCurrentServer();
        if (server != null) {
            return TileDiskCache.hashKey("mp:" + server.ip.trim().toLowerCase(java.util.Locale.ROOT));
        }
        var sp = mc.getSingleplayerServer();
        if (sp != null) {
            Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            return "sp-" + TileDiskCache.hashKey(name == null ? root.toString() : name.toString());
        }
        return "unknown";
    }

    // --- chunk discovery and dirty marks -------------------------------------------------------------------

    /** Called from the block-change mixin on the client thread. */
    public static void blockChanged(int x, int z) {
        if (store == null || !Minecraft.getInstance().isSameThread()) {
            return;
        }
        long key = TileKey.chunk(x >> 4, z >> 4);
        mark(DIRTY, key, false);
        if (caveActive) {
            mark(CAVE_DIRTY, key, false);
        }
    }

    private static void mark(LongObjMap<long[]> map, long key, boolean immediate) {
        long[] m = map.get(key);
        long now = ticks;
        if (m == null) {
            map.put(key, new long[] {immediate ? now - MAX_WAIT_TICKS : now, immediate ? now - QUIET_TICKS : now});
        } else if (!immediate) {
            m[1] = now;
        } else {
            m[0] = now - MAX_WAIT_TICKS;
        }
    }

    private static void pollChunks(ClientLevel lvl, int pcx, int pcz) {
        int r = Minecraft.getInstance().options.getEffectiveRenderDistance() + 1;
        int rows = 2 * r + 1;
        int perTick = Math.max(1, (rows + 3) / 4);
        for (int k = 0; k < perTick; k++) {
            int dz = pollRow - r;
            pollRow = (pollRow + 1) % rows;
            int cz = pcz + dz;
            for (int dx = -r; dx <= r; dx++) {
                int cx = pcx + dx;
                long key = TileKey.chunk(cx, cz);
                LevelChunk chunk = lvl.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                int[] seen = SEEN.get(key);
                if (chunk == null) {
                    if (seen != null) {
                        SEEN.remove(key);
                    }
                    continue;
                }
                int id = System.identityHashCode(chunk);
                if (seen == null) {
                    SEEN.put(key, new int[] {id});
                    mark(DIRTY, key, true);
                    if (caveActive) {
                        mark(CAVE_DIRTY, key, true);
                    }
                } else if (seen[0] != id) {
                    seen[0] = id;
                    mark(DIRTY, key, true);
                }
            }
        }
        // forget chunks far outside the view (unloaded long ago)
        if (ticks % 200 == 0) {
            int far = r + 4;
            SEEN.removeIf(k -> Math.abs(TileKey.chunkX(k) - pcx) > far || Math.abs(TileKey.chunkZ(k) - pcz) > far);
        }
    }

    private static void updateCave(ClientLevel lvl, LocalPlayer player, int pcx, int pcz) {
        boolean allowed = ClientState.INSTANCE.allows(PolicySnapshot.Feature.CAVE_MODE);
        boolean want = SETTINGS.caveMode && allowed;
        if (SETTINGS.caveMode && !allowed && !caveLockShown) {
            caveLockShown = true; // the setting stays as the player chose it; the server's policy wins and says so
            PlayerMessages.overlay(player, Component.translatable(PolicySnapshot.Feature.CAVE_MODE.lockKey()));
        } else if (allowed || !SETTINGS.caveMode) {
            caveLockShown = false;
        }
        MapStore s = store;
        if (!want) {
            if (caveActive && s != null) {
                s.clearCave();
                CAVE_DIRTY.clear();
                MinimapHud.reset();
            }
            caveActive = false;
            caveSlice = Integer.MIN_VALUE;
            return;
        }
        int slice = player.getBlockY() + 2;
        if (!caveActive || Math.abs(slice - caveSlice) >= 4) {
            caveActive = true;
            caveSlice = slice;
            if (s != null) {
                s.clearCave();
            }
            CAVE_DIRTY.clear();
            epochCave++;
            int span = MinimapHud.windowChunks() / 2 + 1;
            for (int dz = -span; dz <= span; dz++) {
                for (int dx = -span; dx <= span; dx++) {
                    long key = TileKey.chunk(pcx + dx, pcz + dz);
                    if (SEEN.containsKey(key)) {
                        mark(CAVE_DIRTY, key, true);
                    }
                }
            }
            MinimapHud.reset();
        }
    }

    private static int epochCave;

    private static void submitDirty(ClientLevel lvl, int pcx, int pcz) {
        long t0 = System.nanoTime();
        if (caveActive) {
            submitFrom(CAVE_DIRTY, lvl, pcx, pcz, true, t0);
        }
        submitFrom(DIRTY, lvl, pcx, pcz, false, t0);
    }

    private static void submitFrom(LongObjMap<long[]> map, ClientLevel lvl, int pcx, int pcz, boolean cave, long t0) {
        if (map.size() == 0) {
            return;
        }
        ThreadPoolExecutor exec = scanExec;
        if (exec == null) {
            return;
        }
        int[] n = {0};
        long now = ticks;
        java.util.Arrays.fill(PICK_DIST, Long.MAX_VALUE);
        map.forEach((key, m) -> {
            if (now - m[1] < QUIET_TICKS && now - m[0] < MAX_WAIT_TICKS) {
                return;
            }
            long dx = TileKey.chunkX(key) - pcx;
            long dz = TileKey.chunkZ(key) - pcz;
            long d = dx * dx + dz * dz;
            // keep the nearest SNAPSHOTS_PER_TICK
            int worst = 0;
            for (int i = 1; i < PICK_DIST.length; i++) {
                if (PICK_DIST[i] > PICK_DIST[worst]) {
                    worst = i;
                }
            }
            if (d < PICK_DIST[worst]) {
                PICK_DIST[worst] = d;
                PICK_KEYS[worst] = key;
                n[0]++;
            }
        });
        int ep = cave ? epochCave : epoch;
        int slice = caveSlice;
        for (int i = 0; i < PICK_DIST.length; i++) {
            if (PICK_DIST[i] == Long.MAX_VALUE) {
                continue;
            }
            if (System.nanoTime() - t0 > SNAPSHOT_BUDGET_NANOS || exec.getQueue().remainingCapacity() == 0) {
                break;
            }
            long key = PICK_KEYS[i];
            LevelChunk chunk = lvl.getChunkSource().getChunk(TileKey.chunkX(key), TileKey.chunkZ(key),
                    ChunkStatus.FULL, false);
            if (chunk == null) {
                map.remove(key);
                continue;
            }
            ChunkSnapshot.Mode mode = cave ? ChunkSnapshot.Mode.CAVE
                    : roofed ? ChunkSnapshot.Mode.ROOFED : ChunkSnapshot.Mode.SURFACE;
            ChunkSnapshot snap = ChunkSnapshot.of(chunk, lvl, mode, slice);
            int top = logicalTop;
            try {
                exec.execute(() -> scan(snap, mode, top, slice, cave, ep));
                map.remove(key);
                PerfCounters.scanQueue.set(exec.getQueue().size());
            } catch (RejectedExecutionException e) {
                break; // stays dirty; retried next tick
            }
        }
    }

    /** Scan worker. */
    private static void scan(ChunkSnapshot snap, ChunkSnapshot.Mode mode, int top, int slice, boolean cave, int ep) {
        long t0 = System.nanoTime();
        try {
            ChunkColumns cols = new ChunkColumns();
            switch (mode) {
                case CAVE:
                    ColumnScanner.scanCave(snap, slice, cols);
                    break;
                case ROOFED:
                    ColumnScanner.scanRoofed(snap, top, cols);
                    break;
                default:
                    ColumnScanner.scanSurface(snap, cols);
                    break;
            }
            ScanResult r = new ScanResult(snap.chunkX, snap.chunkZ, cols, cave, ep);
            for (int i = 0; i < 256; i++) {
                r.blockIds[i] = ChunkSnapshot.StateKeys.id(cols.block[i]);
                r.biomeIds[i] = snap.biomeIdOf(cols.biome[i]);
            }
            RESULTS.add(r);
        } catch (RuntimeException e) {
            // a chunk that cannot be scanned is skipped; counted, not logged per chunk
            PerfCounters.corruptTiles.incrementAndGet();
        } finally {
            PerfCounters.scans.incrementAndGet();
            PerfCounters.scanNanos.addAndGet(System.nanoTime() - t0);
        }
    }

    private static int effectRows(LocalPlayer player) {
        boolean good = false;
        boolean bad = false;
        for (var e : player.getActiveEffects()) {
            if (!e.showIcon()) {
                continue;
            }
            if (e.getEffect().value().isBeneficial()) {
                good = true;
            } else {
                bad = true;
            }
        }
        return (good ? 1 : 0) + (bad ? 1 : 0);
    }

    /** Clears this server's cache folder and the live map (the "clear map cache" action). */
    public static void clearCache() {
        Path d = worldDir();
        TileDiskCache c = cache;
        ExecutorService io = ioExec;
        switchLevel(Minecraft.getInstance(), null);
        if (d != null && c != null && io != null) {
            io.execute(() -> c.deleteTree(d));
        }
    }

    /** The I/O executor (world-map reads of other dimensions). */
    public static @Nullable ExecutorService io() {
        return ioExec;
    }
}
