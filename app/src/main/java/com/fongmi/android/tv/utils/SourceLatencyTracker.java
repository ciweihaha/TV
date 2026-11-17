package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-site response-latency tracker, used to order VOD source fallback.
 *
 * <p>Search fans out to every searchable site and the fallback list used to
 * be ordered by arrival time, so a slow site that happened to answer first
 * was tried before a fast one. Each site now keeps an exponentially weighted
 * moving average of its search/detail round-trip time; the fallback list
 * sorts by match quality first and by latency within the same tier, so the
 * fastest source is tried first.
 *
 * <p>Only successful round trips are recorded: a timeout or failure is not
 * a latency sample (failures are tracked separately by
 * {@link FailedSourceTracker}). A site with no sample yet sorts after every
 * measured site, so unknown sites are still tried, just last.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class SourceLatencyTracker {

    /** Weight of the newest sample in the moving average. */
    static final double ALPHA = 0.3;
    /** Latency reported for a site with no sample: sorts after all measured sites. */
    public static final long UNKNOWN_MS = Long.MAX_VALUE;
    /** At most this many sites are remembered; the least recently used entry is evicted. */
    static final int MAX_ENTRIES = 256;

    private static final SourceLatencyTracker INSTANCE = new SourceLatencyTracker();

    public static SourceLatencyTracker get() {
        return INSTANCE;
    }

    private final Map<String, Long> latencyMs;

    public SourceLatencyTracker() {
        this.latencyMs = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    /** Records one successful round trip for {@code siteKey} in milliseconds. */
    public synchronized void record(String siteKey, long elapsedMs) {
        if (siteKey == null || siteKey.isEmpty() || elapsedMs < 0) return;
        long sample = Math.max(1, elapsedMs);
        Long previous = latencyMs.get(siteKey);
        latencyMs.put(siteKey, previous == null ? sample : Math.round(previous * (1 - ALPHA) + sample * ALPHA));
    }

    /** Moving-average latency for {@code siteKey}, or {@link #UNKNOWN_MS} when never measured. */
    public synchronized long latencyMs(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) return UNKNOWN_MS;
        Long value = latencyMs.get(siteKey);
        return value == null ? UNKNOWN_MS : value;
    }

    /** Whether any sample exists for {@code siteKey}. */
    public synchronized boolean has(String siteKey) {
        return siteKey != null && !siteKey.isEmpty() && latencyMs.containsKey(siteKey);
    }

    /** Number of tracked sites (bounded by {@link #MAX_ENTRIES}). */
    public synchronized int size() {
        return latencyMs.size();
    }

    public synchronized void clear() {
        latencyMs.clear();
    }

    synchronized List<String> keysInLruOrder() {
        return new ArrayList<>(latencyMs.keySet());
    }
}
