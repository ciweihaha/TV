package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterKeyboard9keyBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardIconBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class KeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_PINYIN = 1;

    private final List<Integer> icons = Arrays.asList(R.drawable.ic_keyboard_remote, R.drawable.ic_keyboard_left, R.drawable.ic_keyboard_right, R.drawable.ic_keyboard_back, R.drawable.ic_keyboard_search, R.drawable.ic_keyboard, R.drawable.ic_setting_home);
    // 拼音 9 键：按键数字 + 键面字母，CustomKeyboard 按多击方式循环取字母，数字排在每组末尾
    private final List<Key> keys = Arrays.asList(new Key("1", ".,?!"), new Key("2", "ABC"), new Key("3", "DEF"), new Key("4", "GHI"), new Key("5", "JKL"), new Key("6", "MNO"), new Key("7", "PQRS"), new Key("8", "TUV"), new Key("9", "WXYZ"), new Key("0", "空格"));
    private final OnClickListener listener;
    private final List<Object> mItems;
    private int mode = MODE_PINYIN;

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

    public interface OnClickListener {

        void onKeyClick(String digit);

        void onIconClick(int resId);

        boolean onLongClick(int resId);
    }

    public void toggle() {
        mode = mode == MODE_SYSTEM ? MODE_PINYIN : MODE_SYSTEM;
        refresh();
    }

    private void refresh() {
        mItems.clear();
        mItems.addAll(icons);
        if (mode == MODE_PINYIN) mItems.addAll(keys);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof Key ? 0 : 1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) return new TextHolder(AdapterKeyboard9keyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        else return new IconHolder(AdapterKeyboardIconBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case 0:
                TextHolder text = (TextHolder) holder;
                text.binding.text.setText(((Key) mItems.get(position)).label);
                break;
            case 1:
                IconHolder icon = (IconHolder) holder;
                icon.binding.icon.setImageResource((int) mItems.get(position));
                break;
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

        private final AdapterKeyboard9keyBinding binding;

        TextHolder(@NonNull AdapterKeyboard9keyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            listener.onKeyClick(((Key) mItems.get(getLayoutPosition())).digit);
        }
    }

    class IconHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final AdapterKeyboardIconBinding binding;

        IconHolder(@NonNull AdapterKeyboardIconBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            listener.onIconClick((int) mItems.get(getLayoutPosition()));
        }

        @Override
        public boolean onLongClick(View view) {
            return listener.onLongClick((int) mItems.get(getLayoutPosition()));
        }
    }
}
