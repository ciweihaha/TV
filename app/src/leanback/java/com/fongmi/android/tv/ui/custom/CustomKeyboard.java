package com.fongmi.android.tv.ui.custom;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.utils.Util;

public class CustomKeyboard implements KeyboardAdapter.OnClickListener {

    private final ActivitySearchBinding binding;
    private final Callback callback;
    private KeyboardAdapter adapter;

    public static CustomKeyboard init(Callback callback, ActivitySearchBinding binding) {
        CustomKeyboard keyboard = new CustomKeyboard(callback, binding);
        keyboard.initView();
        return keyboard;
    }

    public CustomKeyboard(Callback callback, ActivitySearchBinding binding) {
        this.callback = callback;
        this.binding = binding;
    }

    public boolean isSystemMode() {
        return adapter != null && adapter.isSystemMode();
    }

    public void showSystemKeyboard() {
        Util.showKeyboard(binding.keyword);
    }

    public void hideSystemKeyboard() {
        Util.hideKeyboard(binding.keyword);
    }

    private void initView() {
        binding.keyboard.setItemAnimator(null);
        binding.keyboard.setHasFixedSize(false);
        binding.keyboard.addItemDecoration(new SpaceItemDecoration(7, 8));
        binding.keyboard.setAdapter(adapter = new KeyboardAdapter(this));
    }

    @Override
    public void onTextClick(String text) {
        StringBuilder sb = new StringBuilder(binding.keyword.getText().toString());
        int cursor = binding.keyword.getSelectionStart();
        if (binding.keyword.length() > 19) return;
        sb.insert(cursor, text);
        binding.keyword.setText(sb.toString());
        binding.keyword.setSelection(cursor + 1);
    }

    @Override
    public void onIconClick(int resId) {
        StringBuilder sb = new StringBuilder(binding.keyword.getText().toString());
        int cursor = binding.keyword.getSelectionStart();
        if (resId == R.drawable.ic_setting_home) callback.showDialog();
        else if (resId == R.drawable.ic_keyboard_remote) callback.onRemote();
        else if (resId == R.drawable.ic_keyboard_search) callback.onSearch();
        else if (resId == R.drawable.ic_keyboard_left) onMoveLeft(cursor);
        else if (resId == R.drawable.ic_keyboard_right) onMoveRight(cursor);
        else if (resId == R.drawable.ic_keyboard_back) onBackspace(sb, cursor);
        else if (resId == R.drawable.ic_keyboard) onToggleKeyboard();
    }

    private void onToggleKeyboard() {
        adapter.toggle();
        if (adapter.isSystemMode()) showSystemKeyboard();
        else hideSystemKeyboard();
    }

    private void onMoveLeft(int cursor) {
        binding.keyword.setSelection(--cursor < 0 ? 0 : cursor);
    }

    private void onMoveRight(int cursor) {
        binding.keyword.setSelection(++cursor > binding.keyword.length() ? binding.keyword.length() : cursor);
    }

    private void onBackspace(StringBuilder sb, int cursor) {
        if (cursor <= 0) return;
        sb.deleteCharAt(cursor - 1);
        binding.keyword.setText(sb.toString());
        binding.keyword.setSelection(cursor - 1);
    }

    @Override
    public boolean onLongClick(int resId) {
        if (resId != R.drawable.ic_keyboard_back) return false;
        binding.keyword.setText("");
        return true;
    }

    public interface Callback {

        void showDialog();

        void onRemote();

        void onSearch();
    }
}
