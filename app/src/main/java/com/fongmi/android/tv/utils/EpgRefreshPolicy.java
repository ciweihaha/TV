package com.fongmi.android.tv.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Decides when the live EPG (XMLTV) must be re-downloaded.
 *
 * <p>Root cause of the stale-guide bug (2026-11-19): the XMLTV file was
 * downloaded once when the live source was first loaded and never again
 * for the lifetime of the process. The server file is regenerated daily,
 * so after midnight (or after the server file was replaced) the guide
 * still showed the previous day's programmes — e.g. stuck at 20:04.
 *
 * <p>Refresh is due when any of these holds:
 * <ul>
 *   <li>never refreshed (and never attempted, or the last attempt is old);</li>
 *   <li>at least {@link #REFRESH_INTERVAL_MS} (1 hour) since the last
 *       successful refresh;</li>
 *   <li>the calendar day changed since the last successful refresh
 *       (the server file covers one day only);</li>
 *   <li>there is no programme covering now (stale/empty guide), throttled
 *       to at most one attempt per {@link #STALE_RETRY_MS} (10 minutes)
 *       so a dead server is not hammered.</li>
 * </ul>
 *
 * <p>A recent attempt (in-flight initial load or a failure minutes ago)
 * suppresses a duplicate refresh.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class EpgRefreshPolicy {

    /** Regular re-download interval: the server file is regenerated daily, hourly is plenty. */
    public static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);

    /** Minimum gap between two refresh attempts triggered by a stale/empty guide. */
    public static final long STALE_RETRY_MS = TimeUnit.MINUTES.toMillis(10);

    private EpgRefreshPolicy() {
    }

    public static boolean shouldRefresh(long lastRefreshMs, long lastAttemptMs, long nowMs, boolean hasCurrentProgram, boolean dayChanged) {
        // A recent attempt (initial load in flight, or a failure minutes ago)
        // suppresses every trigger: never start a second download on top.
        if (nowMs - lastAttemptMs < STALE_RETRY_MS) return false;
        if (dayChanged) return true;
        if (lastRefreshMs > 0 && nowMs - lastRefreshMs >= REFRESH_INTERVAL_MS) return true;
        if (!hasCurrentProgram) return true;
        // Never refreshed successfully: retry once the last attempt is old
        // (the recent-attempt throttle above already suppressed fresh
        // attempts), even when another path supplied a current programme.
        return lastRefreshMs <= 0;
    }

    public static boolean isDayChanged(long lastRefreshMs, long nowMs, ZoneId zoneId) {
        if (lastRefreshMs <= 0) return false;
        return !LocalDate.ofInstant(Instant.ofEpochMilli(lastRefreshMs), zoneId).equals(LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zoneId));
    }
}
