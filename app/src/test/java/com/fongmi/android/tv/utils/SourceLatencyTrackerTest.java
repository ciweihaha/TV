package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourceLatencyTrackerTest {

    @Test
    public void unknownSiteSortsLast() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        assertEquals(SourceLatencyTracker.UNKNOWN_MS, tracker.latencyMs("never-seen"));
        assertEquals(SourceLatencyTracker.UNKNOWN_MS, tracker.latencyMs(null));
        assertEquals(SourceLatencyTracker.UNKNOWN_MS, tracker.latencyMs(""));
        assertFalse(tracker.has("never-seen"));
    }

    @Test
    public void firstSampleIsUsedAsIs() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("fast", 120);
        assertEquals(120, tracker.latencyMs("fast"));
        assertTrue(tracker.has("fast"));
    }

    @Test
    public void emaMovesTowardNewSamples() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("site", 100);
        tracker.record("site", 1000);
        long afterSlow = tracker.latencyMs("site");
        assertTrue("EMA should rise after a slow sample: " + afterSlow, afterSlow > 100 && afterSlow < 1000);
        tracker.record("site", 100);
        long afterFast = tracker.latencyMs("site");
        assertTrue("EMA should fall after a fast sample: " + afterFast, afterFast < afterSlow);
    }

    @Test
    public void invalidSamplesAreIgnored() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record(null, 100);
        tracker.record("", 100);
        tracker.record("site", -5);
        assertEquals(0, tracker.size());
        tracker.record("site", 0);
        assertEquals(1, tracker.latencyMs("site"));
    }

    @Test
    public void entriesAreBoundedAndOldestEvicted() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        for (int i = 0; i < SourceLatencyTracker.MAX_ENTRIES + 50; i++) tracker.record("site-" + i, 100);
        assertEquals(SourceLatencyTracker.MAX_ENTRIES, tracker.size());
        assertFalse("oldest site should have been evicted", tracker.has("site-0"));
        assertTrue(tracker.has("site-" + (SourceLatencyTracker.MAX_ENTRIES + 49)));
    }

    @Test
    public void concurrentRecordingStaysConsistent() throws Exception {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        int threads = 8;
        int perThread = 5000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            pool.execute(() -> {
                for (int i = 0; i < perThread; i++) tracker.record("site-" + (thread * 10 + i % 10), 50 + i % 200);
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        assertTrue(tracker.size() <= SourceLatencyTracker.MAX_ENTRIES);
        assertTrue(tracker.size() > 0);
        for (String key : tracker.keysInLruOrder()) {
            long value = tracker.latencyMs(key);
            assertTrue(value >= 1 && value < SourceLatencyTracker.UNKNOWN_MS);
        }
    }

    @Test
    public void latencyOrderingPrefersFasterSite() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("slow", 3000);
        tracker.record("fast", 150);
        tracker.record("mid", 800);
        List<String> sites = new ArrayList<>(List.of("slow", "mid", "fast", "unknown"));
        sites.sort((a, b) -> Long.compare(tracker.latencyMs(a), tracker.latencyMs(b)));
        assertEquals(List.of("fast", "mid", "slow", "unknown"), sites);
    }
}
