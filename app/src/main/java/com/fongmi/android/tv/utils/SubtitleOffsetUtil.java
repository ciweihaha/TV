package com.fongmi.android.tv.utils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.text.SubtitleOffsets;

public final class SubtitleOffsetUtil {

    public static final long MAX_OFFSET_MS = 300_000;

    private SubtitleOffsetUtil() {
    }

    @Nullable
    public static SubtitleOffsets shift(SubtitleOffsets previous, long deltaMs, boolean primary, boolean secondary) {
        long main = primary ? shift(previous.primaryMs, deltaMs) : previous.primaryMs;
        long sub = secondary ? shift(previous.secondaryMs, deltaMs) : previous.secondaryMs;
        // Only a channel that is actually being shifted can fail. Previously
        // an unset primary offset (TIME_UNSET, e.g. no primary subtitle
        // selected) made every secondary-only adjustment fail with the
        // "limit" toast, even though the secondary value itself was fine.
        if (primary && main == C.TIME_UNSET) return null;
        if (secondary && sub == C.TIME_UNSET) return null;
        return new SubtitleOffsets(main, sub);
    }

    public static long shift(long previousMs, long deltaMs) {
        try {
            // An unset offset has no base value; shift from 0 so a first
            // adjustment on that channel works instead of overflowing.
            long base = previousMs == C.TIME_UNSET ? 0 : previousMs;
            long result = Math.addExact(base, deltaMs);
            return result < -MAX_OFFSET_MS || result > MAX_OFFSET_MS ? C.TIME_UNSET : result;
        } catch (ArithmeticException e) {
            return C.TIME_UNSET;
        }
    }
}
