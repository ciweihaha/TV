package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

public class EpgFetchGuardTest {

    @Test
    public void failedFetchIsSkippedUntilTtlExpires() {
        AtomicLong clock = new AtomicLong(0);
        EpgFetchGuard guard = new EpgFetchGuard(clock::get, 300_000);
        String key = "http://epg.example/x.xml|2026-11-18";
        assertTrue(guard.shouldFetch(key));
        guard.recordFailure(key);
        assertFalse(guard.shouldFetch(key));
        clock.set(299_999);
        assertFalse(guard.shouldFetch(key));
        clock.set(300_000);
        assertTrue(guard.shouldFetch(key));
    }

    @Test
    public void successClearsFailure() {
        EpgFetchGuard guard = new EpgFetchGuard();
        String key = "http://epg.example/x.xml|2026-11-18";
        guard.recordFailure(key);
        assertFalse(guard.shouldFetch(key));
        guard.recordSuccess(key);
        assertTrue(guard.shouldFetch(key));
    }

    @Test
    public void differentDaysAreIndependent() {
        EpgFetchGuard guard = new EpgFetchGuard();
        guard.recordFailure("http://epg.example/x.xml|2026-11-17");
        assertTrue(guard.shouldFetch("http://epg.example/x.xml|2026-11-18"));
    }

    @Test
    public void stressRapidZapping() {
        EpgFetchGuard guard = new EpgFetchGuard();
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            String key = "http://epg.example/" + (i % 500) + ".xml|2026-11-18";
            if (i % 3 == 0) guard.recordFailure(key);
            guard.shouldFetch(key);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("100k guard checks took " + elapsedMs + "ms", elapsedMs < 2000);
    }
}
