package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterKeyboard9keyBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardTextBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class KeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_FULL = 1;
    public static final int MODE_PINYIN = 2;
    public static final int MODE_STROKE = 3;

    private static final int TYPE_SMALL = 0;
    private static final int TYPE_LARGE = 1;

    // 拼音全键 QWERTY：三行 10 / 10 / 9（末行空格占两格），与照片的 26 键布局一致，按一下输入一个字母
    private final List<String> fullList = Arrays.asList(
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l", ".",
            "z", "x", "c", "v", " ", "b", "n", "m", ".");
    // 拼音 9 键：按键数字 + 键面字母，CustomKeyboard 按多击方式循环取字母，数字排在每组末尾
    private final List<Key> keys = Arrays.asList(new Key("1", ".,?!"), new Key("2", "ABC"), new Key("3", "DEF"), new Key("4", "GHI"), new Key("5", "JKL"), new Key("6", "MNO"), new Key("7", "PQRS"), new Key("8", "TUV"), new Key("9", "WXYZ"), new Key("0", "空格"));
    // 笔画键盘：原注音键表（b0ff225 时的 twList），按一下输入一个注音符号，联想走 ZhuToPin 转拼音
    private final List<String> strokeList = Arrays.asList("ㄅ", "ㄆ", "ㄇ", "ㄈ", "ㄉ", "ㄊ", "ㄋ", "ㄌ", "ㄍ", "ㄎ", "ㄏ", "ㄐ", "ㄑ", "ㄒ", "ㄓ", "ㄔ", "ㄕ", "ㄖ", "ㄗ", "ㄘ", "ㄙ", "ㄧ", "ㄨ", "ㄩ", "ㄚ", "ㄛ", "ㄜ", "ㄝ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    private final OnClickListener listener;
    private final List<Object> mItems;
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
        if (mode == MODE_FULL) return 10;
        if (mode == MODE_STROKE) return 7;
        return 3;
    }

    public boolean isWideKey(int position) {
        return mode == MODE_FULL && position >= 0 && position < mItems.size() && " ".equals(mItems.get(position));
    }

    public interface OnClickListener {

        void onTextClick(String text);

        void onKeyClick(String digit);
    }

    public void setMode(int mode) {
        this.mode = mode;
        refresh();
    }

    private void refresh() {
        mItems.clear();
        if (mode == MODE_FULL) mItems.addAll(fullList);
        else if (mode == MODE_PINYIN) mItems.addAll(keys);
        else if (mode == MODE_STROKE) mItems.addAll(strokeList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (mItems.get(position) instanceof Key) return TYPE_LARGE;
        return mode == MODE_FULL ? TYPE_SMALL : TYPE_LARGE;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SMALL) return new TextHolder(AdapterKeyboardTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        else return new LargeHolder(AdapterKeyboard9keyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = mItems.get(position);
        if (holder instanceof TextHolder) {
            TextHolder text = (TextHolder) holder;
            String value = item.toString();
            text.binding.text.setText(" ".equals(value) ? "空格" : value);
        } else if (holder instanceof LargeHolder) {
            LargeHolder large = (LargeHolder) holder;
            if (item instanceof Key) large.binding.text.setText(((Key) item).label);
            else large.binding.text.setText(item.toString());
        }
    }

    static class Key {

        final String digit;
        final String label;

        Key(String digit, String letters) {
            this.digit = digit;
            this.label = digit + " " + letters;
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
            listener.onTextClick(mItems.get(getLayoutPosition()).toString());
        }
    }

    class LargeHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterKeyboard9keyBinding binding;

        LargeHolder(@NonNull AdapterKeyboard9keyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            Object item = mItems.get(getLayoutPosition());
            if (item instanceof Key) listener.onKeyClick(((Key) item).digit);
            else listener.onTextClick(item.toString());
        }
    }
}
