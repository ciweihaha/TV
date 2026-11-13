package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDataSource;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;
import com.fongmi.android.tv.utils.Notify;

public class VideoViewModel extends SiteViewModel implements VodDataSource {

    private final MutableLiveData<VodDetailResult> detail;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> preload;
    private final MutableLiveData<PlaybackResult<VodPlayRequest>> playback;
    private final ViewModelTaskRunner<TaskType> requestTasks;
    private final VodPlaybackState playbackState;

    public VideoViewModel() {
        detail = new MutableLiveData<>();
        preload = new MutableLiveData<>();
        playback = new MutableLiveData<>();
        requestTasks = new ViewModelTaskRunner<>(TaskType.class);
        playbackState = new VodPlaybackState();
    }

    public LiveData<VodDetailResult> getDetail() {
        return detail;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPreload() {
        return preload;
    }

    public LiveData<PlaybackResult<VodPlayRequest>> getPlayback() {
        return playback;
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        return new VodPlaybackController(host, this, playbackState);
    }

    @Override
    public void detailContent(String key, String id) {
        detailContent(key, id, false);
    }

    @Override
    public void detailContent(String key, String id, boolean builtin) {
        requestTasks.execute(
                TaskType.DETAIL,
                Constant.TIMEOUT_VOD,
                () -> {
                    Result result = SiteApi.detailContent(key, id, builtin);
                    return new VodDetailResult(key, id, result, preloadHistory(key, id, result));
                },
                detail::postValue,
                error -> detail.postValue(new VodDetailResult(key, id, handleError(error), null)));
    }

    /** Load the playback history on the background thread (P3) so the
     * detail observer on the main thread never touches the database.
     * Falls back to a same-name history, as VodHistoryPolicy used to. */
    private History preloadHistory(String key, String id, Result result) {
        if (result.getVod() == null) return null;
        String historyKey = key + AppDatabase.SYMBOL + id + AppDatabase.SYMBOL + VodConfig.getCid();
        History history = History.find(historyKey);
        if (history != null) return history;
        history = new History();
        history.setKey(historyKey);
        history.setCid(VodConfig.getCid());
        history.setVodName(result.getVod().getName());
        history.findEpisode(result.getVod().getFlags());
        return history;
    }

    @Override
    public void playerContent(VodPlayRequest request) {
        loadPlayback(request, TaskType.PLAYBACK, playback);
    }

    @Override
    public void resolveContent(VodPlayRequest request, Result result) {
        requestTasks.execute(
                TaskType.PLAYBACK,
                Constant.TIMEOUT_VOD,
                () -> new PlaybackResult<>(request, SiteApi.resolvePlayerContent(request.getKey(), request.getId(), result)),
                playback::postValue,
                error -> playback.postValue(new PlaybackResult<>(request, handleError(error))));
    }

    @Override
    public void preloadContent(VodPlayRequest request) {
        loadPlayback(request, TaskType.PRELOAD, preload);
    }

    @Override
    public void cancelPreloadContent() {
        requestTasks.cancel(TaskType.PRELOAD);
    }

    @Override
    public void cancelContent() {
        for (TaskType type : TaskType.values()) requestTasks.cancel(type);
        stopSearch();
    }

    private void loadPlayback(VodPlayRequest request, TaskType type, MutableLiveData<PlaybackResult<VodPlayRequest>> output) {
        requestTasks.execute(
                type,
                Constant.TIMEOUT_VOD,
                () -> getPlaybackResult(request, type),
                output::postValue,
                error -> output.postValue(new PlaybackResult<>(request, handleError(error))));
    }

    private PlaybackResult<VodPlayRequest> getPlaybackResult(VodPlayRequest request, TaskType type) throws Exception {
        Result result = type == TaskType.PRELOAD
                ? SiteApi.preloadContent(request.getKey(), request.getFlag(), request.getId(), request.isBuiltin())
                : SiteApi.playerContent(request.getKey(), request.getFlag(), request.getId(), request.isBuiltin(), request.getQualityPosition());
        return new PlaybackResult<>(request, result);
    }

    private Result handleError(Throwable error) {
        error.printStackTrace();
        String message = Notify.getError(error);
        return message != null ? Result.error(message) : Result.empty();
    }

    @Override
    protected void onCleared() {
        requestTasks.cancelAll();
        playbackState.reset();
        super.onCleared();
    }

    private enum TaskType {DETAIL, PLAYBACK, PRELOAD}
}
