package com.fongmi.android.tv.ui.dialog;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.ui.activity.SettingPreloadActivity;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.SliderUtil;

public class PreloadDialog extends BaseAlertDialog {

    public static final int THREADS = PreloadSetting.TYPE_THREADS;
    public static final int SIZE = PreloadSetting.TYPE_SIZE;
    public static final int TIME = PreloadSetting.TYPE_TIME;

    private DialogSpeedBinding binding;
    private int type;

    public static void show(FragmentActivity activity, int type) {
        PreloadDialog dialog = new PreloadDialog();
        Bundle args = new Bundle();
        args.putInt("type", type);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        type = requireArguments().getInt("type");
        binding.slider.setValueTo(PreloadSetting.getMax(type));
        binding.slider.setValueFrom(PreloadSetting.getMin(type));
        binding.slider.setStepSize(PreloadSetting.getStep(type));
        SliderUtil.setValue(binding.slider, PreloadSetting.getValue(type));
        binding.slider.setLabelFormatter(value -> format(Math.round(value)));
    }

    @Override
    protected void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) ((SettingPreloadActivity) requireActivity()).setPreload(type, Math.round(SliderUtil.snap(slider, value)));
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





    private String format(int value) {
        if (type == THREADS) return getString(R.string.player_preload_threads_value, value);
        if (type == SIZE) return FileUtil.byteCountToDisplaySize(value * 1024L * 1024L);
        return getString(R.string.player_preload_time_value, value);
    }
}
