package za.co.neroland.neromaps.server.tiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neromaps.fog.TileMask;
import za.co.neroland.neromaps.fog.Visibility;
import za.co.neroland.neromaps.network.MapNetwork;
import za.co.neroland.neromaps.network.Payloads;
import za.co.neroland.neromaps.server.MapServer;
import za.co.neroland.neromaps.store.PlayerStore;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * The one read path for server tiles outside the link module (shared explored maps on the world map, wall maps and map
 * tables). Fog of war is checked first, on the server thread, against live grants: a tile the viewer may not see at
 * all gets no answer, exactly like a tile that was never captured, with no work queued, so neither content nor timing
 * differs. A partly visible tile is masked on the worker and its etag is the content hash of the masked bytes, so a
 * change in a hidden chunk answers the same etag (and, when the client holds it, the same empty "unchanged" answer).
 * Answers are sent from the server thread; a revocation in between bumps the viewer's generation and drops them.
 *
 * <p><b>Budgets.</b> Each viewer may have at most {@link #MAX_INFLIGHT} tiles queued or waiting to be sent and may draw
 * at most {@link LinkTiles#VIEWER_BYTES_PER_MINUTE} of answers per minute; all viewers together may hold at most
 * {@link #MAX_QUEUED} jobs on the capture worker, so capture always keeps most of its queue. Requests past any of
 * these are dropped (the client asks again on its own schedule). Only dimensions the server actually has are served.
 */
public final class TileService {

    /** Shared-area requests stop at LOD 2 (64 x 64 chunks): the fog check stays bounded on the server thread. */
    public static final int MAX_LOD = 2;
    /** Tiles one viewer may have queued on the worker or waiting in the outbox. */
    public static final int MAX_INFLIGHT = 64;
    /** Tile jobs all viewers together may hold on the capture worker (its queue is {@link CaptureService#QUEUE}). */
    public static final int MAX_QUEUED = CaptureService.QUEUE / 4;

    private record Answer(UUID viewer, int generation, Payloads.TileData data) { }

    /** What one tile request is answered with: the etag the viewer sees and the bytes (empty = "you have it"). */
    public record Reply(long etag, byte[] bytes) { }

    private static final ConcurrentLinkedQueue<Answer> OUTBOX = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Integer> GENERATION = new ConcurrentHashMap<>();
    static final TileBudget BUDGET = new TileBudget(LinkTiles.VIEWER_BYTES_PER_MINUTE, MAX_INFLIGHT, MAX_QUEUED);

    private TileService() {
    }

    /** Ends any answers in flight for {@code viewer} (revocation, erasure, logout). */
    public static void dropViewer(UUID viewer) {
        GENERATION.merge(viewer, 1, Integer::sum);
    }

    /** Drops the viewer's rate windows (logout, erasure). */
    public static void forget(UUID viewer) {
        dropViewer(viewer);
        BUDGET.forget(viewer);
    }

    /** True when {@code dim} names one of the server's levels (no store, index or directory exists for anything else). */
    public static boolean knownDimension(MinecraftServer s, String dim) {
        if (dim == null || dim.isEmpty()) {
            return false;
        }
        for (ServerLevel l : s.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(dim)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The answer to one tile for one mask (worker thread; pure, tested): null when nothing is to be sent (the masked
     * copy shows nothing, exactly like a missing tile); an empty byte array when the client already holds the etag.
     */
    public static @org.jetbrains.annotations.Nullable Reply answer(ServerTileStore.Encoded e, TileMask mask, long clientEtag)
            throws TileCorruptException {
        byte[] bytes = e.bytes();
        long etag = e.etag();
        if (!mask.all()) {
            byte[] masked = mask.encodeMasked(TileCodec.decode(e.bytes()));
            if (masked == null) {
                return null;
            }
            bytes = masked;
            etag = TileCodec.contentHash(masked);
        }
        return new Reply(etag, etag == clientEtag ? new byte[0] : bytes);
    }

    public static void request(ServerPlayer p, Payloads.TileRequest msg) {
        ServerLevel level = p.level();
        MinecraftServer s = level.getServer();
        CaptureService.PERF.tileRequests.incrementAndGet();
        CaptureService.noteDemand();
        if (!MapServer.policy().serverCapture()) {
            return;
        }
        int lod = Math.min(MAX_LOD, msg.lod());
        String dim = za.co.neroland.neromaps.store.Waypoint.cleanId(msg.dimension(), "");
        if (!knownDimension(s, dim)) {
            return; // never builds a store, an index or a directory for a made-up dimension
        }
        UUID viewer = p.getUUID();
        if (BUDGET.overBudget(viewer, System.currentTimeMillis())) {
            return;
        }
        CaptureService.ensureRunning(s);
        Visibility v = Visibility.of(PlayerStore.get(s), viewer, dim, MapServer.policy().publicMap());
        int gen = GENERATION.getOrDefault(viewer, 0);
        for (int i = 0; i < msg.tiles().length; i++) {
            long key = msg.tiles()[i];
            int tx = TileKey.tx(key);
            int tz = TileKey.tz(key);
            if (!LinkTiles.inWorld(lod, tx, tz)) {
                continue;
            }
            long t0 = System.nanoTime();
            TileMask mask = TileMask.of(v, lod, tx, tz);
            CaptureService.PERF.maskNanos.addAndGet(System.nanoTime() - t0);
            CaptureService.PERF.masks.incrementAndGet();
            if (mask.none()) {
                CaptureService.PERF.tilesHidden.incrementAndGet();
                continue; // hidden: no answer, no work, same as a tile that does not exist
            }
            if (!BUDGET.tryReserve(viewer)) {
                break; // asked again on the client's next round
            }
            long clientEtag = msg.etags()[i];
            boolean queued = CaptureService.onWorker(dim, store -> {
                BUDGET.dequeued();
                boolean sent = false;
                try {
                    ServerTileStore.Encoded e = store.encoded(lod, tx, tz);
                    Reply r = e == null ? null : answer(e, mask, clientEtag);
                    if (r != null) {
                        OUTBOX.add(new Answer(viewer, gen, new Payloads.TileData(dim, lod, tx, tz, r.etag(), r.bytes())));
                        sent = true;
                    }
                } catch (TileCorruptException ex) {
                    // recovered by the store on its next read; no answer meanwhile
                } finally {
                    if (!sent) {
                        BUDGET.finished(viewer);
                    }
                }
            });
            if (!queued) {
                BUDGET.dequeued();
                BUDGET.finished(viewer);
                break;
            }
        }
    }

    /** Server tick: sends finished answers to viewers still online with an unchanged generation. */
    public static void drain(MinecraftServer s) {
        Answer a;
        int sent = 0;
        long now = System.currentTimeMillis();
        while (sent < 64 && (a = OUTBOX.poll()) != null) {
            BUDGET.finished(a.viewer);
            if (GENERATION.getOrDefault(a.viewer, 0) != a.generation) {
                continue;
            }
            ServerPlayer p = s.getPlayerList().getPlayer(a.viewer);
            if (p != null && MapNetwork.send(p, a.data)) {
                BUDGET.sent(a.viewer, a.data.data().length + 64L, now);
                CaptureService.PERF.tilesSent.incrementAndGet();
                sent++;
            }
        }
    }

    public static void reset() {
        OUTBOX.clear();
        GENERATION.clear();
        BUDGET.reset();
    }
}
