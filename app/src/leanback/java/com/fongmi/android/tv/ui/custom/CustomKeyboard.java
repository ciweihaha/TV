package com.fongmi.android.tv.ui.custom;

import android.text.Editable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.utils.Util;

/**
 * 当贝市场式搜索键盘：A–Z 平铺字母格 + 数字盘 + 功能行（中/英切换 / 123 / 清空 / 退格）；
 * 空格键是字母格里跟在 Z 后面的一格（数字盘里跟在 9 后面），不是单独的通栏条。
 * <p>
 * 输入模型是“输拼音首字母、右侧实时出结果”：按键只向搜索框追加一个字母/数字，没有拼音拼写条、
 * 没有候选词提交、没有九键切音节。旧的 T9/拼写状态机（拼写串、数字序列、音节切分、音节点击、拼写提交等）已全部删除。
 * <p>
 * 中/英切换（2026-11-21）：默认中文（现状：输拼音首字母、右侧出联想）；英文 = 字母直输、按字面搜。
 * 两种模式下字母/数字键的行为相同（联想接口对拉丁字母原样查询，ZhuToPin.get 只转注音、不动拉丁字母），
 * 切换真正改变的是空格键：中文模式空格 = 确认首个联想词（与点首个建议相同），英文/数字盘模式空格 = 输入空格字符。
 * 当前模式显示在搜索页提示行第一行。
 * <p>
 * 搜索框内容就是唯一的文本状态（没有 committed/composing 两份状态）：外部改动搜索框（语音输入、
 * 历史记录、初始关键字）时不需要同步，追加/退格/清空都直接改搜索框本身。系统输入法模式下
 * 搜索框归系统输入法管，自带键盘格与功能行隐藏，只留模式行作切回入口。
 */
public class CustomKeyboard implements KeyboardAdapter.OnClickListener {

    private static final int MAX_LEN = 20;

    private final ActivitySearchBinding binding;
    private KeyboardAdapter adapter;
    private SpaceItemDecoration decoration;
    /** 当前是数字盘（true）还是字母格（false）；不记入 Setting，进搜索屏永远从字母格开始。 */
    private boolean digitsMode;
    /** 中/英输入模式：默认中文（false = 中文，true = 英文）；不记入 Setting，进搜索屏永远从中文开始。 */
    private boolean englishMode;
    /** 空格键在中文模式下的动作（确认首个联想词），由 SearchActivity 设置；英文/数字盘模式空格直接输空格字符，不走这里。 */
    private OnSpaceListener spaceListener;

    /** 中文模式空格键的回调：SearchActivity 用首个联想词实现（点首个建议的同一效果）。 */
    public interface OnSpaceListener {
        void onSpace();
    }

    public void setOnSpaceListener(OnSpaceListener listener) {
        this.spaceListener = listener;
    }

    public boolean isEnglishMode() {
        return englishMode;
    }

    public static CustomKeyboard init(ActivitySearchBinding binding) {
        CustomKeyboard keyboard = new CustomKeyboard(binding);
        keyboard.initView();
        return keyboard;
    }

    public CustomKeyboard(ActivitySearchBinding binding) {
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
        binding.keyboard.setAdapter(adapter = new KeyboardAdapter(this));
        ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
        binding.funcLang.setOnClickListener(v -> toggleLang());
        binding.funcDigits.setOnClickListener(v -> toggleDigits());
        binding.funcClear.setOnClickListener(v -> clearAll());
        binding.funcBack.setOnClickListener(v -> onBackspace());
        binding.funcBack.setOnLongClickListener(v -> {
            clearAll();
            return true;
        });
        binding.modeFull.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_LETTERS));
        binding.modeSystem.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_SYSTEM));
        updateDecoration();
        updateFuncDigitsLabel();
        setMode(KeyboardAdapter.MODE_LETTERS);
    }

    public void setMode(int mode) {
        if (mode == KeyboardAdapter.MODE_SYSTEM) {
            adapter.setMode(KeyboardAdapter.MODE_SYSTEM);
            binding.funcRow.setVisibility(View.GONE);
            binding.keyboard.setVisibility(View.GONE);
            showSystemKeyboard();
        } else {
            digitsMode = false;
            adapter.setMode(KeyboardAdapter.MODE_LETTERS);
            ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
            updateDecoration();
            binding.funcRow.setVisibility(View.VISIBLE);
            binding.keyboard.setVisibility(View.VISIBLE);
            hideSystemKeyboard();
            updateFuncDigitsLabel();
        }
        updateModeRow();
    }

    private void updateDecoration() {
        if (decoration != null) binding.keyboard.removeItemDecoration(decoration);
        // 键间距 6dp（以前 8dp）：6 行共省 10dp，左栏放得下，模式行不再被顶出屏幕
        binding.keyboard.addItemDecoration(decoration = new SpaceItemDecoration(adapter.getSpanCount(), 6));
    }

    /** 数字盘时功能行的 123 显示为 ABC（点按切回字母格），字母格时显示 123。 */
    private void updateFuncDigitsLabel() {
        binding.funcDigits.setText(digitsMode ? "ABC" : "123");
    }

    /** 模式行高亮当前模式：自带键盘时“全键盘”白字、系统输入法灰字，反之亦然。 */
    private void updateModeRow() {
        boolean system = isSystemMode();
        binding.modeFull.setTextColor(ContextCompat.getColor(binding.modeFull.getContext(), system ? R.color.white_60 : R.color.white));
        binding.modeSystem.setTextColor(ContextCompat.getColor(binding.modeSystem.getContext(), system ? R.color.white : R.color.white_60));
    }

    /** 搜索框下方向键：落到功能行第一个键（中/英切换）。 */
    public void focusFunctionRow() {
        binding.funcLang.requestFocus();
    }

    /** 中/英切换：中文 = 输拼音首字母出联想（现状），英文 = 字母直输按字面搜。
     * 键上文字固定是“中/英”，当前模式显示在提示行第一行（提示行平时是中文那句，
     * 英文模式换成英文提示）。字母/数字键本身在两种模式下行为相同。 */
    private void toggleLang() {
        englishMode = !englishMode;
        updateHint();
    }

    /** 提示行第一行随中/英文模式切换：中文是原来的“请输入拼音字母，或汉字”，英文是英文提示。 */
    private void updateHint() {
        binding.hintText.setText(englishMode ? R.string.search_hint_english : R.string.search_hint_dangbei);
    }

    /** 空格键（字母格里 Z 后面那格 / 数字盘里 9 后面那格）：英文/数字盘模式 = 输入一个空格字符
     * （空输入时不输前导空格）；中文模式 = 确认首个联想词（回调给 SearchActivity，
     * 与点首个建议相同；无建议时什么都不做）。 */
    private void onSpace() {
        if (digitsMode || englishMode) {
            Editable editable = binding.keyword.getText();
            if (editable == null || editable.length() == 0) return;
            appendText(" ");
            return;
        }
        if (spaceListener != null) spaceListener.onSpace();
    }

    /** 字母格 ↔ 数字盘切换（功能行 123/ABC 键）。 */
    private void toggleDigits() {
        digitsMode = !digitsMode;
        adapter.setMode(digitsMode ? KeyboardAdapter.MODE_DIGITS : KeyboardAdapter.MODE_LETTERS);
        ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
        updateDecoration();
        updateFuncDigitsLabel();
    }

    @Override
    public void onKey(KeyboardAdapter.KeyItem item) {
        if (item.kind == KeyboardAdapter.KIND_SPACE) {
            onSpace();
            return;
        }
        if (item.kind == KeyboardAdapter.KIND_LETTER || item.kind == KeyboardAdapter.KIND_DIGIT) appendText(item.text);
    }

    /** 向搜索框末尾追加一个字母/数字；TextWatcher 会触发联想（300ms 防抖，见 SearchActivity）。 */
    private void appendText(String text) {
        Editable editable = binding.keyword.getText();
        if (editable == null || editable.length() >= MAX_LEN) return;
        editable.append(text);
        binding.keyword.setSelection(editable.length());
    }

    /** 退格：删光标前一个字符（光标一般在末尾，即删最后一个字符）。系统模式也一样：
     * 直接改搜索框的 Editable，不回写任何缓存文本，不会覆盖系统输入法刚打的字。 */
    private void onBackspace() {
        Editable editable = binding.keyword.getText();
        if (editable == null) return;
        int pos = binding.keyword.getSelectionStart();
        if (pos <= 0) return;
        editable.delete(pos - 1, pos);
        binding.keyword.setSelection(pos - 1);
    }

    /** 清空：删掉搜索框全部内容（空输入时右侧切回历史 + 热搜）。 */
    private void clearAll() {
        binding.keyword.setText("");
    }
}
