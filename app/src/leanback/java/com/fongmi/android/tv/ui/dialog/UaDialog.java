package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.github.catvod.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class UaDialog extends BaseSingleInputDialog {

    private boolean append = true;

    @Override
    protected void initView() {
        String text = Setting.getUa();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        super.initEvent();
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
    }

    private void detect(String s) {
        if (append && "c".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(Util.CHROME);
        } else if (append && "o".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(Util.OKHTTP);
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    @Override
    protected void onPositive(View view) {
        ((UaListener) requireActivity()).setUa(binding.text.getText().toString().trim());
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.text());
        binding.positive.performClick();
    }
}
