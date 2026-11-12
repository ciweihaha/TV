package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterKeyboardIconBinding;
import com.fongmi.android.tv.databinding.AdapterKeyboardTextBinding;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class KeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_EN = 1;
    public static final int MODE_ZHUYIN = 2;

    private final List<Integer> icons = Arrays.asList(R.drawable.ic_keyboard_remote, R.drawable.ic_keyboard_left, R.drawable.ic_keyboard_right, R.drawable.ic_keyboard_back, R.drawable.ic_keyboard_search, R.drawable.ic_keyboard, R.drawable.ic_setting_home);
    private final List<String> enList = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    private final List<String> twList = Arrays.asList("ㄅ", "ㄆ", "ㄇ", "ㄈ", "ㄉ", "ㄊ", "ㄋ", "ㄌ", "ㄍ", "ㄎ", "ㄏ", "ㄐ", "ㄑ", "ㄒ", "ㄓ", "ㄔ", "ㄕ", "ㄖ", "ㄗ", "ㄘ", "ㄙ", "ㄧ", "ㄨ", "ㄩ", "ㄚ", "ㄛ", "ㄜ", "ㄝ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    private final OnClickListener listener;
    private final List<Object> mItems;
    private int mode = MODE_SYSTEM;

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

        void onTextClick(String text);

        void onIconClick(int resId);

        boolean onLongClick(int resId);
    }

    public void toggle() {
        if (mode == MODE_SYSTEM) {
            mode = Setting.isZhuyin() ? MODE_ZHUYIN : MODE_EN;
        } else if (mode == MODE_EN) {
            mode = MODE_ZHUYIN;
            Setting.putZhuyin(true);
        } else {
            mode = MODE_SYSTEM;
            Setting.putZhuyin(false);
        }
        refresh();
    }

    private void refresh() {
        mItems.clear();
        mItems.addAll(icons);
        if (mode == MODE_EN) mItems.addAll(enList);
        else if (mode == MODE_ZHUYIN) mItems.addAll(twList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof String ? 0 : 1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) return new TextHolder(AdapterKeyboardTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        else return new IconHolder(AdapterKeyboardIconBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case 0:
                TextHolder text = (TextHolder) holder;
                text.binding.text.setText(mItems.get(position).toString());
                break;
            case 1:
                IconHolder icon = (IconHolder) holder;
                icon.binding.icon.setImageResource((int) mItems.get(position));
                break;
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
