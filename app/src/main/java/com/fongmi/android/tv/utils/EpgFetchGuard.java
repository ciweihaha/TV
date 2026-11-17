package com.fongmi.android.tv.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Negative cache for EPG day fetches.
 *
 * <p>{@code LiveApi.getEpg} fetches the EPG for yesterday, today and tomorrow
 * on every call. When the EPG server is down, every channel zap re-issued
 * the same failing HTTP requests (and on the media-browse path the failure
 * used to propagate to the caller). A failed URL+date is now remembered for
 * {@link #FAILURE_TTL_MS} so rapid zapping does not hammer a dead server.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class EpgFetchGuard {

    public static final long FAILURE_TTL_MS = 5 * 60 * 1000;

    private final ConcurrentMap<String, Long> failures;
    private final LongSupplier clock;
    private final long ttlMs;

    public EpgFetchGuard() {
        this(System::currentTimeMillis, FAILURE_TTL_MS);
    }

    EpgFetchGuard(LongSupplier clock, long ttlMs) {
        this.failures = new ConcurrentHashMap<>();
        this.clock = clock;
        this.ttlMs = ttlMs;
    }

    public boolean shouldFetch(String key) {
        Long failedAt = key == null ? null : failures.get(key);
        return failedAt == null || clock.getAsLong() - failedAt >= ttlMs;
    }

    public void recordFailure(String key) {
        if (key != null) failures.put(key, clock.getAsLong());
    }

    public void recordSuccess(String key) {
        if (key != null) failures.remove(key);
    }
}
