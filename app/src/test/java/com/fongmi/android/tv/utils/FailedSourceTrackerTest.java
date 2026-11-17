package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

public class FailedSourceTrackerTest {

    @Test
    public void failedIdIsRememberedThenExpires() {
        AtomicLong clock = new AtomicLong(1_000);
        FailedSourceTracker tracker = new FailedSourceTracker(clock::get, 60_000);
        tracker.add("vod-1");
        assertTrue(tracker.has("vod-1"));
        clock.set(1_000 + 59_999);
        assertTrue(tracker.has("vod-1"));
        clock.set(1_000 + 60_000);
        assertFalse("entry must expire at the TTL boundary", tracker.has("vod-1"));
    }

    @Test
    public void reAddingRefreshesTtl() {
        AtomicLong clock = new AtomicLong(0);
        FailedSourceTracker tracker = new FailedSourceTracker(clock::get, 1_000);
        tracker.add("vod-1");
        clock.set(900);
        tracker.add("vod-1");
        clock.set(1_800);
        assertTrue(tracker.has("vod-1"));
        clock.set(1_900);
        assertFalse(tracker.has("vod-1"));
    }

    @Test
    public void nullAndEmptyIdsIgnored() {
        FailedSourceTracker tracker = new FailedSourceTracker();
        tracker.add(null);
        tracker.add("");
        assertFalse(tracker.has(null));
        assertFalse(tracker.has(""));
        assertEquals(0, tracker.size());
    }

    @Test
    public void clearDropsAll() {
        FailedSourceTracker tracker = new FailedSourceTracker();
        tracker.add("a");
        tracker.add("b");
        tracker.clear();
        assertFalse(tracker.has("a"));
        assertEquals(0, tracker.size());
    }

    @Test
    public void stressTenThousandIds() {
        FailedSourceTracker tracker = new FailedSourceTracker();
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) tracker.add("vod-" + i);
        for (int i = 0; i < 10_000; i++) assertTrue(tracker.has("vod-" + i));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(10_000, tracker.size());
        assertTrue("10k add+lookup took " + elapsedMs + "ms", elapsedMs < 2000);
    }
}
