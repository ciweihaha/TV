package com.fongmi.android.tv.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Tracks VOD source ids that failed to play, with a time-to-live.
 *
 * <p>Previously a failed source id stayed blacklisted for the rest of the
 * playback session: one transient failure (weak network, a 5xx from the
 * source) permanently removed that source from the fallback list, even
 * though retrying it minutes later would often work. Entries now expire
 * after {@link #DEFAULT_TTL_MS} so a later manual source switch or a new
 * search can try the source again.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class FailedSourceTracker {

    public static final long DEFAULT_TTL_MS = 10 * 60 * 1000;

    private final Map<String, Long> failed;
    private final LongSupplier clock;
    private final long ttlMs;

    public FailedSourceTracker() {
        this(System::currentTimeMillis, DEFAULT_TTL_MS);
    }

    FailedSourceTracker(LongSupplier clock, long ttlMs) {
        this.failed = new HashMap<>();
        this.clock = clock;
        this.ttlMs = ttlMs;
    }

    public synchronized void add(String id) {
        if (id != null && !id.isEmpty()) failed.put(id, clock.getAsLong());
    }

    public synchronized boolean has(String id) {
        if (id == null || id.isEmpty()) return false;
        purgeExpired(clock.getAsLong());
        return failed.containsKey(id);
    }

    public synchronized void clear() {
        failed.clear();
    }

    synchronized int size() {
        purgeExpired(clock.getAsLong());
        return failed.size();
    }

    private void purgeExpired(long now) {
        failed.values().removeIf(failedAt -> now - failedAt >= ttlMs);
    }
}
