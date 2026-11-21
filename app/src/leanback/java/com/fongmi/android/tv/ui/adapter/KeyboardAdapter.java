package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterKeyboardTextBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 当贝市场式搜索键盘：A–Z 平铺字母格（5 列 × 6 行，字母直接印在蓝底上，无键框；
 * 末行 Z 后面跟一个空格键）+ 数字盘（0–9，末尾跟一个空格键）。
 * <p>
 * 输入模型是“输拼音首字母、右侧实时出结果”：按键只向搜索框追加一个字母/数字，没有拼音拼写条、
 * 没有候选词提交、没有九键切音节。旧的拼音全键（QWERTY 错列）/ 拼音九键（T9 预测）/ 符号盘
 * 三套键表已全部删除，音节切分表也不再被搜索屏引用。
 */
public class KeyboardAdapter extends RecyclerView.Adapter<KeyboardAdapter.TextHolder> {

    /** 系统输入法接管：不显示自带键盘格。 */
    public static final int MODE_SYSTEM = 0;
    /** A–Z 字母格。 */
    public static final int MODE_LETTERS = 1;
    /** 0–9 数字盘。 */
    public static final int MODE_DIGITS = 2;

    public static final int KIND_LETTER = 0;
    public static final int KIND_DIGIT = 1;
    /** 空格键：字母格里跟在 Z 后面（第 27 格），数字盘里跟在 9 后面。 */
    public static final int KIND_SPACE = 2;

    /** 当贝字母格：5 列。 */
    private static final int SPAN_COUNT = 5;

    private static final List<KeyItem> LETTERS = new ArrayList<>();
    private static final List<KeyItem> DIGITS = new ArrayList<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++) LETTERS.add(KeyItem.letter(String.valueOf(c)));
        LETTERS.add(KeyItem.space());
        for (char c = '0'; c <= '9'; c++) DIGITS.add(KeyItem.digit(String.valueOf(c)));
        DIGITS.add(KeyItem.space());
    }

    private final OnClickListener listener;
    private final List<KeyItem> mItems;
    private int mode = MODE_LETTERS;

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
        return SPAN_COUNT;
    }

    public interface OnClickListener {

        void onKey(KeyItem item);
    }

    public void setMode(int mode) {
        this.mode = mode;
        refresh();
    }

    private void refresh() {
        mItems.clear();
        if (mode == MODE_LETTERS) mItems.addAll(LETTERS);
        else if (mode == MODE_DIGITS) mItems.addAll(DIGITS);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public TextHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TextHolder(AdapterKeyboardTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TextHolder holder, int position) {
        KeyItem item = mItems.get(position);
        // 空格键的显示文字走字符串资源（中文“空格”/英文“Space”），其余键直接用键面文字
        holder.binding.text.setText(item.kind == KIND_SPACE ? holder.itemView.getContext().getString(R.string.search_space) : item.label);
    }

    /** 一个按键：kind 决定输入方式（字母/数字），text 是追加进搜索框的内容。 */
    public static class KeyItem {

        public final int kind;
        public final String text;
        public final String label;

        private KeyItem(int kind, String text, String label) {
            this.kind = kind;
            this.text = text;
            this.label = label;
        }

        static KeyItem letter(String text) {
            return new KeyItem(KIND_LETTER, text, text);
        }

        static KeyItem digit(String text) {
            return new KeyItem(KIND_DIGIT, text, text);
        }

        /** 空格键：text 是英文/数字盘模式下追加进搜索框的空格字符；label 不用，
         * 显示文字在 onBindViewHolder 里取 R.string.search_space。 */
        static KeyItem space() {
            return new KeyItem(KIND_SPACE, " ", null);
        }
    }

    class TextHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterKeyboardTextBinding binding;

        TextHolder(@NonNull AdapterKeyboardTextBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return;
            listener.onKey(mItems.get(position));
        }
    }
}
