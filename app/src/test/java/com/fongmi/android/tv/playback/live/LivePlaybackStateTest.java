package com.fongmi.android.tv.playback.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Channel;

import org.junit.Test;

public class LivePlaybackStateTest {

    @Test
    public void recordsPreviousChannelOnSwitch() {
        LivePlaybackState state = new LivePlaybackState();
        Channel cctv1 = new Channel("CCTV-1");
        Channel cctv2 = new Channel("CCTV-2");
        state.setChannel(cctv1);
        assertNull(state.getPreviousChannel());
        state.setChannel(cctv2);
        assertEquals(cctv1, state.getPreviousChannel());
        assertEquals(cctv2, state.getChannel());
    }

    @Test
    public void reselectingSameChannelKeepsPrevious() {
        LivePlaybackState state = new LivePlaybackState();
        Channel a = new Channel("A");
        Channel b = new Channel("B");
        state.setChannel(a);
        state.setChannel(b);
        state.setChannel(b);
        assertEquals(a, state.getPreviousChannel());
    }

    @Test
    public void switchingBackSwapsPrevious() {
        LivePlaybackState state = new LivePlaybackState();
        Channel a = new Channel("A");
        Channel b = new Channel("B");
        state.setChannel(a);
        state.setChannel(b);
        state.setChannel(state.getPreviousChannel());
        assertEquals(a, state.getChannel());
        assertEquals(b, state.getPreviousChannel());
    }

    @Test
    public void resetClearsPrevious() {
        LivePlaybackState state = new LivePlaybackState();
        state.setChannel(new Channel("A"));
        state.setChannel(new Channel("B"));
        assertTrue(state.getPreviousChannel() != null);
        state.reset();
        assertNull(state.getPreviousChannel());
        assertNull(state.getChannel());
    }

    @Test
    public void stressRapidChannelSwitching() {
        LivePlaybackState state = new LivePlaybackState();
        Channel previous = null;
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            Channel next = new Channel("CH-" + i);
            state.setChannel(next);
            if (i > 0) assertEquals(previous, state.getPreviousChannel());
            previous = next;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertFalse("10k channel switches took " + elapsedMs + "ms", elapsedMs > 2000);
    }
}
