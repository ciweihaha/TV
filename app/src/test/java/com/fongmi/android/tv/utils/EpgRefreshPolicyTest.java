package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

public class EpgRefreshPolicyTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    /** 2026-11-24 08:00 UTC. */
    private static final long NOW = 1_795_507_200_000L;
    private static final long HOUR = EpgRefreshPolicy.REFRESH_INTERVAL_MS;
    private static final long STALE_RETRY = EpgRefreshPolicy.STALE_RETRY_MS;

    @Test
    public void neverAttemptedRefreshes() {
        assertTrue(EpgRefreshPolicy.shouldRefresh(0, 0, NOW, false, false));
        assertTrue(EpgRefreshPolicy.shouldRefresh(0, 0, NOW, true, false));
    }

    @Test
    public void failedFirstRefreshRetriesAfterThrottle() {
        // First download failed (attempt recorded, never refreshed): once the
        // attempt is older than the throttle, retry even if another path
        // supplied a current programme — otherwise the XML guide would stay
        // unloaded for the rest of the process lifetime.
        assertTrue(EpgRefreshPolicy.shouldRefresh(0, NOW - STALE_RETRY - 1, NOW, true, false));
        assertTrue(EpgRefreshPolicy.shouldRefresh(0, NOW - STALE_RETRY - 1, NOW, false, false));
    }

    @Test
    public void recentAttemptSuppressesDuplicateRefresh() {
        // Initial download in flight / just attempted: do not start another one.
        assertFalse(EpgRefreshPolicy.shouldRefresh(0, NOW - 60_000, NOW, false, false));
        assertFalse(EpgRefreshPolicy.shouldRefresh(0, NOW - 60_000, NOW, true, false));
    }

    @Test
    public void hourlyRefresh() {
        long refreshed = NOW - HOUR;
        assertTrue(EpgRefreshPolicy.shouldRefresh(refreshed, refreshed, NOW, true, false));
        long justRefreshed = NOW - 30 * 60_000;
        assertFalse(EpgRefreshPolicy.shouldRefresh(justRefreshed, justRefreshed, NOW, true, false));
    }

    @Test
    public void staleGuideRefreshesButThrottled() {
        long refreshed = NOW - 30 * 60_000;
        // No programme covering now, last attempt long ago: refresh.
        assertTrue(EpgRefreshPolicy.shouldRefresh(refreshed, NOW - STALE_RETRY - 1, NOW, false, false));
        // Same, but attempted 1 minute ago (server may be dead): wait.
        assertFalse(EpgRefreshPolicy.shouldRefresh(refreshed, NOW - 60_000, NOW, false, false));
        // Current programme exists: no stale refresh.
        assertFalse(EpgRefreshPolicy.shouldRefresh(refreshed, NOW - STALE_RETRY - 1, NOW, true, false));
    }

    @Test
    public void dayChangedRefreshes() {
        // Refreshed 30 minutes ago but yesterday: the daily server file rolled over.
        long yesterday = NOW - 30 * 60_000 - 8 * 60 * 60_000;
        assertTrue(EpgRefreshPolicy.isDayChanged(yesterday, NOW, UTC));
        assertTrue(EpgRefreshPolicy.shouldRefresh(yesterday, NOW - STALE_RETRY - 1, NOW, true, true));
        // Same day: no day-change refresh.
        assertFalse(EpgRefreshPolicy.isDayChanged(NOW - 30 * 60_000, NOW, UTC));
        assertFalse(EpgRefreshPolicy.shouldRefresh(NOW - 30 * 60_000, NOW - STALE_RETRY - 1, NOW, true, false));
    }

    @Test
    public void dayChangeStillThrottlesRecentAttempts() {
        long yesterday = NOW - 30 * 60_000 - 8 * 60 * 60_000;
        assertFalse(EpgRefreshPolicy.shouldRefresh(yesterday, NOW - 60_000, NOW, true, true));
    }
}
