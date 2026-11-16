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
import za.co.neroland.neromaps.tile.Tile;
import za.co.neroland.neromaps.tile.TileCodec;
import za.co.neroland.neromaps.tile.TileCorruptException;
import za.co.neroland.neromaps.tile.TileKey;

/**
 * The one read path for server tiles outside the link module (shared explored maps on the world map; decision 007).
 * Fog of war is checked first, on the server thread, against live grants: a tile the viewer may not see at all gets
 * no answer, exactly like a tile that was never captured, with no work queued, so neither content nor timing differs.
 * A partly visible tile is masked on the worker and its etag mixed with the mask. Answers are sent from the server
 * thread; a revocation in between bumps the viewer's generation and drops them.
 */
public final class TileService {

    /** Shared-area requests stop at LOD 2 (64 x 64 chunks): the fog check stays bounded on the server thread. */
    public static final int MAX_LOD = 2;

    private record Answer(UUID viewer, int generation, Payloads.TileData data) { }

    private static final ConcurrentLinkedQueue<Answer> OUTBOX = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Integer> GENERATION = new ConcurrentHashMap<>();

    private TileService() {
    }

    /** Ends any answers in flight for {@code viewer} (revocation, erasure, logout). */
    public static void dropViewer(UUID viewer) {
        GENERATION.merge(viewer, 1, Integer::sum);
    }

    public static void request(ServerPlayer p, Payloads.TileRequest msg) {
        ServerLevel level = p.level();
        MinecraftServer s = level.getServer();
        CaptureService.PERF.tileRequests.incrementAndGet();
        CaptureService.noteDemand();
        if (!MapServer.policy().serverCapture()) {
            return;
        }
        CaptureService.ensureRunning(s);
        int lod = Math.min(MAX_LOD, msg.lod());
        String dim = za.co.neroland.neromaps.store.Waypoint.cleanId(msg.dimension(), "");
        Visibility v = Visibility.of(PlayerStore.get(s), p.getUUID(), dim, MapServer.policy().publicMap());
        UUID viewer = p.getUUID();
        int gen = GENERATION.getOrDefault(viewer, 0);
        for (int i = 0; i < msg.tiles().length; i++) {
            long key = msg.tiles()[i];
            int tx = TileKey.tx(key);
            int tz = TileKey.tz(key);
            long t0 = System.nanoTime();
            TileMask mask = TileMask.of(v, lod, tx, tz);
            CaptureService.PERF.maskNanos.addAndGet(System.nanoTime() - t0);
            CaptureService.PERF.masks.incrementAndGet();
            if (mask.none()) {
                CaptureService.PERF.tilesHidden.incrementAndGet();
                continue; // hidden: no answer, no work, same as a tile that does not exist
            }
            long clientEtag = msg.etags()[i];
            CaptureService.onWorker(dim, store -> {
                ServerTileStore.Encoded e = store.encoded(lod, tx, tz);
                if (e == null) {
                    return; // never captured: no answer
                }
                byte[] bytes = e.bytes();
                long etag = mask.etag(e.etag());
                if (etag == clientEtag) {
                    bytes = new byte[0];
                } else if (!mask.all()) {
                    try {
                        Tile t = TileCodec.decode(e.bytes());
                        bytes = TileCodec.encode(mask.apply(t));
                    } catch (TileCorruptException ex) {
                        return;
                    }
                }
                OUTBOX.add(new Answer(viewer, gen, new Payloads.TileData(dim, lod, tx, tz, etag, bytes)));
            });
        }
    }

    /** Server tick: sends finished answers to viewers still online with an unchanged generation. */
    public static void drain(MinecraftServer s) {
        Answer a;
        int sent = 0;
        while (sent < 64 && (a = OUTBOX.poll()) != null) {
            if (GENERATION.getOrDefault(a.viewer, 0) != a.generation) {
                continue;
            }
            ServerPlayer p = s.getPlayerList().getPlayer(a.viewer);
            if (p != null && MapNetwork.send(p, a.data)) {
                CaptureService.PERF.tilesSent.incrementAndGet();
                sent++;
            }
        }
    }

    public static void reset() {
        OUTBOX.clear();
        GENERATION.clear();
    }
}
