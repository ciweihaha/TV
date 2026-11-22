package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.utils.PinyinTable;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度输入法电视版式搜索键盘：拼音全键（QWERTY 三行错列）/ 拼音九键（T9 预测）+ 符号盘。
 * <p>
 * 面板（底部灰色）：顶部页签只有拼音全键 / 拼音九键（没有笔画）；键盘上方是候选栏（SearchActivity 管，
 * 中文模式有拼写时显示，首选蓝底）；左列 分词 / ?12 / 中/英，右列 删除 / 完成（完成占两行高），
 * 空格在全键第三行中间、九键是 0 键；左下角 du 标志。
 * <p>
 * 输入方式与手机输入法一致：正在拼的拼音显示在搜索框里（已提交文本 + 拼写串），候选词走现有的
 * getSuggest → iQIYI 建议链；点候选栏选词上屏并搜索；退格先删拼写中的最后一个字母/数字，再删已提交的字符；
 * 点“完成”先提交首选候选（没有则提交拼音原文）再搜索；中文空格 = 确认首个联想词并搜索。
 * <p>
 * 九键中文是预测输入（T9）：每键只按一次，数字序列按 PinyinTable 切成拼音音节；分词键在数字序列末尾
 * 强制断一个音节；不编造中文词库。九键英文是多击输入：同键连按在该键字母间循环（如 2→A→B→C），
 * 换键或停顿超过 1.2 秒即确认当前字母。
 * <p>
 * 中/英切换：中文 = 输拼音出候选栏；英文 = 字母直输、没有候选栏。切到英文时正在拼的拼音按原文提交，
 * 不丢输入。系统输入法模式下搜索框归系统输入法管，自带面板隐藏，只留模式行作切回入口。
 */
public class CustomKeyboard implements KeyboardAdapter.OnClickListener {

    private static final int MAX_LEN = 20;
    private static final char[] PUNCTS = {'.', ',', '?', '!'};
    private static final long PUNCT_TIMEOUT = 1000;
    /** 英文九键多击：同键连按的确认时限。 */
    private static final long TAP_TIMEOUT = 1200;
    /** 九键各键的多击字母表（与键面一致）。 */
    private static final String[] TAP_LETTERS = {"", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};

    private final ActivitySearchBinding binding;
    private final Callback callback;
    private KeyboardAdapter adapter;
    private SpaceItemDecoration decoration;
    private int customMode;

    /** 已提交进搜索框的文本（中文或字符）。 */
    private final StringBuilder committed = new StringBuilder();
    /** 全键正在拼的拼音字母（可含分词符 '）。 */
    private final StringBuilder composing = new StringBuilder();
    /** 九键已按的数字序列。 */
    private final StringBuilder digits = new StringBuilder();
    /** 九键当前切出的音节。 */
    private List<String> syllables = new ArrayList<>();
    /** 九键未切出的尾部数字。 */
    private String remainder = "";
    /** 分词键强制断开的音节边界（数字序列里的下标位置）。 */
    private final List<Integer> segBreaks = new ArrayList<>();
    /** 1 键标点循环：上次输入的标点与时间。 */
    private String lastPunct = "";
    private long lastPunctTime;
    /** 英文九键多击：上次按的数字键与时间。 */
    private String lastTapDigit = "";
    private long lastTapTime;
    /** 中/英输入模式：默认中文（false = 中文，true = 英文）；不记入 Setting，进搜索屏永远从中文开始。 */
    private boolean englishMode;
    /** 光标在已提交文本里的位置；-1 表示末尾（默认）。新版没有光标移动键，恒为末尾。 */
    private int cursorPos = -1;

    /** 光标处的有效位置（夹在 [0, committed.length] 内）。 */
    private int editPos() {
        if (cursorPos < 0) return committed.length();
        return Math.max(0, Math.min(cursorPos, committed.length()));
    }

    /** 在光标处插入已提交文本，光标移到插入内容之后；末尾时保持 -1。 */
    private void insertCommitted(String text) {
        int pos = editPos();
        committed.insert(pos, text);
        cursorPos = pos + text.length();
        if (cursorPos >= committed.length()) cursorPos = -1;
        renderBox();
    }

    /** 删掉光标前一个已提交字符，光标跟进一步。 */
    private void deleteCommittedBeforeCursor() {
        int pos = editPos();
        if (pos <= 0) return;
        committed.deleteCharAt(pos - 1);
        cursorPos = pos - 1;
        if (cursorPos >= committed.length()) cursorPos = -1;
        renderBox();
    }

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

    public boolean isEnglishMode() {
        return englishMode;
    }

    public int getMode() {
        return adapter == null ? customMode : adapter.getMode();
    }

    public void showSystemKeyboard() {
        Util.showKeyboard(binding.keyword);
        // 系统输入法有时第一次弹不出来（EMUI/Leanback 下 showSoftInput 可能被拒）：600ms 后若输入法
        // 仍未激活，再强制弹一次。弹不出来也不困住用户：模式行 + 返回键都能切回自带键盘。
        binding.keyword.postDelayed(() -> {
            if (!isSystemMode() || binding.keyword.getWindowToken() == null) return;
            InputMethodManager imm = (InputMethodManager) binding.keyword.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && !imm.isActive(binding.keyword)) {
                imm.showSoftInput(binding.keyword, InputMethodManager.SHOW_FORCED);
            }
        }, 600);
    }

    /** 从系统输入法模式回来自带键盘（返回键/模式行共用）：回到进入系统模式前的全键/九键。 */
    public void backToCustomMode() {
        if (isSystemMode()) setMode(customMode);
    }

    public void hideSystemKeyboard() {
        Util.hideKeyboard(binding.keyword);
    }

    /** 正在拼写（拼写串有内容）。 */
    public boolean isComposing() {
        return composingDisplay().length() > 0;
    }

    /** renderBox 正在回写搜索框（TextWatcher 回调里用它区分“键盘自己改的”还是“外部改的”）。 */
    private boolean rendering;

    public boolean isRendering() {
        return rendering;
    }

    /** 正在拼的拼音在搜索框里的显示：全键是字母串（含分词符），九键是切出的音节连写 + 未切出的尾部数字。 */
    public String composingDisplay() {
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
            StringBuilder sb = new StringBuilder();
            for (String s : syllables) sb.append(s);
            sb.append(remainder);
            return sb.toString();
        }
        return composing.toString();
    }

    private void initView() {
        binding.keyboard.setItemAnimator(null);
        binding.keyboard.setHasFixedSize(false);
        binding.keyboard.setAdapter(adapter = new KeyboardAdapter(this));
        ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getSpanSize(position);
            }
        });
        binding.tabFull.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_FULL));
        binding.tabNine.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_PINYIN));
        binding.segKey.setOnClickListener(v -> onSegmentKey());
        binding.symKey.setOnClickListener(v -> onToggleSymbol());
        binding.langKey.setOnClickListener(v -> toggleLang());
        binding.delKey.setOnClickListener(v -> onBackspace());
        binding.delKey.setOnLongClickListener(v -> {
            committed.setLength(0);
            cursorPos = -1;
            clearComposingState();
            resetTap();
            renderBox();
            return true;
        });
        binding.doneKey.setOnClickListener(v -> callback.onSearch());
        binding.modeFull.setOnClickListener(v -> setMode(customMode));
        binding.modeSystem.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_SYSTEM));
        setMode(customMode);
    }

    public void setMode(int mode) {
        if (mode == KeyboardAdapter.MODE_SYSTEM) {
            // 进系统模式先同步搜索框的当前内容：系统输入法接管后框内文本可能已变，
            // 不同步的话 committed 还是旧文本，之后按删除会把旧文本回写覆盖新输入
            syncFromBox();
            adapter.setMode(KeyboardAdapter.MODE_SYSTEM);
            binding.kbPanel.setVisibility(View.GONE);
            showSystemKeyboard();
        } else if (mode == KeyboardAdapter.MODE_SYMBOL) {
            // 符号盘是临时布局：不记入模式记忆，customMode 保持进入符号盘前的模式（全键/九键），按 ABC 回到该模式
            adapter.setMode(KeyboardAdapter.MODE_SYMBOL);
            ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
            updateDecoration();
        } else {
            customMode = mode;
            Setting.putKeyboardMode(mode);
            syncFromBox();
            adapter.setMode(mode);
            ((GridLayoutManager) binding.keyboard.getLayoutManager()).setSpanCount(adapter.getSpanCount());
            updateDecoration();
            binding.kbPanel.setVisibility(View.VISIBLE);
            hideSystemKeyboard();
            updateTabs();
        }
        updateModeRow();
        callback.onStateChanged();
    }

    private void updateDecoration() {
        if (decoration != null) binding.keyboard.removeItemDecoration(decoration);
        binding.keyboard.addItemDecoration(decoration = new SpaceItemDecoration(adapter.getSpanCount(), 6));
    }

    private void updateTabs() {
        binding.tabFull.setSelected(customMode == KeyboardAdapter.MODE_FULL);
        binding.tabNine.setSelected(customMode == KeyboardAdapter.MODE_PINYIN);
        binding.tabFull.setTextColor(ContextCompat.getColor(binding.tabFull.getContext(), customMode == KeyboardAdapter.MODE_FULL ? R.color.white : R.color.white_60));
        binding.tabNine.setTextColor(ContextCompat.getColor(binding.tabNine.getContext(), customMode == KeyboardAdapter.MODE_PINYIN ? R.color.white : R.color.white_60));
    }

    /** 模式行高亮当前模式：自带键盘时“全键盘”白字、系统输入法灰字，反之亦然。 */
    private void updateModeRow() {
        boolean system = isSystemMode();
        binding.modeFull.setTextColor(ContextCompat.getColor(binding.modeFull.getContext(), system ? R.color.white_60 : R.color.white));
        binding.modeSystem.setTextColor(ContextCompat.getColor(binding.modeSystem.getContext(), system ? R.color.white : R.color.white_60));
    }

    /** 搜索框下方向键：落到页签行当前选中的页签。 */
    public void focusSelectedTab() {
        if (binding.kbPanel.getVisibility() == View.VISIBLE) {
            if (customMode == KeyboardAdapter.MODE_PINYIN) binding.tabNine.requestFocus();
            else binding.tabFull.requestFocus();
        } else {
            binding.modeFull.requestFocus();
        }
    }

    private void renderBox() {
        String text = committed.toString() + composingDisplay();
        rendering = true;
        try {
            binding.keyword.setText(text);
        } finally {
            rendering = false;
        }
        // 正在拼写时光标在拼写串末尾；否则光标停在移动后的位置（默认末尾）
        binding.keyword.setSelection(isComposing() ? text.length() : editPos());
    }

    /** 外部改动了搜索框（语音输入、历史记录、初始关键字、模式切换）：以框内文本为准，丢弃拼写状态。 */
    public void syncFromBox() {
        committed.setLength(0);
        committed.append(binding.keyword.getText().toString());
        cursorPos = -1;
        clearComposingState();
        resetTap();
    }

    /** 搜索框文本被键盘之外改了（蓝牙键盘/按键注入等直接往输入框里打字）：以框内文本为准，
     * 不能留着陈旧的 committed，否则下次按删除会把旧文本回写、覆盖刚打的字。
     * 全键模式下，框内超出已提交部分的尾巴算正在拼的拼音（候选栏照常出）；九键/系统模式直接按整段文本同步。 */
    public void syncExternalInput() {
        if (isSystemMode()) {
            syncFromBox();
            return;
        }
        // 英文模式没有拼写串：外部输入直接算已提交文本
        if (englishMode) {
            syncFromBox();
            return;
        }
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
            syncFromBox();
            return;
        }
        String text = binding.keyword.getText().toString();
        String committedText = committed.toString();
        if (text.startsWith(committedText)) {
            String suffix = text.substring(committedText.length());
            if (!suffix.equals(composing.toString())) {
                composing.setLength(0);
                composing.append(suffix);
                resetTap();
            }
        } else {
            syncFromBox();
        }
    }

    private void clearComposingState() {
        composing.setLength(0);
        digits.setLength(0);
        syllables.clear();
        remainder = "";
        segBreaks.clear();
    }

    private void resegment() {
        if (segBreaks.isEmpty()) {
            PinyinTable.Segmentation seg = PinyinTable.segment(digits.toString());
            syllables = new ArrayList<>(seg.syllables);
            remainder = seg.remainder;
            return;
        }
        // 有强制断点：按断点分段切分，未切出的尾部数字顺延到下一段再切
        List<String> all = new ArrayList<>();
        String pending = "";
        int prev = 0;
        List<Integer> breaks = new ArrayList<>(segBreaks);
        java.util.Collections.sort(breaks);
        for (int b : breaks) {
            if (b <= prev || b > digits.length()) continue;
            String part = pending + digits.substring(prev, b);
            PinyinTable.Segmentation seg = PinyinTable.segment(part);
            all.addAll(seg.syllables);
            pending = seg.remainder;
            prev = b;
        }
        String last = pending + digits.substring(prev);
        PinyinTable.Segmentation seg = PinyinTable.segment(last);
        all.addAll(seg.syllables);
        syllables = all;
        remainder = seg.remainder;
    }

    /** 把正在拼的拼音提交（useCandidate=true 时用首选候选，没有则提交拼音原文；false 时提交拼音原文），拼写状态清空。 */
    public void commitComposing(boolean useCandidate) {
        if (!isComposing()) return;
        String candidate = useCandidate ? callback.getFirstCandidate() : null;
        String text = candidate != null && !candidate.isEmpty() ? candidate : composingDisplay();
        int pos = editPos();
        committed.insert(pos, text);
        cursorPos = pos + text.length();
        if (cursorPos >= committed.length()) cursorPos = -1;
        clearComposingState();
        resetTap();
        renderBox();
    }

    /** 中/英切换：中文 = 输拼音出候选栏，英文 = 字母直输、无候选栏。切到英文时正在拼的拼音按原文提交，不丢输入。 */
    private void toggleLang() {
        if (!englishMode) commitComposing(false);
        englishMode = !englishMode;
        updateHint();
        callback.onStateChanged();
    }

    /** 提示行第一行随中/英文模式切换：中文是原来的“请输入拼音字母，或汉字”，英文是英文提示。 */
    private void updateHint() {
        binding.hintText.setText(englishMode ? R.string.search_hint_english : R.string.search_hint_dangbei);
    }

    /** 分词键：九键中文在数字序列末尾强制断一个音节；全键中文在拼写串末尾加分词符。英文模式无拼音可分，不做事。 */
    private void onSegmentKey() {
        if (isSystemMode() || englishMode) return;
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
            if (digits.length() == 0 || segBreaks.contains(digits.length())) return;
            segBreaks.add(digits.length());
            resegment();
            renderBox();
        } else {
            if (composing.length() == 0 || composing.charAt(composing.length() - 1) == '\'') return;
            composing.append('\'');
            renderBox();
        }
    }

    /** ?12 键：符号盘与当前模式互切；进符号盘前正在拼的拼音按原文提交。 */
    private void onToggleSymbol() {
        if (isSystemMode()) return;
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_SYMBOL) {
            setMode(customMode);
            return;
        }
        commitComposing(false);
        setMode(KeyboardAdapter.MODE_SYMBOL);
    }

    @Override
    public void onKey(KeyboardAdapter.KeyItem item) {
        if (item.kind == KeyboardAdapter.KIND_SPACER) return;
        resetTapIfNotDigit(item);
        switch (item.kind) {
            case KeyboardAdapter.KIND_LETTER:
                onLetter(item.text);
                break;
            case KeyboardAdapter.KIND_DIGIT:
                onDigit(item.text);
                break;
            case KeyboardAdapter.KIND_SYMBOL:
                onSymbol(item.text);
                break;
            case KeyboardAdapter.KIND_ACTION:
                onAction(item.action);
                break;
        }
    }

    @Override
    public void onKeyLongPress(KeyboardAdapter.KeyItem item) {
        if (item.kind == KeyboardAdapter.KIND_ACTION && item.action == KeyboardAdapter.ACTION_BACKSPACE) {
            committed.setLength(0);
            cursorPos = -1;
            clearComposingState();
            resetTap();
            renderBox();
        } else if (item.kind == KeyboardAdapter.KIND_DIGIT) {
            // 长按数字键直接输入数字本身（短按是 T9 拼音 / 英文多击）：九键模式下 2–9 也能打出数字
            commitComposing(false);
            if (binding.keyword.length() < MAX_LEN) insertCommitted(item.text);
            resetTap();
        }
    }

    private void resetTap() {
        lastTapDigit = "";
        lastTapTime = 0;
    }

    private void resetTapIfNotDigit(KeyboardAdapter.KeyItem item) {
        if (item.kind != KeyboardAdapter.KIND_DIGIT) resetTap();
    }

    private void onLetter(String letter) {
        if (binding.keyword.length() >= MAX_LEN) return;
        if (englishMode) {
            // 英文模式：字母直接提交进搜索框，没有候选栏
            insertCommitted(letter);
            return;
        }
        composing.append(letter);
        renderBox();
    }

    private void onDigit(String digit) {
        if ("0".equals(digit)) {
            onSpaceKey();
            return;
        }
        if ("1".equals(digit)) {
            onPunctKey();
            return;
        }
        if (binding.keyword.length() >= MAX_LEN) return;
        if (englishMode) {
            onTapDigit(digit);
            return;
        }
        digits.append(digit);
        resegment();
        renderBox();
    }

    /** 英文九键多击：同键 1.2 秒内连按在该键字母间循环（如 2→A→B→C），换键或超时即确认当前字母再起新字母。 */
    private void onTapDigit(String digit) {
        int d;
        try {
            d = Integer.parseInt(digit);
        } catch (NumberFormatException e) {
            return;
        }
        if (d < 2 || d > 9) return;
        String letters = TAP_LETTERS[d];
        long now = android.os.SystemClock.uptimeMillis();
        if (digit.equals(lastTapDigit) && now - lastTapTime < TAP_TIMEOUT && committed.length() > 0) {
            char cur = committed.charAt(committed.length() - 1);
            int idx = letters.indexOf(cur);
            if (idx >= 0) {
                committed.setCharAt(committed.length() - 1, letters.charAt((idx + 1) % letters.length()));
                lastTapTime = now;
                renderBox();
                return;
            }
        }
        insertCommitted(String.valueOf(letters.charAt(0)));
        lastTapDigit = digit;
        lastTapTime = now;
    }

    private void onSymbol(String text) {
        commitComposing(false);
        if (binding.keyword.length() >= MAX_LEN) return;
        insertCommitted(text);
    }

    private void onAction(int action) {
        if (action == KeyboardAdapter.ACTION_BACKSPACE) {
            onBackspace();
        } else if (action == KeyboardAdapter.ACTION_SPACE) {
            onSpaceKey();
        } else if (action == KeyboardAdapter.ACTION_SYMBOL) {
            commitComposing(false);
            setMode(KeyboardAdapter.MODE_SYMBOL);
        } else if (action == KeyboardAdapter.ACTION_ABC) {
            // 回到进入符号盘前的模式：九键就回九键，全键就回全键，不写死全键
            setMode(customMode);
        } else if (action == KeyboardAdapter.ACTION_SEARCH) {
            callback.onSearch();
        }
    }

    /** 空格：中文正在拼写时确认首个联想词并搜索（与点首个建议相同；无建议时什么都不做）；
     * 其余情况（英文模式、已提交文本后）输入一个空格字符，空输入时不输前导空格。 */
    private void onSpaceKey() {
        if (!englishMode && isComposing()) {
            callback.onConfirmFirstAndSearch();
            return;
        }
        if (binding.keyword.length() >= MAX_LEN) return;
        Editable editable = binding.keyword.getText();
        if (editable == null || editable.length() == 0) return;
        insertCommitted(" ");
    }

    /** 1 键标点：先提交正在拼的拼音，再输入“.”；1 秒内再按则在 . , ? ! 间循环。循环改的是光标前一个标点。 */
    private void onPunctKey() {
        long now = System.currentTimeMillis();
        int pos = editPos();
        if (!lastPunct.isEmpty() && now - lastPunctTime < PUNCT_TIMEOUT && pos > 0
                && committed.charAt(pos - 1) == lastPunct.charAt(0)) {
            int idx = 0;
            for (int i = 0; i < PUNCTS.length; i++) if (PUNCTS[i] == lastPunct.charAt(0)) idx = i;
            char next = PUNCTS[(idx + 1) % PUNCTS.length];
            committed.setCharAt(pos - 1, next);
            lastPunct = String.valueOf(next);
        } else {
            commitComposing(true);
            if (binding.keyword.length() >= MAX_LEN) return;
            insertCommitted(".");
            lastPunct = ".";
        }
        lastPunctTime = now;
        renderBox();
    }

    /** 退格：先删拼写中的最后一个字母/数字，再删已提交的字符（光标前一个）。 */
    private void onBackspace() {
        if (isSystemMode()) {
            // 系统键盘模式：搜索框归系统输入法管，直接删输入法缓冲区光标前的字符；
            // 绝不能用 renderBox() 把陈旧的 committed 回写，那会覆盖系统输入法刚打的字
            Editable editable = binding.keyword.getText();
            int pos = binding.keyword.getSelectionStart();
            if (editable != null && pos > 0) editable.delete(pos - 1, pos);
            committed.setLength(0);
            committed.append(binding.keyword.getText().toString());
            cursorPos = -1;
            return;
        }
        resetTap();
        if (isComposing()) {
            if (adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
                if (digits.length() > 0) digits.deleteCharAt(digits.length() - 1);
                segBreaks.removeIf(b -> b >= digits.length());
                resegment();
            } else if (composing.length() > 0) {
                composing.deleteCharAt(composing.length() - 1);
            }
            renderBox();
            return;
        }
        deleteCommittedBeforeCursor();
    }

    public interface Callback {

        void onSearch();

        /** 当前候选词的第一个（用于完成/空格/标点时提交拼写）；没有候选或候选未对应当前输入时返回 null。 */
        String getFirstCandidate();

        /** 中文空格：确认首个联想词并搜索（与点首个建议相同；无建议时什么都不做）。 */
        void onConfirmFirstAndSearch();

        /** 键盘状态变了（模式/页签/中英文切换）：SearchActivity 刷新候选栏等。 */
        void onStateChanged();
    }
}
