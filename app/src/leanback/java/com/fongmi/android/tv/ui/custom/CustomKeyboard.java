package com.fongmi.android.tv.ui.custom;

import android.text.Editable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.ui.adapter.SyllableAdapter;
import com.fongmi.android.tv.utils.PinyinTable;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机式搜索键盘：拼音全键（QWERTY 错列）/ 拼音九键（T9 预测）+ 符号盘。
 * <p>
 * 输入方式与手机输入法一致：键盘上方有拼写条，显示正在拼的拼音；候选词走现有的
 * getSuggest → iQIYI 建议链，点候选词把中文提交进搜索框（不立即搜索）；
 * 退格先删拼写中的最后一个字母/数字，再删已提交的字符；点“完成”/搜索先提交
 * 首选候选（没有则提交拼音原文）再搜索。
 * <p>
 * 九键是预测输入（T9）：每键只按一次，数字序列按 PinyinTable 切成拼音音节，
 * 多个音节匹配时点拼写条上的音节切换；不编造中文词库。
 * 笔画键盘已删除：MODE_STROKE 不再有任何分支，旧的模式值由 Setting 容错为拼音全键。
 * <p>
 * 状态模型：搜索框内容 = 已提交文本（committed）+ 正在拼的拼音（composingDisplay），
 * 光标默认在末尾，顶部的左右键可移动光标，退格/输入都在光标处操作；
 * 外部改动搜索框（语音、历史记录、初始关键字）时由 syncFromBox 同步。
 * 系统键盘模式下搜索框归系统输入法管，CustomKeyboard 不回写自己的 committed：
 * 进系统模式时先 syncFromBox 同步框内文本，退格直接删输入法缓冲区里的字符。
 */
public class CustomKeyboard implements KeyboardAdapter.OnClickListener, SyllableAdapter.OnClickListener {

    private static final int MAX_LEN = 20;
    private static final char[] PUNCTS = {'.', ',', '?', '!'};
    private static final long PUNCT_TIMEOUT = 1000;

    private final ActivitySearchBinding binding;
    private final Callback callback;
    private KeyboardAdapter adapter;
    private SyllableAdapter syllableAdapter;
    private SpaceItemDecoration decoration;
    private int customMode;

    /** 已提交进搜索框的文本（中文或字符）。 */
    private final StringBuilder committed = new StringBuilder();
    /** 全键正在拼的拼音字母。 */
    private final StringBuilder composing = new StringBuilder();
    /** 九键已按的数字序列。 */
    private final StringBuilder digits = new StringBuilder();
    /** 九键当前切出的音节。 */
    private List<String> syllables = new ArrayList<>();
    /** 九键未切出的尾部数字。 */
    private String remainder = "";
    /** 1 键标点循环：上次输入的标点与时间。 */
    private String lastPunct = "";
    private long lastPunctTime;
    /** 光标在已提交文本里的位置；-1 表示末尾（默认）。左右键移动光标，输入/退格在光标处操作。 */
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

    public void showSystemKeyboard() {
        Util.showKeyboard(binding.keyword);
    }

    public void hideSystemKeyboard() {
        Util.hideKeyboard(binding.keyword);
    }

    /** 拼写条是否可见（SearchActivity 焦点路由用）。 */
    public boolean isComposingBarVisible() {
        return binding.composingBar.getVisibility() == View.VISIBLE;
    }

    /** 是否正在拼写（拼写条有内容）。 */
    public boolean isComposing() {
        return composingDisplay().length() > 0;
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
        binding.composingBar.setItemAnimator(null);
        binding.composingBar.setHasFixedSize(false);
        binding.composingBar.setLayoutManager(new LinearLayoutManager(binding.composingBar.getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.composingBar.setAdapter(syllableAdapter = new SyllableAdapter(this));
        binding.tabFull.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_FULL));
        binding.tabNine.setOnClickListener(v -> setMode(KeyboardAdapter.MODE_PINYIN));
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
        if (mode == KeyboardAdapter.MODE_SYSTEM) {
            // 进系统模式先同步搜索框的当前内容：系统输入法接管后框内文本可能已变，
            // 不同步的话 committed 还是旧文本，之后按顶部 ⌫ 会把旧文本回写覆盖新输入
            syncFromBox();
            adapter.setMode(KeyboardAdapter.MODE_SYSTEM);
            binding.keyboardTabs.setVisibility(View.GONE);
            binding.composingBar.setVisibility(View.GONE);
            binding.keyboard.setVisibility(View.GONE);
            binding.systemIme.setText(R.string.keyboard_custom);
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
            binding.keyboardTabs.setVisibility(View.VISIBLE);
            binding.keyboard.setVisibility(View.VISIBLE);
            binding.systemIme.setText(R.string.keyboard_system);
            hideSystemKeyboard();
            updateTabs();
        }
    }

    private void updateDecoration() {
        if (decoration != null) binding.keyboard.removeItemDecoration(decoration);
        binding.keyboard.addItemDecoration(decoration = new SpaceItemDecoration(adapter.getSpanCount(), 8));
    }

    private void updateTabs() {
        binding.tabFull.setSelected(customMode == KeyboardAdapter.MODE_FULL);
        binding.tabNine.setSelected(customMode == KeyboardAdapter.MODE_PINYIN);
        binding.tabFull.setTextColor(ContextCompat.getColor(binding.tabFull.getContext(), customMode == KeyboardAdapter.MODE_FULL ? R.color.white : R.color.white_60));
        binding.tabNine.setTextColor(ContextCompat.getColor(binding.tabNine.getContext(), customMode == KeyboardAdapter.MODE_PINYIN ? R.color.white : R.color.white_60));
    }

    public void focusSelectedTab() {
        if (binding.keyboardTabs.getVisibility() == View.VISIBLE) {
            if (customMode == KeyboardAdapter.MODE_PINYIN) binding.tabNine.requestFocus();
            else binding.tabFull.requestFocus();
        } else {
            binding.iconRemote.requestFocus();
        }
    }

    public boolean focusComposingFirst() {
        // 全键模式的拼写条只有不可点的字母串，不占焦点位：直接跳过
        if (adapter == null || adapter.getMode() != KeyboardAdapter.MODE_PINYIN) return false;
        if (!isComposingBarVisible()) return false;
        View child = binding.composingBar.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    public boolean focusComposingNearest(int targetLeft) {
        // 全键模式的拼写条只有不可点的字母串，不占焦点位：直接跳过
        if (adapter == null || adapter.getMode() != KeyboardAdapter.MODE_PINYIN) return false;
        if (!isComposingBarVisible()) return false;
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < binding.composingBar.getChildCount(); i++) {
            View child = binding.composingBar.getChildAt(i);
            if (child == null || !child.isFocusable()) continue;
            int dist = Math.abs(child.getLeft() - targetLeft);
            if (dist < minDist) {
                minDist = dist;
                nearest = child;
            }
        }
        if (nearest == null) return false;
        nearest.requestFocus();
        return true;
    }

    /** 正在拼的拼音在搜索框里的显示：全键是字母串，九键是切出的音节连写 + 未切出的尾部数字。 */
    private String composingDisplay() {
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
            StringBuilder sb = new StringBuilder();
            for (String s : syllables) sb.append(s);
            sb.append(remainder);
            return sb.toString();
        }
        return composing.toString();
    }

    private void renderBox() {
        String text = committed.toString() + composingDisplay();
        binding.keyword.setText(text);
        // 正在拼写时光标在拼写串末尾；否则光标停在移动后的位置（默认末尾）
        binding.keyword.setSelection(isComposing() ? text.length() : editPos());
    }

    /** 外部改动了搜索框（语音输入、历史记录、初始关键字、模式切换）：以框内文本为准，丢弃拼写状态。 */
    public void syncFromBox() {
        committed.setLength(0);
        committed.append(binding.keyword.getText().toString());
        cursorPos = -1;
        clearComposingState();
    }

    private void clearComposingState() {
        composing.setLength(0);
        digits.setLength(0);
        syllables.clear();
        remainder = "";
        updateComposingBar();
    }

    private void resegment() {
        PinyinTable.Segmentation seg = PinyinTable.segment(digits.toString());
        syllables = new ArrayList<>(seg.syllables);
        remainder = seg.remainder;
    }

    private void updateComposingBar() {
        List<SyllableAdapter.Item> items = new ArrayList<>();
        if (adapter != null && adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
            for (String s : syllables) items.add(new SyllableAdapter.Item(s, true, false));
            if (!remainder.isEmpty()) items.add(new SyllableAdapter.Item(remainder, false, true));
        } else if (composing.length() > 0) {
            items.add(new SyllableAdapter.Item(composing.toString(), false, false));
        }
        syllableAdapter.setItems(items);
        binding.composingBar.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 把正在拼的拼音提交为中文（首选候选，没有则提交拼音原文），拼写状态清空。提交到光标处。 */
    public void commitComposing() {
        if (!isComposing()) return;
        String candidate = callback.getFirstCandidate();
        String text = candidate != null && !candidate.isEmpty() ? candidate : composingDisplay();
        int pos = editPos();
        committed.insert(pos, text);
        cursorPos = pos + text.length();
        if (cursorPos >= committed.length()) cursorPos = -1;
        clearComposingState();
        renderBox();
    }

    /** 点候选词：用选中的中文替换正在拼的拼音，不立即搜索。提交到光标处。 */
    public void commitCandidate(String text) {
        if (!isComposing()) return;
        int pos = editPos();
        committed.insert(pos, text);
        cursorPos = pos + text.length();
        if (cursorPos >= committed.length()) cursorPos = -1;
        clearComposingState();
        renderBox();
    }

    @Override
    public void onKey(KeyboardAdapter.KeyItem item) {
        if (item.kind == KeyboardAdapter.KIND_SPACER) return;
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
            renderBox();
        } else if (item.kind == KeyboardAdapter.KIND_DIGIT) {
            // 长按数字键直接输入数字本身（短按是 T9 拼音）：九键模式下 2–9 也能打出数字
            if (isComposing()) commitComposing();
            if (binding.keyword.length() < MAX_LEN) insertCommitted(item.text);
        }
    }

    /** 点拼写条上的音节：在同编码的候选音节间切换。 */
    @Override
    public void onSyllableClick(int position) {
        if (adapter == null || adapter.getMode() != KeyboardAdapter.MODE_PINYIN) return;
        if (position < 0 || position >= syllables.size()) return;
        int start = 0;
        for (int i = 0; i < position; i++) start += PinyinTable.encode(syllables.get(i)).length();
        int len = PinyinTable.encode(syllables.get(position)).length();
        if (start + len > digits.length()) return;
        List<String> alts = PinyinTable.forDigits(digits.substring(start, start + len));
        if (alts.size() <= 1) return;
        int cur = alts.indexOf(syllables.get(position));
        syllables.set(position, alts.get((cur + 1) % alts.size()));
        renderBox();
        updateComposingBar();
    }

    private void onLetter(String letter) {
        if (binding.keyword.length() >= MAX_LEN) return;
        // 拼写串始终显示在框末尾：从中间光标处开始拼写时光标先回末尾，避免拼写串显示位置与提交位置不一致
        if (!isComposing()) cursorPos = -1;
        composing.append(letter);
        renderBox();
        updateComposingBar();
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
        // 同上：九键拼写串显示在框末尾，新起拼写时光标回末尾
        if (!isComposing()) cursorPos = -1;
        digits.append(digit);
        resegment();
        renderBox();
        updateComposingBar();
    }

    private void onSymbol(String text) {
        if (isComposing()) commitComposing();
        if (binding.keyword.length() >= MAX_LEN) return;
        insertCommitted(text);
    }

    private void onAction(int action) {
        if (action == KeyboardAdapter.ACTION_BACKSPACE) {
            onBackspace();
        } else if (action == KeyboardAdapter.ACTION_SPACE) {
            onSpaceKey();
        } else if (action == KeyboardAdapter.ACTION_SYMBOL) {
            if (isComposing()) commitComposing();
            setMode(KeyboardAdapter.MODE_SYMBOL);
        } else if (action == KeyboardAdapter.ACTION_ABC) {
            // 回到进入符号盘前的模式：九键就回九键，全键就回全键，不写死全键
            setMode(customMode);
        } else if (action == KeyboardAdapter.ACTION_SEARCH) {
            callback.onSearch();
        }
    }

    /** 空格：先提交正在拼的拼音（首选候选），再空一格。 */
    private void onSpaceKey() {
        if (isComposing()) commitComposing();
        if (binding.keyword.length() >= MAX_LEN) return;
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
            if (isComposing()) commitComposing();
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
        if (isComposing()) {
            if (adapter.getMode() == KeyboardAdapter.MODE_PINYIN) {
                if (digits.length() > 0) digits.deleteCharAt(digits.length() - 1);
                resegment();
            } else if (composing.length() > 0) {
                composing.deleteCharAt(composing.length() - 1);
            }
            renderBox();
            updateComposingBar();
            return;
        }
        deleteCommittedBeforeCursor();
    }

    private void onIconClick(int resId) {
        if (resId == R.drawable.ic_setting_home) callback.showDialog();
        else if (resId == R.drawable.ic_keyboard_remote) callback.onRemote();
        else if (resId == R.drawable.ic_keyboard_search) callback.onSearch();
        else if (resId == R.drawable.ic_keyboard_left) onMoveLeft();
        else if (resId == R.drawable.ic_keyboard_right) onMoveRight();
        else if (resId == R.drawable.ic_keyboard_back) onBackspace();
        else if (resId == R.drawable.ic_keyboard) onToggleKeyboard();
    }

    private void onToggleKeyboard() {
        if (isSystemMode()) setMode(customMode);
        else setMode(KeyboardAdapter.MODE_SYSTEM);
    }

    private void onMoveLeft() {
        if (isComposing()) commitComposing();
        int next = Math.max(0, editPos() - 1);
        cursorPos = next;
        binding.keyword.setSelection(next);
    }

    private void onMoveRight() {
        if (isComposing()) commitComposing();
        int next = Math.min(committed.length(), editPos() + 1);
        cursorPos = next >= committed.length() ? -1 : next;
        binding.keyword.setSelection(next);
    }

    private boolean onLongClick(int resId) {
        if (resId != R.drawable.ic_keyboard_back) return false;
        if (isSystemMode()) {
            // 系统模式：直接清空输入法缓冲区，不经过 committed
            binding.keyword.setText("");
            committed.setLength(0);
            cursorPos = -1;
            return true;
        }
        committed.setLength(0);
        cursorPos = -1;
        clearComposingState();
        renderBox();
        return true;
    }

    public interface Callback {

        void showDialog();

        void onRemote();

        void onSearch();

        /** 当前候选词的第一个（用于完成/空格/标点时提交拼写）；没有候选或只有热搜词时返回 null。 */
        String getFirstCandidate();
    }
}
