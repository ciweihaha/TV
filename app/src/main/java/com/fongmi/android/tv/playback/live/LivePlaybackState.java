package com.fongmi.android.tv.playback.live;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.utils.CatchupResumeTracker;

public class LivePlaybackState {

    private final CatchupResumeTracker catchupResume = new CatchupResumeTracker();
    private LivePlayRequest pendingRequest;
    private LivePlayRequest playingRequest;
    private MediaMetadata playbackMetadata;
    private String playbackKey;
    private Channel channel;
    private Channel previousChannel;
    private Group group;

    public void reset() {
        clearPlayback();
        channel = null;
        previousChannel = null;
        group = null;
    }

    void clearPlayback() {
        pendingRequest = null;
        playingRequest = null;
        playbackMetadata = null;
        playbackKey = null;
    }

    /** Resume position (ms into the program) remembered for a catchup program key, or -1. */
    long getCatchupResumeMs(String key) {
        return catchupResume.resumeMs(key);
    }

    void rememberCatchup(String key, long positionMs, long programDurationMs) {
        catchupResume.remember(key, positionMs, programDurationMs);
    }

    Group getGroup() {
        return group;
    }

    void setGroup(Group group) {
        this.group = group;
    }

    Channel getChannel() {
        return channel;
    }

    @Nullable
    Channel getPreviousChannel() {
        return previousChannel;
    }

    void setChannel(Channel channel) {
        // Remember the channel being left so the user can jump straight
        // back to it. Re-selecting the same channel (e.g. restore after a
        // service reconnect) must not overwrite it.
        if (this.channel != null && this.channel != channel) previousChannel = this.channel;
        this.channel = channel;
        this.group = channel != null ? channel.getGroup() : group;
    }

    @Nullable
    LivePlayRequest getPendingRequest() {
        return pendingRequest;
    }

    @Nullable
    LivePlayRequest getActiveRequest() {
        return pendingRequest != null ? pendingRequest : playingRequest;
    }

    @Nullable
    LivePlayRequest getPlayingRequest() {
        return playingRequest;
    }

    void setPendingRequest(LivePlayRequest pendingRequest) {
        this.pendingRequest = pendingRequest;
    }

    void setPlayingRequest(LivePlayRequest playingRequest, String playbackKey) {
        this.playingRequest = playingRequest;
        this.playbackKey = playbackKey;
        this.pendingRequest = null;
    }

    @Nullable
    String getPlaybackKey() {
        return playbackKey;
    }

    @Nullable
    MediaMetadata getPlaybackMetadata() {
        return playbackMetadata;
    }

    void setPlaybackMetadata(MediaMetadata playbackMetadata) {
        this.playbackMetadata = playbackMetadata;
    }

}
