package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerManager;
import com.github.catvod.utils.Task;

public final class PlaybackReset {

    public static void afterError(PlayerManager player) {
        afterError(player, null);
    }

    public static void afterError(PlayerManager player, Runnable beforeReset) {
        if (beforeReset != null) beforeReset.run();
        Task.executeSerial(() -> Track.delete(player.getKey()));
        player.resetTrack();
        player.reset();
        player.stop();
    }
}
