package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CategoryPagerAdapter;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.utils.CategoryMerger;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class VodActivity extends BasePagerActivity implements TypeAdapter.OnClickListener {

    private ActivityVodBinding mBinding;
    private TypeAdapter mAdapter;

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result, true);
    }

    public static void start(Activity activity, String key, Result result) {
        start(activity, key, result, false);
    }

    private static void start(Activity activity, String key, Result result, boolean home) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        intent.putExtra("home", home);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private Class getType() {
        return hasCategories() ? mAdapter.get(mBinding.pager.getCurrentItem()) : null;
    }

    private boolean hasCategories() {
        return mAdapter.getItemCount() > 0;
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setTypes();
        setPager();
        setViewModel();
    }

    private void setRecyclerView() {
        mBinding.recycler.requestFocus();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new TypeAdapter(this, getKey()));
    }

    private void setTypes() {
        // 分类归并：只显示电影 / 电视剧 / 动画片 / 综艺四个 tab，其余分类按名字规则并进这四个
        mAdapter.addAll(CategoryMerger.merge(getKey(), getResult().getTypes()));
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void setViewModel() {
        SiteViewModel viewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        viewModel.getAction().observe(this, result -> WebActivity.handleAction(this, result));
    }

    /**
     * 爬虫首页分类刷新后重建分类页（上游 cd4475f 的 applyHomeContent，改到 fork 的
     * BasePagerActivity 结构上：选页联动由 BasePagerActivity.initEvent 负责，这里只重建
     * 类型与 pager 并回到第一页；空结果不写筛选缓存，沿用 fork 的规则）。
     */
    private void applyHomeContent(Result result) {
        if (mBinding.pager.getAdapter() instanceof PageAdapter adapter) adapter.clearPages(mBinding.pager);
        getIntent().putExtra("result", result);
        if (!result.getTypes().isEmpty()) Cache.put(result, getKey());
        setTypes();
        setPager();
        if (hasCategories()) mBinding.pager.setCurrentItem(0);
    }

    private boolean isFilterVisible() {
        Class type = getType();
        return type != null && type.getFilter();
    }

    private void updateFilter(Class item) {
        int position = mAdapter.indexOf(item);
        if (!hasCategory(item, position)) return;
        item.setFilter(!item.getFilter());
        getFragment().toggleFilter(item.getFilter());
        mAdapter.notifyItemRangeChanged(position, 1);
    }

    private boolean hasCategory(Class item, int position) {
        return position >= 0 && mAdapter.get(position) == item;
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter(getType());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CATEGORY && hasCategories()) getFragment().onRefresh();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onHomeContentLoaded(RefreshEvent.HomeContentLoaded event) {
        if (getIntent().getBooleanExtra("home", false) && event.siteKey().equals(getKey())) applyHomeContent(event.result());
    }

    @Override
    public void onItemClick(Class item) {
        updateFilter(item);
    }

    @Override
    public void onRefresh(Class item) {
        if (hasCategory(item, mAdapter.indexOf(item))) getFragment().onRefresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event) && hasCategories()) updateFilter(getType());
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onBackInvoked() {
        if (!hasCategories()) super.onBackInvoked();
        else if (isFilterVisible()) closeFilter();
        else navigateCategoryBack();
    }

    private void navigateCategoryBack() {
        FolderFragment fragment = getFragment();
        if (fragment.moveToTop()) return;
        if (fragment.canBack()) fragment.goBack();
        else super.onBackInvoked();
    }

    class PageAdapter extends CategoryPagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }
    }
}
