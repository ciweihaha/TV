package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.SubtitleListener;
import com.fongmi.android.tv.setting.SubtitleSetting;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SubtitleApiDialog extends BaseSingleInputDialog {

    public static void show(Fragment fragment) {
        new SubtitleApiDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected void initView() {
        String text;
        binding.text.setHint(R.string.subtitle_search_token_hint);
        binding.text.setText(text = SubtitleSetting.getEffectiveToken());
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void onPositive(View view) {
        getListener().setSubtitleToken(binding.text.getText().toString().trim());
        dismiss();
    }

    private SubtitleListener getListener() {
        Fragment parent = getParentFragment();
        if (parent instanceof SubtitleListener listener) return listener;
        return (SubtitleListener) requireActivity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.text());
        binding.positive.performClick();
    }
}
