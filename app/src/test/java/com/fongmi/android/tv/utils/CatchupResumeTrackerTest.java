package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CatchupResumeTrackerTest {

    private static final String KEY = CatchupResumeTracker.key("CCTV-1", 1_700_000_000_000L, "20:00:00", "新闻联播");

    @Test
    public void unknownProgramHasNoResume() {
        assertEquals(-1, new CatchupResumeTracker().resumeMs(KEY));
        assertEquals(-1, new CatchupResumeTracker().resumeMs(null));
        assertEquals(-1, new CatchupResumeTracker().resumeMs(""));
    }

    @Test
    public void remembersMidProgramPosition() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        tracker.remember(KEY, 600_000, 1_800_000);
        assertEquals(600_000, tracker.resumeMs(KEY));
    }

    @Test
    public void ignoresTooShortPositions() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        tracker.remember(KEY, 29_999, 1_800_000);
        assertEquals(-1, tracker.resumeMs(KEY));
        tracker.remember(KEY, 30_000, 1_800_000);
        assertEquals(30_000, tracker.resumeMs(KEY));
    }

    @Test
    public void ignoresPositionsNearProgramEnd() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        // 1,800,000 ms program; 60,000 ms from the end is the cutoff.
        tracker.remember(KEY, 1_740_001, 1_800_000);
        assertEquals(-1, tracker.resumeMs(KEY));
        tracker.remember(KEY, 1_740_000, 1_800_000);
        assertEquals(1_740_000, tracker.resumeMs(KEY));
    }

    @Test
    public void unknownDurationOnlyNeedsMinimum() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        tracker.remember(KEY, 900_000, 0);
        assertEquals(900_000, tracker.resumeMs(KEY));
        tracker.remember(KEY, 900_000, -1);
        assertEquals(900_000, tracker.resumeMs(KEY));
    }

    @Test
    public void latestPositionWins() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        tracker.remember(KEY, 100_000, 1_800_000);
        tracker.remember(KEY, 500_000, 1_800_000);
        assertEquals(500_000, tracker.resumeMs(KEY));
    }

    @Test
    public void keyDistinguishesPrograms() {
        String other = CatchupResumeTracker.key("CCTV-1", 1_700_000_000_000L, "20:00:00", "天气预报");
        assertTrue(!KEY.equals(other));
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        tracker.remember(KEY, 100_000, 1_800_000);
        assertEquals(-1, tracker.resumeMs(other));
    }

    @Test
    public void keyFallsBackToStartTextWithoutEpoch() {
        String a = CatchupResumeTracker.key("CCTV-1", 0, "20:00:00", "新闻联播");
        String b = CatchupResumeTracker.key("CCTV-1", 0, "21:00:00", "新闻联播");
        assertTrue(!a.equals(b));
        assertEquals("CCTV-1|20:00:00|新闻联播", a);
    }

    @Test
    public void entriesAreBounded() {
        CatchupResumeTracker tracker = new CatchupResumeTracker();
        for (int i = 0; i < CatchupResumeTracker.MAX_ENTRIES + 20; i++) {
            tracker.remember("key-" + i, 100_000, 1_800_000);
        }
        assertEquals(CatchupResumeTracker.MAX_ENTRIES, tracker.size());
        assertEquals(-1, tracker.resumeMs("key-0"));
        assertEquals(100_000, tracker.resumeMs("key-" + (CatchupResumeTracker.MAX_ENTRIES + 19)));
    }
}
