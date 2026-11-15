package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.databinding.AdapterEmptyStateBinding;

/**
 * 首页加载失败的空状态行：一行可聚焦的“首页加载失败，按确定重试”，按确定重新拉取首页。
 * <p>
 * 只在首页首载失败、一次自动重试也失败、结果为空时出现。getVideo() 拉取前会把推荐区之后
 * 的内容（含这行）清掉，所以它不会叠加，也不会挡住成功的结果。
 */
public class EmptyStatePresenter extends Presenter {

    private final Runnable onRetry;

    public EmptyStatePresenter(Runnable onRetry) {
        this.onRetry = onRetry;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterEmptyStateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        setOnClickListener(viewHolder, view -> onRetry.run());
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        public ViewHolder(@NonNull AdapterEmptyStateBinding binding) {
            super(binding.getRoot());
        }
    }
}
