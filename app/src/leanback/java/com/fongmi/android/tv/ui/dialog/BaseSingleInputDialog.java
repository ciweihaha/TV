package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogSingleInputBinding;


public abstract class BaseSingleInputDialog extends BaseEventDialog {

    protected DialogSingleInputBinding binding;

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSingleInputBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initEvent() {
        binding.phone.setOnClickListener(view -> LocalConnectDialog.show(requireActivity(), "settings"));
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    protected abstract void onPositive(View view);

    protected void onNegative(View view) {
        dismiss();
    }
}
