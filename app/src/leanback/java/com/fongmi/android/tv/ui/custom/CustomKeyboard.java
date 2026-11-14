package com.fongmi.android.tv.ui.custom;

import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.utils.Util;

import java.util.HashMap;
import java.util.Map;

public class CustomKeyboard implements KeyboardAdapter.OnClickListener {

    // 拼音 9 键每组可循环的字符（数字排在组末，经典手机多击方式：按 2 一下是 A，两下是 B，三下是 C，四下是 2）
    private static final Map<String, char[]> GROUPS = new HashMap<>();
    private static final int MULTI_TAP_TIMEOUT = 1000;

    static {
        GROUPS.put("1", new char[]{'.', ',', '?', '!', '1'});
        GROUPS.put("2", new char[]{'A', 'B', 'C', '2'});
        GROUPS.put("3", new char[]{'D', 'E', 'F', '3'});
        GROUPS.put("4", new char[]{'G', 'H', 'I', '4'});
        GROUPS.put("5", new char[]{'J', 'K', 'L', '5'});
        GROUPS.put("6", new char[]{'M', 'N', 'O', '6'});
        GROUPS.put("7", new char[]{'P', 'Q', 'R', 'S', '7'});
        GROUPS.put("8", new char[]{'T', 'U', 'V', '8'});
        GROUPS.put("9", new char[]{'W', 'X', 'Y', 'Z', '9'});
        GROUPS.put("0", new char[]{' ', '0'});
    }

    private final ActivitySearchBinding binding;
    private final Callback callback;
    private KeyboardAdapter adapter;
    private int customMode;
    private String lastDigit;
    private int lastIndex;
    private long lastTime;
    private int lastPos;

    public static CustomKeyboard init(Callback callback, ActivitySearchBinding binding) {
        CustomKeyboard keyboard = new CustomKeyboard(callback, binding);
        keyboard.initView();
        return keyboard;
    }

    public CustomKeyboard(Callback callback, ActivitySearchBinding binding) {
        this.callback = callback;
        this.binding = binding;
        this.customMode = Setting.getKeyboardMode();
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
        ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isWideKey(position) ? 2 : 1;
            }
        });
        binding.tabFull.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_FULL));
        binding.tabNine.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_PINYIN));
        binding.tabStroke.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_STROKE));
        binding.iconRemote.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard_remote));
        binding.iconLeft.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard_left));
        binding.iconRight.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard_right));
        binding.iconBack.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard_back));
        binding.iconBack.setOnLongClickListener(v -> onLongClick(R.drawable.ic_keyboard_back));
        binding.iconSearch.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard_search));
        binding.iconKeyboard.setOnClickListener(v -> onIconClick(R.drawable.ic_keyboard));
        binding.iconHome.setOnClickListener(v -> onIconClick(R.drawable.ic_setting_home));
        binding.systemIme.setOnClickListener(v -> onToggleKeyboard());
        binding.done.setOnClickListener(v -> callback.onSearch());
        setMode(customMode);
    }

    public void setMode(int mode) {
        lastDigit = null;
        if (mode == KeyboardAdapter.MODE_SYSTEM) {
            adapter.setMode(KeyboardAdapter.MODE_SYSTEM);
            binding.keyboardTabs.setVisibility(View.GONE);
            binding.keyboard.setVisibility(View.GONE);
            binding.systemIme.setText(R.string.keyboard_custom);
            showSystemKeyboard();
        } else {
            customMode = mode;
            Setting.putKeyboardMode(mode);
            adapter.setMode(mode);
            ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
            binding.keyboardTabs.setVisibility(View.VISIBLE);
            binding.keyboard.setVisibility(View.VISIBLE);
            binding.systemIme.setText(R.string.keyboard_system);
            hideSystemKeyboard();
            updateTabs();
        }
    }

    private void updateTabs() {
        binding.tabFull.setSelected(customMode == KeyboardAdapter.MODE_FULL);
        binding.tabNine.setSelected(customMode == KeyboardAdapter.MODE_PINYIN);
        binding.tabStroke.setSelected(customMode == KeyboardAdapter.MODE_STROKE);
        binding.tabFull.setTextColor(ContextCompat.getColor(binding.tabFull.getContext(), customMode == KeyboardAdapter.MODE_FULL ? R.color.white : R.color.white_60));
        binding.tabNine.setTextColor(ContextCompat.getColor(binding.tabNine.getContext(), customMode == KeyboardAdapter.MODE_PINYIN ? R.color.white : R.color.white_60));
        binding.tabStroke.setTextColor(ContextCompat.getColor(binding.tabStroke.getContext(), customMode == KeyboardAdapter.MODE_STROKE ? R.color.white : R.color.white_60));
    }

    public void focusSelectedTab() {
        if (binding.keyboardTabs.getVisibility() == View.VISIBLE) {
            if (customMode == KeyboardAdapter.MODE_PINYIN) binding.tabNine.requestFocus();
            else if (customMode == KeyboardAdapter.MODE_STROKE) binding.tabStroke.requestFocus();
            else binding.tabFull.requestFocus();
        } else {
            binding.iconRemote.requestFocus();
        }
    }

    @Override
    public void onTextClick(String text) {
        lastDigit = null;
        StringBuilder sb = new StringBuilder(binding.keyword.getText().toString());
        int cursor = binding.keyword.getSelectionStart();
        if (cursor < 0) cursor = sb.length();
        if (binding.keyword.length() > 19) return;
        sb.insert(cursor, text);
        binding.keyword.setText(sb.toString());
        binding.keyword.setSelection(cursor + text.length());
    }

    @Override
    public void onKeyClick(String digit) {
        char[] group = GROUPS.get(digit);
        if (group == null) return;
        String text = binding.keyword.getText().toString();
        int cursor = binding.keyword.getSelectionStart();
        if (cursor < 0) cursor = text.length();
        long now = System.currentTimeMillis();
        // 同一键 1 秒内连续按：替换光标前刚输入的字符为组内下一个（多击循环）
        if (digit.equals(lastDigit) && now - lastTime < MULTI_TAP_TIMEOUT && cursor == lastPos && cursor > 0 && text.charAt(cursor - 1) == group[lastIndex]) {
            lastIndex = (lastIndex + 1) % group.length;
            StringBuilder sb = new StringBuilder(text);
            sb.setCharAt(cursor - 1, group[lastIndex]);
            binding.keyword.setText(sb.toString());
            binding.keyword.setSelection(cursor);
        } else {
            if (binding.keyword.length() > 19) return;
            StringBuilder sb = new StringBuilder(text);
            sb.insert(cursor, group[0]);
            binding.keyword.setText(sb.toString());
            binding.keyword.setSelection(cursor + 1);
            lastIndex = 0;
        }
        lastDigit = digit;
        lastTime = now;
        lastPos = binding.keyword.getSelectionStart();
    }

    private void onIconClick(int resId) {
        lastDigit = null;
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
        if (isSystemMode()) setMode(customMode);
        else setMode(KeyboardAdapter.MODE_SYSTEM);
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

    private boolean onLongClick(int resId) {
        if (resId != R.drawable.ic_keyboard_back) return false;
        lastDigit = null;
        binding.keyword.setText("");
        return true;
    }

    public interface Callback {

        void showDialog();

        void onRemote();

        void onSearch();
    }
}
