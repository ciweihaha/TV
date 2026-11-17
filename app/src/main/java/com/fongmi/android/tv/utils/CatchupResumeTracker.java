package com.fongmi.android.tv.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Remembers where the user left off in a live catchup (replay) program,
 * within the current session only.
 *
 * <p>Previously re-selecting a replay program always restarted it from the
 * beginning, even if the user had watched half of it, zapped away, and came
 * back. Positions are now remembered per program (channel + program start
 * + title) so coming back resumes where playback stopped.
 *
 * <p>Two guards keep resumes sensible: positions under
 * {@link #MIN_RESUME_MS} are not worth resuming, and positions within
 * {@link #MIN_REMAINING_MS} of the program end are discarded (the program
 * is effectively finished; resuming there would just end playback again).
 * Nothing is persisted to disk: zapping away for the night and coming back
 * tomorrow starts fresh, which is the right default for live TV.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class CatchupResumeTracker {

    /** Only resume when at least this much of the program was watched. */
    public static final long MIN_RESUME_MS = 30_000;
    /** Discard a position this close to the program end. */
    public static final long MIN_REMAINING_MS = 60_000;
    /** At most this many programs are remembered; the least recently used is evicted. */
    static final int MAX_ENTRIES = 64;

    private final Map<String, Long> positions;

    public CatchupResumeTracker() {
        this.positions = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    /** Builds the per-program key. {@code programStartMs} is the EPG start time in epoch ms; when it is 0 (times not parsed yet) the raw start text is used instead. */
    public static String key(String channelName, long programStartMs, String programStart, String title) {
        String start = programStartMs > 0 ? String.valueOf(programStartMs) : Objects.toString(programStart, "");
        return Objects.toString(channelName, "") + "|" + start + "|" + Objects.toString(title, "");
    }

    /**
     * Remembers {@code positionMs} for {@code key}.
     *
     * @param programDurationMs length of the program in ms, or &lt;= 0 when unknown; when known, positions near the end are discarded.
     */
    public synchronized void remember(String key, long positionMs, long programDurationMs) {
        if (key == null || key.isEmpty()) return;
        if (positionMs < MIN_RESUME_MS) return;
        if (programDurationMs > 0 && positionMs > programDurationMs - MIN_REMAINING_MS) return;
        positions.put(key, positionMs);
    }

    /** Stored resume position in ms, or -1 when this program was never left mid-way. */
    public synchronized long resumeMs(String key) {
        if (key == null || key.isEmpty()) return -1;
        Long value = positions.get(key);
        return value == null ? -1 : value;
    }

    public synchronized void forget(String key) {
        if (key != null) positions.remove(key);
    }

    public synchronized int size() {
        return positions.size();
    }

    public synchronized void clear() {
        positions.clear();
    }
}
