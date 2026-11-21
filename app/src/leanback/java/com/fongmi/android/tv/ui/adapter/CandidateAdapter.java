package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterCandidateBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度输入法电视版候选栏：横向一排候选词（来自现有的联想接口建议词），
 * 首选（第 0 个）蓝底白字；遥控器左右移动选词，点按/确认键选词上屏并搜索。
 */
public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.Holder> {

    private final OnClickListener listener;
    private final List<String> mItems = new ArrayList<>();
    private int selected = 0;

    public CandidateAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onCandidateClick(String text);

        void onCandidateFocus(int position);
    }

    public void setItems(List<String> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        selected = 0;
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size() || position == selected) return;
        int old = selected;
        selected = position;
        notifyItemChanged(old);
        notifyItemChanged(selected);
    }

    public int getSelected() {
        return selected;
    }

    public String getItem(int position) {
        if (position < 0 || position >= mItems.size()) return null;
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AdapterCandidateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.binding.text.setText(mItems.get(position));
        boolean isSelected = position == selected;
        holder.binding.text.setBackgroundResource(isSelected ? R.drawable.shape_candidate_selected : R.drawable.shape_candidate_normal);
        holder.binding.text.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), isSelected ? R.color.white : R.color.white_90));
    }

    class Holder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnFocusChangeListener {

        private final AdapterCandidateBinding binding;

        Holder(@NonNull AdapterCandidateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            itemView.setOnFocusChangeListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return;
            setSelected(position);
            listener.onCandidateClick(mItems.get(position));
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (!hasFocus) return;
            int position = getLayoutPosition();
            if (position == RecyclerView.NO_POSITION || position < 0 || position >= mItems.size()) return;
            setSelected(position);
            listener.onCandidateFocus(position);
        }
    }
}
