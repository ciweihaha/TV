package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final ViewModelSearchRunner searches;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        tasks = new ViewModelTaskRunner<>(TaskType.class);
        searches = new ViewModelSearchRunner();
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(VodConfig.get().getHome()));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    /** 分类归并后的拉取：一个主分类对应多个成员 typeId，合并成一个结果返回。 */
    public void categoryContent(String key, List<String> tids, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tids, page, filter, extend));
    }

    /** 分类归并后的拉取（按成员分别发 extend）：每个成员只收到自己筛选定义里的键。 */
    public void categoryContent(String key, List<String> tids, String page, boolean filter, HashMap<String, String> extend, java.util.Map<String, HashMap<String, String>> extendsByTid) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tids, page, filter, extend, extendsByTid));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, result, new SearchTask(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        searches.start(sites, site -> new SearchTask(site, keyword, quick, "1"), search::postValue, quick);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        tasks.execute(type, Constant.TIMEOUT_VOD, callable, liveData::postValue, error -> handleError(type, liveData, error));
    }

    private void handleError(TaskType type, MutableLiveData<Result> liveData, Throwable error) {
        String message = Notify.getError(error);
        liveData.postValue(message != null ? Result.error(message) : Result.empty());
        // 绝大多数网络错（超时/断网/解析失败）getError 都返回 null，以前这里直接静默，
        // 首页首载失败就只剩空白；现在补一个通用提示，让用户至少知道是网络问题。
        // 注意：这里只补 Toast，不改 post 的 Result 类型——Result.error 会触发
        // BrowseTree 的 hasMsg 抛错等连锁行为，空结果仍按空结果走。
        if (type == TaskType.RESULT) App.post(() -> Notify.show(message != null ? message : ResUtil.getString(R.string.error_load_failed)));
        error.printStackTrace();
    }

    public void stopSearch() {
        searches.stop();
    }

    @Override
    protected void onCleared() {
        stopSearch();
        tasks.cancelAll();
    }

    private record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

        @Override
        public Result call() throws Exception {
            if (quick && !site.isQuickSearch()) return Result.empty();
            return SiteApi.searchContent(site, keyword, quick, page);
        }
    }

    private enum TaskType {RESULT, ACTION}
}
