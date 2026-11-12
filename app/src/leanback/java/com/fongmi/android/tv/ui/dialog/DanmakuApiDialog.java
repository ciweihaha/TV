package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.DanmakuListener;
import com.fongmi.android.tv.setting.DanmakuSetting;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class DanmakuApiDialog extends BaseSingleInputDialog {

    public static void show(Fragment fragment) {
        new DanmakuApiDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected void initView() {
        String text;
        binding.text.setHint(R.string.danmaku_api);
        binding.text.setText(text = DanmakuSetting.getEffectiveApiUrl());
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void onPositive(View view) {
        getListener().setDanmakuApi(binding.text.getText().toString().trim());
        dismiss();
    }

    private DanmakuListener getListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof DanmakuListener) return (DanmakuListener) parent;
        return (DanmakuListener) requireActivity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.text());
        binding.positive.performClick();
    }
}
