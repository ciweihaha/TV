package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.sidesheet.SideSheetDialog;

public abstract class BaseSideSheetDialog extends AppCompatDialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    protected int getWidth() {
        return Math.min(ResUtil.dp2px(420), ResUtil.getScreenWidth() / 2);
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment.getClass() == getClass()) return;
        show(manager, null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SideSheetDialog dialog = new SideSheetDialog(requireContext());
        dialog.getBehavior().setDraggable(false);
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    @Override
    public void onStart() {
        super.onStart();
        FrameLayout sheet = getDialog().findViewById(com.google.android.material.R.id.m3_side_sheet);
        if (sheet == null) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.width = getWidth();
        sheet.setLayoutParams(params);
    }
}
