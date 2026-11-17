package com.fongmi.android.tv.playback.vod;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import java.util.List;

public class VodPlaybackStateTest {

    private static VodPlaybackState stateWithEpisodes(int count) {
        VodPlaybackState state = new VodPlaybackState();
        Flag flag = new Flag("线路1");
        for (int i = 0; i < count; i++) {
            flag.getEpisodes().add(Episode.create("第" + (i + 1) + "集", "url-" + i));
        }
        state.setFlags(List.of(flag));
        return state;
    }

    @Test
    public void episodeAccessorsReturnNullWithNoFlags() {
        // Regression: getEpisode()/getRelativeEpisode() used to index into
        // an empty flags list (IndexOutOfBoundsException / Math.clamp
        // IllegalArgumentException) when detail returned no flags.
        VodPlaybackState state = new VodPlaybackState();
        assertNull(state.getEpisode());
        assertNull(state.getRelativeEpisode(1));
        assertNull(state.getRelativeEpisode(-1));
    }

    @Test
    public void episodeAccessorsReturnNullWithEmptyEpisodes() {
        VodPlaybackState state = new VodPlaybackState();
        state.setFlags(List.of(new Flag("线路1")));
        assertNull(state.getEpisode());
        assertNull(state.getRelativeEpisode(1));
    }

    @Test
    public void episodeAccessorsReturnCurrentAndRelative() {
        VodPlaybackState state = stateWithEpisodes(3);
        Episode first = state.getEpisode();
        assertSame(state.getFlags().get(0).getEpisodes().get(0), first);
        assertSame(state.getFlags().get(0).getEpisodes().get(1), state.getRelativeEpisode(1));
        assertSame(state.getFlags().get(0).getEpisodes().get(2), state.getRelativeEpisode(99));
        assertSame(state.getFlags().get(0).getEpisodes().get(0), state.getRelativeEpisode(-99));
    }

    @Test
    public void resetClearsFlags() {
        VodPlaybackState state = stateWithEpisodes(2);
        state.reset();
        assertNull(state.getEpisode());
    }
}
