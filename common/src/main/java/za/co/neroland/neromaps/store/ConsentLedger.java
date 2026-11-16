package za.co.neroland.neromaps.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The consent rules of decision 009 over a {@link MapPlayerState}, pure and clock-injected so they can be tested
 * without a server. Offers go onto the recipient's record as a pending request with a random server id; only the
 * recipient can accept or decline it (the lookup is in <em>their own</em> incoming list); acceptance writes a grant
 * onto the sharer's record; either side revokes by deleting the grant. Server thread only.
 */
public final class ConsentLedger {

    /** The outcome of an offer. {@code SENT} is also returned when the recipient refuses requests (silently). */
    public enum Offer { SENT, SELF, ALREADY_SHARED, NO_SUCH_WAYPOINT }

    private final MapPlayerState state;
    private final LongSupplier random;

    public ConsentLedger(MapPlayerState state, LongSupplier random) {
        this.state = state;
        this.random = random;
    }

    /**
     * Offers {@code scope} from {@code from} to {@code to}. Returns the outcome; when a request was stored its id is
     * written to {@code idOut[0]} (0 when the recipient refuses requests and nothing was stored).
     */
    public Offer offer(UUID from, UUID to, ShareScope scope, long day, long[] idOut) {
        if (idOut != null && idOut.length > 0) {
            idOut[0] = 0;
        }
        if (from.equals(to)) {
            return Offer.SELF;
        }
        if (scope.kind() == ShareScope.Kind.WAYPOINT
                && state.find(from).flatMap(r -> r.waypoint(scope.waypointId())).isEmpty()) {
            return Offer.NO_SUCH_WAYPOINT;
        }
        for (Grant g : state.grantsOf(from)) {
            if (g.to().equals(to) && g.scope().equals(scope)) {
                return Offer.ALREADY_SHARED;
            }
        }
        PlayerRecord recipient = state.find(to).orElse(null);
        if (recipient != null && !recipient.acceptRequests) {
            return Offer.SENT; // the sender is not told the recipient refuses requests
        }
        recipient = state.record(to);
        recipient.incoming.removeIf(q -> q.from().equals(from) && q.scope().equals(scope));
        while (recipient.incoming.size() >= ShareRequest.MAX_PENDING) {
            recipient.incoming.remove(0); // oldest first
        }
        long id;
        do {
            id = random.getAsLong() & Long.MAX_VALUE;
        } while (id == 0 || findIncoming(recipient, id) != null);
        recipient.incoming.add(new ShareRequest(id, from, scope, day));
        if (idOut != null && idOut.length > 0) {
            idOut[0] = id;
        }
        state.changed();
        return Offer.SENT;
    }

    private static ShareRequest findIncoming(PlayerRecord r, long id) {
        for (ShareRequest q : r.incoming) {
            if (q.requestId() == id) {
                return q;
            }
        }
        return null;
    }

    /**
     * {@code recipient} accepts their own pending request {@code requestId}. Returns the request, or null when the
     * recipient has no such request (forged, expired, already answered, or the sharer was erased).
     */
    public ShareRequest accept(UUID recipient, long requestId) {
        PlayerRecord r = state.find(recipient).orElse(null);
        if (r == null) {
            return null;
        }
        ShareRequest q = findIncoming(r, requestId);
        if (q == null) {
            return null;
        }
        r.incoming.remove(q);
        // erasure removes a player's requests everywhere, so a request's sharer always still exists here
        PlayerRecord sharer = state.record(q.from());
        if (q.scope().kind() == ShareScope.Kind.WAYPOINT && sharer.waypoint(q.scope().waypointId()).isEmpty()) {
            state.changed();
            return null; // the thing offered no longer exists
        }
        sharer.grants.removeIf(g -> g.to().equals(recipient) && g.scope().equals(q.scope()));
        sharer.grants.add(new Grant(recipient, q.scope()));
        if (q.scope().kind() == ShareScope.Kind.WAYPOINT) {
            int id = q.scope().waypointId();
            for (int i = 0; i < sharer.waypoints.size(); i++) {
                if (sharer.waypoints.get(i).id() == id) {
                    sharer.waypoints.set(i, sharer.waypoints.get(i).withShared(true));
                }
            }
        }
        state.changed();
        return q;
    }

    /** {@code recipient} declines their own pending request. Returns it, or null when there was none. */
    public ShareRequest decline(UUID recipient, long requestId) {
        PlayerRecord r = state.find(recipient).orElse(null);
        if (r == null) {
            return null;
        }
        ShareRequest q = findIncoming(r, requestId);
        if (q == null) {
            return null;
        }
        r.incoming.remove(q);
        state.changed();
        return q;
    }

    /** One removed grant, with both parties, so the caller can notify them. */
    public record Revoked(UUID sharer, UUID recipient, ShareScope scope) { }

    /**
     * {@code actor} ends shares with {@code other} in either direction: grants {@code actor} gave {@code other}, and
     * grants {@code other} gave {@code actor}. {@code kind} null means every kind. Effective immediately: the grant is
     * gone before this returns.
     */
    public List<Revoked> revoke(UUID actor, UUID other, ShareScope.Kind kind) {
        List<Revoked> out = new ArrayList<>();
        removeGrants(actor, other, kind, out);
        removeGrants(other, actor, kind, out);
        // pending offers between the two in the same kinds are withdrawn too
        PlayerRecord a = state.find(actor).orElse(null);
        if (a != null) {
            a.incoming.removeIf(q -> q.from().equals(other) && (kind == null || q.scope().kind() == kind));
        }
        PlayerRecord o = state.find(other).orElse(null);
        if (o != null) {
            o.incoming.removeIf(q -> q.from().equals(actor) && (kind == null || q.scope().kind() == kind));
        }
        state.changed();
        return out;
    }

    /** Ends exactly one grant ({@code sharer} to {@code recipient} for {@code scope}). Null when there was none. */
    public Revoked revokeGrant(UUID sharer, UUID recipient, ShareScope scope) {
        PlayerRecord r = state.find(sharer).orElse(null);
        if (r == null || !r.grants.removeIf(g -> g.to().equals(recipient) && g.scope().equals(scope))) {
            return null;
        }
        if (scope.kind() == ShareScope.Kind.WAYPOINT) {
            int id = scope.waypointId();
            boolean stillShared = r.grants.stream().anyMatch(g -> g.scope().waypointId() == id);
            if (!stillShared) {
                for (int i = 0; i < r.waypoints.size(); i++) {
                    if (r.waypoints.get(i).id() == id) {
                        r.waypoints.set(i, r.waypoints.get(i).withShared(false));
                    }
                }
            }
        }
        state.changed();
        return new Revoked(sharer, recipient, scope);
    }

    private void removeGrants(UUID sharer, UUID recipient, ShareScope.Kind kind, List<Revoked> out) {
        PlayerRecord r = state.find(sharer).orElse(null);
        if (r == null) {
            return;
        }
        r.grants.removeIf(g -> {
            if (g.to().equals(recipient) && (kind == null || g.scope().kind() == kind)) {
                out.add(new Revoked(sharer, recipient, g.scope()));
                return true;
            }
            return false;
        });
    }

    /** Drops requests older than {@link ShareRequest#EXPIRE_DAYS}. Returns how many went. */
    public int purgeExpired(long today) {
        int[] n = {0};
        state.forEach((id, r) -> {
            int before = r.incoming.size();
            r.incoming.removeIf(q -> today - q.day() > ShareRequest.EXPIRE_DAYS);
            n[0] += before - r.incoming.size();
        });
        if (n[0] > 0) {
            state.changed();
        }
        return n[0];
    }

    /** True when {@code sharer} has an accepted grant of {@code kind} to {@code recipient}. */
    public boolean hasGrant(UUID sharer, UUID recipient, ShareScope.Kind kind) {
        for (Grant g : state.grantsOf(sharer)) {
            if (g.to().equals(recipient) && g.scope().kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** Every player who shares their explored map of {@code dimension} with {@code viewer}. */
    public List<UUID> mapSharersFor(UUID viewer, String dimension) {
        List<UUID> out = new ArrayList<>();
        state.forEach((id, r) -> {
            for (Grant g : r.grants) {
                if (g.to().equals(viewer) && g.scope().coversDimension(dimension)) {
                    out.add(id);
                    return;
                }
            }
        });
        return out;
    }

    /** Waypoints other players shared with {@code viewer}: (sharer, waypoint) pairs. */
    public List<SharedWaypoint> waypointsSharedWith(UUID viewer) {
        List<SharedWaypoint> out = new ArrayList<>();
        state.forEach((id, r) -> {
            for (Grant g : r.grants) {
                if (g.to().equals(viewer) && g.scope().kind() == ShareScope.Kind.WAYPOINT) {
                    r.waypoint(g.scope().waypointId()).ifPresent(w -> out.add(new SharedWaypoint(id, w)));
                }
            }
        });
        return out;
    }

    /** Players who share their live position with {@code viewer}. */
    public List<UUID> positionSharersFor(UUID viewer) {
        List<UUID> out = new ArrayList<>();
        state.forEach((id, r) -> {
            for (Grant g : r.grants) {
                if (g.to().equals(viewer) && g.scope().kind() == ShareScope.Kind.LIVE_POSITION) {
                    out.add(id);
                    return;
                }
            }
        });
        return out;
    }

    public record SharedWaypoint(UUID sharer, Waypoint waypoint) { }
}
