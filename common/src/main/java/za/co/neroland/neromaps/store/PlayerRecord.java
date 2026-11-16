package za.co.neroland.neromaps.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything NeroMaps keeps about one player, and nothing else: waypoints (with the last three death
 * points), the explored-chunk bitsets, the consent lists (grants this player gave, requests waiting for this player)
 * and two privacy toggles. No name, no position history, no timestamps beyond a request's day. Mutable; the owning
 * {@link MapPlayerState} marks itself dirty after every change.
 */
public final class PlayerRecord {

    public static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Waypoint.CODEC.listOf().optionalFieldOf("waypoints", List.of()).forGetter(r -> r.waypoints),
            Codec.INT.optionalFieldOf("next", 1).forGetter(r -> r.nextId),
            DeathPoint.CODEC.listOf().optionalFieldOf("deaths", List.of()).forGetter(r -> r.deaths),
            ExploredMap.CODEC.optionalFieldOf("explored", new ExploredMap()).forGetter(r -> r.explored),
            Grant.CODEC.listOf().optionalFieldOf("grants", List.of()).forGetter(r -> r.grants),
            ShareRequest.CODEC.listOf().optionalFieldOf("incoming", List.of()).forGetter(r -> r.incoming),
            Codec.BOOL.optionalFieldOf("deathWaypoints", true).forGetter(r -> r.deathWaypoints),
            Codec.BOOL.optionalFieldOf("acceptRequests", true).forGetter(r -> r.acceptRequests),
            Codec.LONG.optionalFieldOf("epoch", 0L).forGetter(r -> r.epoch)
    ).apply(i, PlayerRecord::new));

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    final List<Waypoint> waypoints;
    int nextId;
    final List<DeathPoint> deaths;
    final ExploredMap explored;
    final List<Grant> grants;
    final List<ShareRequest> incoming;
    boolean deathWaypoints;
    boolean acceptRequests;
    /**
     * A random, non-identifying id of this record's life: a new record (first use, or after an erasure) gets a new
     * one, which tells the companion app to wipe what it cached from the old one.
     */
    long epoch;

    public PlayerRecord() {
        this(List.of(), 1, List.of(), new ExploredMap(), List.of(), List.of(), true, true, 0L);
    }

    private PlayerRecord(List<Waypoint> waypoints, int nextId, List<DeathPoint> deaths, ExploredMap explored,
            List<Grant> grants, List<ShareRequest> incoming, boolean deathWaypoints, boolean acceptRequests,
            long epoch) {
        this.waypoints = new ArrayList<>(waypoints);
        this.nextId = Math.max(1, nextId);
        this.deaths = new ArrayList<>(deaths.subList(0, Math.min(DeathPoint.KEEP, deaths.size())));
        this.explored = explored;
        this.grants = new ArrayList<>(grants);
        this.incoming = new ArrayList<>(incoming);
        this.deathWaypoints = deathWaypoints;
        this.acceptRequests = acceptRequests;
        this.epoch = epoch != 0 ? epoch : newEpoch();
        for (Waypoint w : this.waypoints) {
            this.nextId = Math.max(this.nextId, w.id() + 1);
        }
    }

    public List<Waypoint> waypoints() {
        return java.util.Collections.unmodifiableList(waypoints);
    }

    public Optional<Waypoint> waypoint(int id) {
        for (Waypoint w : waypoints) {
            if (w.id() == id) {
                return Optional.of(w);
            }
        }
        return Optional.empty();
    }

    public List<DeathPoint> deaths() {
        return java.util.Collections.unmodifiableList(deaths);
    }

    public ExploredMap explored() {
        return explored;
    }

    public List<Grant> grants() {
        return java.util.Collections.unmodifiableList(grants);
    }

    public List<ShareRequest> incoming() {
        return java.util.Collections.unmodifiableList(incoming);
    }

    public boolean deathWaypoints() {
        return deathWaypoints;
    }

    public boolean acceptRequests() {
        return acceptRequests;
    }

    static long newEpoch() {
        long e;
        do {
            e = RANDOM.nextLong() & 0xFFFFFFFFFFFFL;
        } while (e == 0);
        return e;
    }

    /** The record's life id (see {@link #epoch}), as base 36. */
    public String epoch() {
        return Long.toString(epoch, 36);
    }

    /** True when the record holds nothing worth keeping (it can be dropped on save). */
    public boolean isEmpty() {
        return waypoints.isEmpty() && deaths.isEmpty() && explored.isEmpty() && grants.isEmpty()
                && incoming.isEmpty() && deathWaypoints && acceptRequests;
    }
}
