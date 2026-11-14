package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterComposingSyllableBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼写条：显示正在拼的拼音。拼音全键显示一格字母串；拼音九键显示切出的音节，
 * 点一个音节可在同编码的候选音节间切换；未切出的尾部数字以灰色显示、不可点。
 */
public class SyllableAdapter extends RecyclerView.Adapter<SyllableAdapter.ViewHolder> {

    public static class Item {

        public final String text;
        public final boolean clickable;
        public final boolean remainder;

        public Item(String text, boolean clickable, boolean remainder) {
            this.text = text;
            this.clickable = clickable;
            this.remainder = remainder;
        }
    }

    private final List<Item> mItems = new ArrayList<>();
    private final OnClickListener listener;

    public interface OnClickListener {

        void onSyllableClick(int position);
    }

    public SyllableAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterComposingSyllableBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = mItems.get(position);
        holder.binding.text.setText(item.text);
        holder.binding.text.setFocusable(item.clickable);
        holder.binding.text.setClickable(item.clickable);
        holder.binding.text.setTextColor(ContextCompat.getColor(holder.binding.text.getContext(), item.remainder ? R.color.white_60 : R.color.white));
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterComposingSyllableBinding binding;

        ViewHolder(@NonNull AdapterComposingSyllableBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getLayoutPosition();
            if (position >= 0 && position < mItems.size() && mItems.get(position).clickable) listener.onSyllableClick(position);
        }
    }
}
