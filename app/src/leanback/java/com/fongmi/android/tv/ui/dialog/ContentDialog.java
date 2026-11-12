package com.fongmi.android.tv.ui.dialog;

import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogContentBinding;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.github.bassaer.library.MDColor;

public class ContentDialog extends BaseAlertDialog {

    private DialogContentBinding binding;
    private CharSequence content;

    public static ContentDialog create() {
        return new ContentDialog();
    }

    public ContentDialog content(CharSequence content) {
        this.content = content;
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogContentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        binding.text.setText(content, TextView.BufferType.SPANNABLE);
        binding.text.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(binding.text);
    }
}