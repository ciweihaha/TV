package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.ui.fragment.SettingPreloadFragment;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.SliderUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PreloadDialog extends BaseAlertDialog {

    public static final int THREADS = PreloadSetting.TYPE_THREADS;
    public static final int SIZE = PreloadSetting.TYPE_SIZE;
    public static final int TIME = PreloadSetting.TYPE_TIME;

    private DialogSpeedBinding binding;
    private int type;
    private int value;

    public static void show(Fragment fragment, int type) {
        PreloadDialog dialog = new PreloadDialog();
        Bundle args = new Bundle();
        args.putInt("type", type);
        dialog.setArguments(args);
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSpeedBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        type = requireArguments().getInt("type");
        return builder().setTitle(getTitle()).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative);
    }

    @Override
    protected void initView() {
        binding.slider.setValueTo(PreloadSetting.getMax(type));
        binding.slider.setValueFrom(PreloadSetting.getMin(type));
        binding.slider.setStepSize(PreloadSetting.getStep(type));
        SliderUtil.setValue(binding.slider, value = PreloadSetting.getValue(type));
        binding.slider.setLabelFormatter(value -> format(Math.round(value)));
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((SettingPreloadFragment) requireParentFragment()).setPreload(type, Math.round(SliderUtil.snap(binding.slider, binding.slider.getValue())));
    }

    private void onNegative(DialogInterface dialog, int which) {
        ((SettingPreloadFragment) requireParentFragment()).setPreload(type, value);
    }

    private int getTitle() {
        if (type == THREADS) return R.string.player_preload_threads;
        if (type == SIZE) return R.string.player_preload_size;
        return R.string.player_preload_time;
    }





    private String format(int value) {
        if (type == THREADS) return getString(R.string.player_preload_threads_value, value);
        if (type == SIZE) return FileUtil.byteCountToDisplaySize(value * 1024L * 1024L);
        return getString(R.string.player_preload_time_value, value);
    }
}
