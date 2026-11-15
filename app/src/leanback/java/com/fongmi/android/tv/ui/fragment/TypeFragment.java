package com.fongmi.android.tv.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentTypeBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FilterPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Task;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private VodPresenter mVodPresenter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private boolean headerVisible;
    private boolean filterVisible;
    private boolean filterRefreshing;
    /** 筛选面板刷新节流：30 秒内重复打开不重复拉首页。 */
    private static final long FILTER_REFRESH_INTERVAL = 30_000;
    private long lastFilterRefreshTime;
    /** 刷新前旧筛选的快照：新定义无效/空结果/失败时回滚用。 */
    private Map<String, List<Filter>> filterBackup;

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder) {
        return newInstance(key, typeId, style, extend, folder, new ArrayList<>());
    }

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder, ArrayList<String> mergedIds) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        args.putStringArrayList("mergedIds", mergedIds);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return getArguments().getString("typeId");
    }

    /** 分类归并后的成员 typeId（主分类自身排第一）；空表示未归并，走单分类路径。 */
    private ArrayList<String> getMergedIds() {
        ArrayList<String> ids = getArguments().getStringArrayList("mergedIds");
        return ids == null ? new ArrayList<>() : ids;
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(getArguments().getParcelable("style"));
    }

    private HashMap<String, String> getExtend() {
        return (HashMap<String, String>) getArguments().getSerializable("extend");
    }

    private List<Filter> getFilter() {
        String typeId = getTypeId();
        if (!Cache.contains(getKey(), typeId)) typeId = getParent().getType().getTypeId();
        return Cache.copy(getKey(), typeId);
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private FolderFragment getParent() {
        return ((FolderFragment) getParentFragment());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mExtends = getExtend();
        mFilters = getFilter();
        setRecyclerView();
        setViewModel();
        setFilters();
        getVideo();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setHeader(getActivity(), R.id.recycler);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
    }

    private void setFilters() {
        for (Filter filter : mFilters) if (mExtends.containsKey(filter.getKey())) filter.setSelected(mExtends.get(filter.getKey()));
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setSelected(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isSelected()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        onRefresh();
    }

    private void getVideo() {
        mLast = null;
        checkFilter();
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        // 归并后的主分类：一次拉取全部成员 typeId 并合并去重；否则走单分类。
        // 每个成员只发自己筛选定义里的 extend，不共用主分类同一份（M23）。
        List<String> mergedIds = getMergedIds();
        if (mergedIds.size() > 1) mViewModel.categoryContent(getKey(), mergedIds, page, true, mExtends, getMemberExtends(mergedIds));
        else mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    /** 按成员拆 extend：只保留该成员筛选定义里有的键；成员没有缓存定义时回退到主分类的 extend。 */
    private HashMap<String, HashMap<String, String>> getMemberExtends(List<String> tids) {
        HashMap<String, HashMap<String, String>> map = new HashMap<>();
        for (String tid : tids) {
            List<Filter> memberFilters = Cache.copy(getKey(), tid);
            HashMap<String, String> memberExtend = new HashMap<>();
            if (memberFilters.isEmpty()) {
                memberExtend.putAll(mExtends);
            } else {
                Set<String> keys = new HashSet<>();
                for (Filter filter : memberFilters) keys.add(filter.getKey());
                for (Map.Entry<String, String> entry : mExtends.entrySet()) {
                    if (keys.contains(entry.getKey())) memberExtend.put(entry.getKey(), entry.getValue());
                }
            }
            map.put(tid, memberExtend);
        }
        return map;
    }

    private void setAdapter(Result result) {
        boolean first = mScroller.first();
        boolean flag = mExtends.isEmpty();
        int size = result.getList().size();
        mBinding.progressLayout.showContent(first & flag, size);
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.endLoading(result);
        if (size > 0) addVideo(result);
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
        checkMore();
    }

    private void checkMore() {
        if (mScroller.isDisable() || mAdapter.size() >= 5) return;
        mScroller.checkMore();
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addGrid(items.subList(size, items.size()), style);
        return true;
    }

    private void addGrid(List<Vod> items, Style style) {
        if (mVodPresenter == null || !mVodPresenter.matches(style)) {
            mVodPresenter = new VodPresenter(this, style);
            mLast = null;
        }
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) rows.add(createVideoRow(part));
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow createVideoRow(List<Vod> items) {
        mLast = new ArrayObjectAdapter(mVodPresenter);
        mLast.addAll(0, items);
        return new ListRow(mLast);
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showFilter() {
        List<ListRow> rows = new ArrayList<>();
        for (Filter filter : mFilters) rows.add(getRow(filter));
        mBinding.recycler.postDelayed(() -> mBinding.recycler.scrollToPosition(0), 48);
        mAdapter.addAll(0, rows);
    }

    private void hideFilter() {
        mAdapter.removeItems(0, mFilters.size());
    }

    public void toggleFilter(boolean visible) {
        this.filterVisible = visible;
        if (visible) {
            if (!mFilters.isEmpty()) showFilter();
            refreshFilters();
        } else if (!mFilters.isEmpty()) {
            hideFilter();
        }
    }

    /**
     * Refresh the site's filter definitions in the background.
     *
     * The filter Cache is only populated when the home page loads
     * (HomeActivity.onResult -> Cache.put(result, siteKey)) and is never refreshed
     * while the app runs, so if the site changes its filter keys/values the app keeps
     * sending the stale keys: the site ignores the unknown extend keys and answers
     * with the category's default list, i.e. tapping a filter visibly does nothing.
     * Re-fetching home here makes the chips — and the extend keys sent — match the
     * site's current schema. SiteApi.homeContent is cached for 5 minutes, so repeated
     * opens are cheap, and the wall itself is never touched: only the Cache and the
     * filter rows are rebuilt, and only when the definitions actually changed.
     * <p>
     * 节流：30 秒内重复打开面板不重复拉首页；失败不计入节流（时间戳只在成功时记），
     * 失败后可以立即重试。失败保护：拉取前先备份旧筛选，
     * 失败/空结果时回滚到旧筛选并提示，旧数据保留、面板不被清空。
     */
    private void refreshFilters() {
        if (filterRefreshing) return;
        long now = System.currentTimeMillis();
        if (now - lastFilterRefreshTime < FILTER_REFRESH_INTERVAL) return;
        // 节流时间戳只在成功时记（见 applyFilters）：失败也计次的话，失败后 30 秒内无法重试
        filterRefreshing = true;
        filterBackup = Cache.snapshot();
        Site site = getSite();
        // 先清掉本站 5 分钟的首页缓存，否则 homeContent 直接返回缓存、
        // 所谓“重拉取”根本没发出请求，定义永远判“没变”（v5.9.5 的重拉取就是这么失效的）
        SiteApi.invalidateHome(site);
        Futures.addCallback(Task.submit(() -> SiteApi.homeContent(site), Constant.TIMEOUT_VOD), Task.callback(
                result -> App.post(() -> applyFilters(result)),
                error -> {
                    error.printStackTrace();
                    App.post(() -> {
                        filterRefreshing = false;
                        if (filterBackup != null) Cache.restore(filterBackup);
                        Notify.show("筛选刷新失败，已保留旧筛选");
                    });
                }
        ), MoreExecutors.directExecutor());
    }

    private void applyFilters(Result result) {
        filterRefreshing = false;
        if (mBinding == null || getView() == null || !isAdded()) {
            if (filterBackup != null) Cache.restore(filterBackup);
            return;
        }
        if (result == null || result.getTypes().isEmpty()) {
            if (filterBackup != null) Cache.restore(filterBackup);
            Notify.show("筛选刷新失败，已保留旧筛选");
            return;
        }
        Cache.put(result, getKey());
        List<Filter> fresh = getFilter();
        // 新定义是空的（站点本次没返回筛选）：回滚到旧筛选，不清空全站筛选缓存
        if (fresh.isEmpty() && !mFilters.isEmpty()) {
            if (filterBackup != null) Cache.restore(filterBackup);
            Notify.show("筛选刷新失败，已保留旧筛选");
            return;
        }
        // 刷新成功才记节流时间：失败/空结果不计次，用户可以立即再试
        lastFilterRefreshTime = System.currentTimeMillis();
        if (sameFilters(mFilters, fresh)) return;
        if (filterVisible && !mFilters.isEmpty()) hideFilter();
        mFilters = fresh;
        setFilters();
        mExtends.entrySet().removeIf(entry -> mFilters.stream()
                .filter(filter -> filter.getKey().equals(entry.getKey()))
                .flatMap(filter -> filter.getValue().stream())
                .noneMatch(value -> value.getV().equals(entry.getValue())));
        if (filterVisible && !mFilters.isEmpty()) showFilter();
        // 筛选定义变了：重拉一次视频列表，否则面板换了、列表还是按旧键拉的旧数据
        getVideo();
    }

    private static boolean sameFilters(List<Filter> a, List<Filter> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Filter fa = a.get(i), fb = b.get(i);
            if (!Objects.equals(fa.getKey(), fb.getKey())) return false;
            if (!Objects.equals(fa.getName(), fb.getName())) return false;
            List<Value> va = fa.getValue(), vb = fb.getValue();
            if (va.size() != vb.size()) return false;
            for (int j = 0; j < va.size(); j++) {
                if (!Objects.equals(va.get(j).getV(), vb.get(j).getV())) return false;
                if (!Objects.equals(va.get(j).getN(), vb.get(j).getN())) return false;
            }
        }
        return true;
    }

    private void checkFilter() {
        int adapterSize = mAdapter.size();
        int filterSize = filterVisible ? mFilters.size() : 0;
        if (adapterSize > filterSize) mAdapter.removeItems(filterSize, mAdapter.size() - filterSize);
        if (adapterSize == 0) mBinding.progressLayout.showProgress();
        else mBinding.swipeLayout.setRefreshing(true);
    }

    public void onRefresh() {
        getVideo();
    }

    public boolean moveToTop() {
        return mBinding != null && mBinding.recycler.moveToTop();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            getParent().openFolder(item.getId(), mExtends);
            headerVisible = mBinding.recycler.isHeaderVisible();
        } else {
            if (getSite().isIndex()) CollectActivity.start(requireActivity(), item.getName());
            else VideoActivity.start(requireActivity(), getKey(), item, isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        CollectActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public boolean onLoadMore(String page) {
        getVideo(getTypeId(), page);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            mBinding.recycler.showHeader();
        } else {
            if (headerVisible) mBinding.recycler.showHeader();
            else mBinding.recycler.hideHeader();
            mBinding.recycler.requestFocus();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        moveToTop();
    }
}
