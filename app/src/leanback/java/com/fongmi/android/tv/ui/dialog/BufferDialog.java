package com.fongmi.android.tv.ui.dialog;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogBufferBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.SliderUtil;

public class BufferDialog extends BaseAlertDialog {

    private DialogBufferBinding binding;

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogBufferBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        SliderUtil.setValue(binding.slider, PlayerSetting.getBuffer());
    }

    @Override
    protected void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) ((BufferListener) requireActivity()).setBuffer(Math.round(SliderUtil.snap(slider, value)));
        });
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dismiss();
            return enter;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
}
