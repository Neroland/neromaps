package za.co.neroland.neromaps.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * A per-player token bucket for one kind of serverbound action. Memory only, keyed by UUID for the session and
 * dropped on logout; never persisted or logged.
 */
public final class RateLimiter {

    private final int capacity;
    private final long refillNanos;
    private final LongSupplier clock;
    private final Map<UUID, long[]> buckets = new HashMap<>();

    /** {@code capacity} actions, refilling one every {@code refillMillis}. */
    public RateLimiter(int capacity, long refillMillis, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillNanos = Math.max(1, refillMillis) * 1_000_000L;
        this.clock = nanoClock;
    }

    public RateLimiter(int capacity, long refillMillis) {
        this(capacity, refillMillis, System::nanoTime);
    }

    /** Takes one token; false when the player is over the limit (the action must be dropped). */
    public boolean tryAcquire(UUID player) {
        long now = clock.getAsLong();
        long[] b = buckets.computeIfAbsent(player, k -> new long[] {capacity, now});
        long elapsed = now - b[1];
        if (elapsed >= refillNanos) {
            long add = elapsed / refillNanos;
            b[0] = Math.min(capacity, b[0] + add);
            b[1] += add * refillNanos;
        }
        if (b[0] <= 0) {
            return false;
        }
        b[0]--;
        return true;
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }

    public void forgetAll() {
        buckets.clear();
    }

    public int tracked() {
        return buckets.size();
    }
}
