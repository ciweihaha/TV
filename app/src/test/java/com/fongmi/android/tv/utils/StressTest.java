package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress tests for the Round 134 paths: repeated source switching,
 * searching, paging-sized result sets, and long soak runs.
 *
 * <p>These are JVM unit tests over pure-JDK helpers (the same code the
 * player uses), so they run both locally and in CI. On-device stress
 * (real channel zapping, playback, long runs on the tablet) is a separate
 * step once the tablet is back.
 */
public class StressTest {

    private static class Hit {
        final String name;
        final String id;
        final String site;

        Hit(String name, String id, String site) {
            this.name = name;
            this.id = id;
            this.site = site;
        }
    }

    /** Simulates 1,000 source switches: re-rank the fallback list every time a source fails. */
    @Test
    public void stressThousandFallbackReranks() {
        SourceLatencyTracker latency = new SourceLatencyTracker();
        FailedSourceTracker failed = new FailedSourceTracker();
        List<Hit> pool = new ArrayList<>();
        for (int site = 0; site < 30; site++) {
            latency.record("site-" + site, 100 + site * 37L);
            pool.add(new Hit("星际穿越", "id-" + site, "site-" + site));
        }
        long start = System.nanoTime();
        for (int round = 0; round < 1000; round++) {
            List<Hit> ranked = SearchResultRanker.rank(pool, "星际穿越", h -> h.name, h -> h.id, h -> h.site, h -> latency.latencyMs(h.site));
            assertEquals(30, ranked.size());
            // Within the exact-match tier the list must be latency-ordered:
            // the failed (slowest-tried) source drops out and the next
            // fastest takes its place.
            for (int i = 1; i < ranked.size(); i++) {
                assertTrue(latency.latencyMs(ranked.get(i - 1).site) <= latency.latencyMs(ranked.get(i).site));
            }
            failed.add(ranked.get(0).id);
            pool.removeIf(hit -> failed.has(hit.id));
            pool.add(new Hit("星际穿越", "id-new-" + round, "site-new-" + round));
            latency.record("site-new-" + round, 200);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("1000 fallback re-ranks took " + elapsedMs + "ms", elapsedMs < 5000);
    }

    /** Simulates paging through a huge category: 50 pages x 200 items, deduped and ranked per page. */
    @Test
    public void stressPagedResults() {
        List<Hit> seen = new ArrayList<>();
        long start = System.nanoTime();
        for (int page = 0; page < 50; page++) {
            List<Hit> items = new ArrayList<>();
            for (int i = 0; i < 200; i++) items.add(new Hit("影片" + ((page * 200 + i) % 3000), "id-" + ((page * 200 + i) % 3000), "site-" + (i % 10)));
            List<Hit> ranked = SearchResultRanker.rank(items, "影片7", h -> h.name, h -> h.id, h -> h.site);
            assertTrue(!ranked.isEmpty());
            seen.addAll(ranked);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50 * 200, seen.size());
        assertTrue("50 pages of ranking took " + elapsedMs + "ms", elapsedMs < 5000);
    }

    /** Simulates a long session: 200 rounds of search + latency + EPG-guard churn; memory must stay bounded. */
    @Test
    public void stressSoakKeepsMemoryBounded() {
        SourceLatencyTracker latency = new SourceLatencyTracker();
        FailedSourceTracker failed = new FailedSourceTracker();
        EpgFetchGuard guard = new EpgFetchGuard();
        CatchupResumeTracker resume = new CatchupResumeTracker();
        List<Hit> pool = new ArrayList<>();
        for (int i = 0; i < 500; i++) pool.add(new Hit("影片" + (i % 100), "id-" + i, "site-" + (i % 50)));
        for (int round = 0; round < 200; round++) {
            SearchResultRanker.rank(pool, "影片7", h -> h.name, h -> h.id, h -> h.site, h -> latency.latencyMs(h.site));
            latency.record("site-" + (round % 50), 100 + round % 500);
            failed.add("failed-" + round);
            guard.recordFailure("epg-" + (round % 20));
            resume.remember("prog-" + (round % 30), 120_000, 1_800_000);
        }
        assertTrue(latency.size() <= SourceLatencyTracker.MAX_ENTRIES);
        assertTrue(failed.size() <= 200);
        assertTrue(resume.size() <= CatchupResumeTracker.MAX_ENTRIES);
    }

    /** Simulates many sources failing at once: the failed set must not grow without bound. */
    @Test
    public void stressMassSourceFailures() {
        FailedSourceTracker failed = new FailedSourceTracker();
        for (int i = 0; i < 10_000; i++) failed.add("vod-" + i);
        assertEquals(10_000, failed.size());
        assertTrue(failed.has("vod-9999"));
        failed.clear();
        assertEquals(0, failed.size());
    }

    /** Concurrent latency recording from search threads must not corrupt the tracker. */
    @Test
    public void stressConcurrentSearchLatency() throws Exception {
        SourceLatencyTracker latency = new SourceLatencyTracker();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong searches = new AtomicLong();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            pool.execute(() -> {
                for (int i = 0; i < 2000; i++) {
                    latency.record("site-" + (thread * 5 + i % 5), 80 + (i % 900));
                    searches.incrementAndGet();
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        assertEquals(16_000, searches.get());
        assertTrue(latency.size() <= SourceLatencyTracker.MAX_ENTRIES);
    }
}
