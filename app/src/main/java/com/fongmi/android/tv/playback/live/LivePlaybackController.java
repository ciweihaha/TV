package com.fongmi.android.tv.playback.live;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.utils.ResUtil;

public class LivePlaybackController {

    private final LivePlaybackHost host;
    private final LiveDataSource dataSource;
    private final LivePlaybackState state;

    /** 单线路重试上限：瞬时 2004 重拉一次往往就好，超过 2 次说明不是抖动，直接弹错。 */
    private static final int MAX_ERROR_RETRIES = 2;
    /** 重试间隔：1s、2s 递增，和 mytv fork / 天光云影同一策略。 */
    private static final long[] RETRY_DELAYS = {1000, 2000};

    private int errorRetries;
    private boolean retryPending;
    private LivePlayRequest lastRequest;
    private final Runnable retryTask;

    public LivePlaybackController(LivePlaybackHost host, LiveDataSource dataSource, LivePlaybackState state) {
        this.host = host;
        this.dataSource = dataSource;
        this.state = state;
        // retryTask 必须在构造器里初始化：字段初始化器运行时 state 还没赋值，
        // lambda 里读 state 会报 "variable state might not have been initialized"（CI #128/#129）。
        this.retryTask = () -> {
            retryPending = false;
            if (lastRequest == null || lastRequest.isCatchup() || !lastRequest.matches(state.getChannel())) return;
            if (state.getPendingRequest() != null) return;
            requestLive();
        };
    }

    public void reset() {
        cancelRetry();
        dataSource.cancelUrl();
        state.reset();
    }

    private void cancelRetry() {
        App.removeCallbacks(retryTask);
        retryPending = false;
        errorRetries = 0;
    }

    public void selectGroup(Group group) {
        state.setGroup(group);
        host.renderGroupSelection(group);
        host.renderGroupChannels(group);
    }

    public void selectChannel(Channel channel) {
        if (channel == null) return;
        cancelRetry();
        LivePlayRequest activeRequest = state.getActiveRequest();
        boolean restore = activeRequest != null && activeRequest.matches(channel);
        state.setChannel(channel);
        host.renderChannelSelection(channel);
        if (restore) restorePlaybackIfNeeded();
        else requestLive();
    }

    public boolean selectEpg(EpgData data) {
        return selectEpg(data, C.TIME_UNSET);
    }

    public boolean selectEpg(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null || data == null) return false;
        if (data.isSelected()) return requestCatchup(data, startPositionMs);
        if (!channel.hasCatchup() && !channel.isRtsp()) return false;
        host.renderEpgSelection(data);
        return requestCatchup(data, C.TIME_UNSET);
    }

    public void onPlaybackResult(PlaybackResult<LivePlayRequest> playback) {
        if (playback == null || cannotApply(playback)) return;
        applyPlaybackResult(playback.result(), playback.request());
    }

    private void applyPlaybackResult(Result result, LivePlayRequest request) {
        String realUrl = result.getRealUrl();
        if (result.hasMsg() || TextUtils.isEmpty(realUrl)) failPlayback(result.getMsg());
        else if (Source.isPush(result.getUrl().v())) redirectPush(result.getUrl().v());
        else startResolvedPlayback(result, request, realUrl);
    }

    public void redirectPush(String url) {
        String target = url.substring("push://".length());
        dataSource.cancelUrl();
        state.clearPlayback();
        host.stopPlaybackForRefresh();
        if (target.isEmpty()) playbackError(ResUtil.getString(R.string.error_play_url));
        else host.openPush(target);
    }

    private void startResolvedPlayback(Result result, LivePlayRequest request, String realUrl) {
        errorRetries = 0;
        long position = result.hasPosition() ? result.getPosition() : request.getPosition();
        state.setPlayingRequest(request, realUrl);
        host.startPlayback(result, position, publishPlaybackMetadata(getEpgData(request)));
    }

    private void failPlayback(String msg) {
        state.clearPlayback();
        host.stopPlaybackForRefresh();
        playbackError(msg);
    }

    public void playbackError(String msg) {
        if (state.getPendingRequest() != null) return;
        if (retryPending) return;
        if (trySameLineRetry()) return;
        host.resetPlaybackForError(msg);
        fallbackAfterError();
    }

    /**
     * 单线路（或已在最后一条线路）时自动切线帮不上忙，改重试同一条线路。
     * 瞬时 2004（票据/分片过期、上游抖动）重拉一次往往就好，不必等用户手动切台；
     * 重试期间不弹错，耗尽后才走原来的报错 + 切线流程。
     * 多线路且还没到最后一条时不走这里——自动切线就是恢复手段（原有行为不变）。
     * 回看（catchup）不重试：requestLive 会切回直播，语义不对。
     */
    private boolean trySameLineRetry() {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        if (LiveSetting.isChange() && !channel.isLast()) return false;
        if (lastRequest == null || lastRequest.isCatchup() || !lastRequest.matches(channel)) return false;
        if (errorRetries >= MAX_ERROR_RETRIES) return false;
        retryPending = true;
        App.post(retryTask, RETRY_DELAYS[errorRetries]);
        errorRetries++;
        return true;
    }

    public void playbackEnded() {
        if (state.getPendingRequest() != null) return;
        if (host.isPlayerLive()) playNextProgram();
        else nextChannel();
    }

    public void onEpgChanged(EpgData data) {
        if (state.getChannel() != null && state.getPendingRequest() == null) publishPlaybackMetadata(data);
    }

    public void refresh() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LivePlayRequest request = state.getActiveRequest();
        if (request != null && request.isCatchup() && request.matches(channel)) {
            long position = host.getPlayerPosition();
            long startPositionMs = host.hasPlaybackSession() && position != C.TIME_UNSET ? position : request.getPosition();
            requestPlayback(LivePlayRequest.catchup(channel, request.getCatchupData(), startPositionMs), true);
        } else {
            requestLive();
        }
    }

    public void onPlaybackServiceReady() {
        host.restorePlaybackKey(state.getPlaybackKey());
        syncPlaybackMetadata();
        if (restorePlaybackIfNeeded()) return;
        LivePlayRequest request = state.getActiveRequest();
        if (state.getChannel() != null && (request == null || !request.matches(state.getChannel()))) requestLive();
    }

    private void syncPlaybackMetadata() {
        MediaMetadata metadata = state.getPlaybackMetadata();
        if (metadata != null) host.renderPlaybackMetadata(metadata);
    }

    private boolean restorePlaybackIfNeeded() {
        if (state.getPendingRequest() != null) return false;
        LivePlayRequest request = state.getPlayingRequest();
        if (request == null || !request.matches(state.getChannel()) || host.hasPlaybackSession()) return false;
        refresh();
        return true;
    }

    public void prevChannel() {
        moveChannel(-1);
    }

    public void nextChannel() {
        moveChannel(1);
    }

    public boolean hasPreviousChannel() {
        return state.getPreviousChannel() != null;
    }

    /**
     * Jumps back to the channel watched before the current one. Selecting
     * it swaps the two, so calling this again returns to where you were.
     */
    public boolean backToPreviousChannel() {
        Channel previous = state.getPreviousChannel();
        if (previous == null) return false;
        selectChannel(previous);
        return true;
    }

    public void prevLine() {
        switchLine(false, true);
    }

    public void nextLine(boolean show) {
        switchLine(true, show);
    }

    private void switchLine(boolean next, boolean show) {
        Channel channel = state.getChannel();
        if (channel == null || channel.isOnly()) return;
        channel.switchLine(next);
        host.renderLineSelection(channel, show);
        requestLive();
    }

    private void moveChannel(int delta) {
        Group group = state.getGroup();
        if (group == null || group.isEmpty()) return;
        int size = group.getChannel().size();
        int position = group.getPosition() + delta;
        boolean limit = position < 0 || position >= size;
        if (LiveSetting.isAcross() && limit) moveGroup(delta);
        else group.setPosition(limit ? wrap(position, size) : position);
        group = state.getGroup();
        if (group != null && !group.isEmpty()) selectChannel(group.current());
    }

    private void moveGroup(int delta) {
        int count = host.getGroupCount();
        if (count <= 1) return;
        Group current = state.getGroup();
        int position = host.getGroupPosition();
        for (int i = 0; i < count; i++) {
            position = wrap(position + delta, count);
            Group target = host.getGroup(position);
            if (target.equals(current)) return;
            if (target.skip() || target.isEmpty()) continue;
            state.setGroup(target);
            host.renderGroupSelection(target);
            host.renderGroupChannels(target);
            target.setPosition(delta > 0 ? 0 : target.getChannel().size() - 1);
            return;
        }
    }

    private int wrap(int position, int size) {
        return ((position % size) + size) % size;
    }

    private void fallbackAfterError() {
        Channel channel = state.getChannel();
        if (!LiveSetting.isChange() || channel == null || channel.isLast()) return;
        nextLine(true);
    }

    private void playNextProgram() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        EpgData data = getNextEpgData(channel);
        if (data != null && selectEpg(data)) return;
        data = getCurrentEpgData(channel);
        if (data != null) host.renderEpgSelection(data);
        refresh();
    }

    private void requestLive() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LiveConfig.get().setKeep(channel);
        requestPlayback(LivePlayRequest.live(channel, C.TIME_UNSET), true);
    }

    private boolean requestCatchup(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        requestPlayback(LivePlayRequest.catchup(channel, data, startPositionMs), false);
        return true;
    }

    private void requestPlayback(LivePlayRequest request, boolean showProgress) {
        if (!host.isPlaybackServiceReady()) return;
        lastRequest = request;
        state.setPendingRequest(request);
        host.preparePlaybackForRefresh();
        if (request.isCatchup()) host.onCatchupRequested();
        if (showProgress) host.showProgress();
        dataSource.getUrl(request);
    }

    private EpgData getEpgData(LivePlayRequest request) {
        return request.isCatchup() ? request.getCatchupData() : getCurrentEpgData(request.getChannel());
    }

    @Nullable
    private EpgData getNextEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId());
        int current = epg.getInRange();
        int position = epg.getSelected() + 1;
        return position <= current && position > 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    @Nullable
    private EpgData getCurrentEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId()).selected();
        int position = epg.getSelected();
        return position >= 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    private MediaMetadata publishPlaybackMetadata(@Nullable EpgData liveData) {
        LivePlayRequest request = state.getActiveRequest();
        boolean catchup = request != null && request.isCatchup();
        Channel channel = request == null ? state.getChannel() : request.getChannel();
        EpgData data = catchup ? request.getCatchupData() : liveData;
        int mediaType = catchup ? MediaMetadata.MEDIA_TYPE_VIDEO : MediaMetadata.MEDIA_TYPE_TV_CHANNEL;
        MediaMetadata metadata = buildPlaybackMetadata(channel, data).buildUpon().setMediaType(mediaType).build();
        state.setPlaybackMetadata(metadata);
        host.renderPlaybackMetadata(metadata);
        return metadata;
    }

    private MediaMetadata buildPlaybackMetadata(Channel channel, EpgData data) {
        String title = channel == null ? "" : channel.getShow();
        String logo = channel == null ? "" : channel.getLogo();
        return MediaItemFactory.buildLiveMetadata(title, data, logo);
    }

    private boolean cannotApply(PlaybackResult<LivePlayRequest> playback) {
        LivePlayRequest pending = state.getPendingRequest();
        LivePlayRequest request = playback.request();
        return pending == null || pending != request || !request.matches(state.getChannel());
    }
}
