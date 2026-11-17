package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.text.SubtitleOffsets;

import org.junit.Test;

public class SubtitleOffsetUtilTest {

    @Test
    public void shiftPrimaryOnly() {
        SubtitleOffsets next = SubtitleOffsetUtil.shift(new SubtitleOffsets(1000, 2000), 500, true, false);
        assertEquals(1500, next.primaryMs);
        assertEquals(2000, next.secondaryMs);
    }

    @Test
    public void shiftSecondaryOnlyWithUnsetPrimary() {
        // Regression: an unset primary offset used to reject a valid
        // secondary-only adjustment with the "limit" toast.
        SubtitleOffsets previous = new SubtitleOffsets(C.TIME_UNSET, 2000);
        SubtitleOffsets next = SubtitleOffsetUtil.shift(previous, 500, false, true);
        assertTrue("secondary-only shift must succeed when primary is unset", next != null);
        assertEquals(C.TIME_UNSET, next.primaryMs);
        assertEquals(2500, next.secondaryMs);
    }

    @Test
    public void shiftUnsetChannelFromZero() {
        SubtitleOffsets next = SubtitleOffsetUtil.shift(new SubtitleOffsets(C.TIME_UNSET, C.TIME_UNSET), 500, true, false);
        assertTrue(next != null);
        assertEquals(500, next.primaryMs);
        assertEquals(C.TIME_UNSET, next.secondaryMs);
    }

    @Test
    public void shiftBeyondLimitReturnsNull() {
        SubtitleOffsets next = SubtitleOffsetUtil.shift(new SubtitleOffsets(299_000, 0), 5_000, true, false);
        assertNull(next);
    }

    @Test
    public void shiftLinkedKeepsBoth() {
        SubtitleOffsets next = SubtitleOffsetUtil.shift(new SubtitleOffsets(1000, 1000), -500, true, true);
        assertEquals(500, next.primaryMs);
        assertEquals(500, next.secondaryMs);
    }

    @Test
    public void shiftLongOverflowIsUnset() {
        assertEquals(C.TIME_UNSET, SubtitleOffsetUtil.shift(Long.MAX_VALUE, 1));
    }
}
