package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterKeyboard9keyBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardSpacerBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardTextBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度输入法电视版式搜索键盘按键表。
 * <p>
 * 拼音全键：QWERTY 三行错列（qwertyuiop / asdfghjkl / zxcvbnm，第二行缩进半键、第三行缩进一键，
 * 错列用不可聚焦的占位键实现）；空格在第三行中间（V 与 B 之间，占两键宽）；删除/完成不在格子里，
 * 在键盘右侧列（见 activity_search.xml）。
 * 符号盘：数字 + 常用标点，ABC 返回字母盘（不记入模式记忆）。
 * 拼音九键：手机 3×4 九宫格（1 标点 / 2 ABC / 3 DEF / 4 GHI / 5 JKL / 6 MNO / 7 PQRS / 8 TUV / 9 WXYZ /
 * * / 0 空格 / #），九键是预测输入（T9），按键只按一次，不再多击循环（英文九键除外：见 CustomKeyboard 多击）。
 * 笔画键盘没有：页签只有拼音全键 / 拼音九键。
 */
public class KeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** 系统输入法接管：不显示自带键盘格。 */
    public static final int MODE_SYSTEM = 0;
    /** 拼音全键（QWERTY）。 */
    public static final int MODE_FULL = 1;
    /** 拼音九键（T9）。 */
    public static final int MODE_PINYIN = 2;
    /** 废弃：笔画键盘已删除，旧的模式值 3 由 Setting 容错为拼音全键。 */
    public static final int MODE_STROKE = 3;
    /** 符号盘：临时布局，不写入 Setting。 */
    public static final int MODE_SYMBOL = 4;

    public static final int KIND_LETTER = 0;
    public static final int KIND_DIGIT = 1;
    public static final int KIND_SYMBOL = 2;
    public static final int KIND_ACTION = 3;
    public static final int KIND_SPACER = 4;

    public static final int ACTION_BACKSPACE = 0;
    public static final int ACTION_SPACE = 1;
    public static final int ACTION_SYMBOL = 2;
    public static final int ACTION_ABC = 3;
    public static final int ACTION_SEARCH = 4;

    /** 占位键的 View tag，SearchActivity 的焦点边缘判断用它跳过占位键。 */
    public static final String TAG_SPACER = "spacer";

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_LARGE = 1;
    private static final int TYPE_SPACER = 2;

    /** 全键/符号盘：半键为单位，共 20 列；九键：3 列。 */
    private static final int SPAN_FULL = 20;
    private static final int SPAN_NINE = 3;

    private static final List<KeyItem> FULL = new ArrayList<>();
    private static final List<KeyItem> SYMBOL = new ArrayList<>();
    private static final List<KeyItem> NINE = new ArrayList<>();

    static {
        // 拼音全键：三行错列；空格在第三行中间（V 与 B 之间，占两键宽）
        for (String s : new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"})
            FULL.add(KeyItem.letter(s, 2));
        FULL.add(KeyItem.spacer(1));
        for (String s : new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"})
            FULL.add(KeyItem.letter(s, 2));
        FULL.add(KeyItem.spacer(1));
        FULL.add(KeyItem.spacer(2));
        for (String s : new String[]{"z", "x", "c", "v"})
            FULL.add(KeyItem.letter(s, 2));
        FULL.add(KeyItem.action("", ACTION_SPACE, 4));
        for (String s : new String[]{"b", "n", "m"})
            FULL.add(KeyItem.letter(s, 2));
        // 符号盘：数字 + 标点 + ABC / 空格 / 退格
        for (String s : new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"})
            SYMBOL.add(KeyItem.symbol(s, 2));
        for (String s : new String[]{".", ",", "?", "!", ":", ";", "'", "\"", "-", "_"})
            SYMBOL.add(KeyItem.symbol(s, 2));
        SYMBOL.add(KeyItem.spacer(1));
        for (String s : new String[]{"@", "#", "$", "%", "&", "*", "+", "=", "/"})
            SYMBOL.add(KeyItem.symbol(s, 2));
        SYMBOL.add(KeyItem.spacer(1));
        SYMBOL.add(KeyItem.spacer(2));
        SYMBOL.add(KeyItem.action("ABC", ACTION_ABC, 3));
        SYMBOL.add(KeyItem.action("", ACTION_SPACE, 10));
        SYMBOL.add(KeyItem.action("⌫", ACTION_BACKSPACE, 3));
        SYMBOL.add(KeyItem.spacer(2));
        // 拼音九键：3×4 九宫格；1 标点循环，0 空格，* # 直接输符号
        NINE.add(KeyItem.digit("1", ",.?!"));
        NINE.add(KeyItem.digit("2", "ABC"));
        NINE.add(KeyItem.digit("3", "DEF"));
        NINE.add(KeyItem.digit("4", "GHI"));
        NINE.add(KeyItem.digit("5", "JKL"));
        NINE.add(KeyItem.digit("6", "MNO"));
        NINE.add(KeyItem.digit("7", "PQRS"));
        NINE.add(KeyItem.digit("8", "TUV"));
        NINE.add(KeyItem.digit("9", "WXYZ"));
        NINE.add(KeyItem.symbol("*", 1));
        NINE.add(KeyItem.digit("0", "空格"));
        NINE.add(KeyItem.symbol("#", 1));
    }

    private final OnClickListener listener;
    private final List<KeyItem> mItems;
    private int mode = MODE_FULL;

    public KeyboardAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.listener = listener;
        refresh();
    }

    public int getMode() {
        return mode;
    }

    public boolean isSystemMode() {
        return mode == MODE_SYSTEM;
    }

    public int getSpanCount() {
        if (mode == MODE_PINYIN) return SPAN_NINE;
        return SPAN_FULL;
    }

    public int getSpanSize(int position) {
        return position >= 0 && position < mItems.size() ? mItems.get(position).span : 1;
    }

    public interface OnClickListener {

        void onKey(KeyItem item);

        void onKeyLongPress(KeyItem item);
    }

    public void setMode(int mode) {
        this.mode = mode;
        refresh();
    }

    private void refresh() {
        mItems.clear();
        if (mode == MODE_FULL) mItems.addAll(FULL);
        else if (mode == MODE_SYMBOL) mItems.addAll(SYMBOL);
        else if (mode == MODE_PINYIN) mItems.addAll(NINE);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        KeyItem item = mItems.get(position);
        if (item.kind == KIND_SPACER) return TYPE_SPACER;
        if (item.kind == KIND_DIGIT) return TYPE_LARGE;
        return TYPE_TEXT;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SPACER) return new SpacerHolder(AdapterKeyboardSpacerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (viewType == TYPE_LARGE) return new LargeHolder(AdapterKeyboard9keyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        return new TextHolder(AdapterKeyboardTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        KeyItem item = mItems.get(position);
        if (holder instanceof TextHolder) {
            // 空格键的显示文字走字符串资源（中文“空格”/英文“Space”），其余键直接用键面文字
            ((TextHolder) holder).binding.text.setText(item.action == ACTION_SPACE ? holder.itemView.getContext().getString(R.string.search_space) : item.label);
        } else if (holder instanceof LargeHolder) {
            ((LargeHolder) holder).binding.text.setText(item.label);
        }
    }

    /** 一个按键：kind 决定输入方式，span 决定在网格里占几列。 */
    public static class KeyItem {

        public final int kind;
        public final String text;
        public final String label;
        public final int span;
        public final int action;

        private KeyItem(int kind, String text, String label, int span, int action) {
            this.kind = kind;
            this.text = text;
            this.label = label;
            this.span = span;
            this.action = action;
        }

        static KeyItem letter(String text, int span) {
            return new KeyItem(KIND_LETTER, text, text, span, -1);
        }

        static KeyItem symbol(String text, int span) {
            return new KeyItem(KIND_SYMBOL, text, text, span, -1);
        }

        static KeyItem digit(String digit, String letters) {
            return digit(digit, letters, 1);
        }

        static KeyItem digit(String digit, String letters, int span) {
            return new KeyItem(KIND_DIGIT, digit, digit + " " + letters, span, -1);
        }

        static KeyItem action(String label, int action, int span) {
            return new KeyItem(KIND_ACTION, label, label, span, action);
        }

        static KeyItem spacer(int span) {
            return new KeyItem(KIND_SPACER, "", "", span, -1);
        }
    }

    class TextHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterKeyboardTextBinding binding;

        TextHolder(@NonNull AdapterKeyboardTextBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return;
            listener.onKey(mItems.get(position));
        }

        @Override
        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return false;
            listener.onKeyLongPress(mItems.get(position));
            return true;
        }
    }

    class LargeHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterKeyboard9keyBinding binding;

        LargeHolder(@NonNull AdapterKeyboard9keyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return;
            listener.onKey(mItems.get(position));
        }

        @Override
        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return false;
            listener.onKeyLongPress(mItems.get(position));
            return true;
        }
    }

    static class SpacerHolder extends RecyclerView.ViewHolder {

        SpacerHolder(@NonNull AdapterKeyboardSpacerBinding binding) {
            super(binding.getRoot());
            itemView.setTag(TAG_SPACER);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }
}
