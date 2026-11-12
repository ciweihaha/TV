package com.fongmi.android.tv.ui.dialog;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogDohBinding;
import com.fongmi.android.tv.ui.adapter.DohAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.github.catvod.bean.Doh;

public class DohDialog extends BaseAlertDialog implements DohAdapter.OnClickListener {

    private DialogDohBinding binding;
    private DohAdapter adapter;
    private int index;

    public static DohDialog create() {
        return new DohDialog();
    }

    public DohDialog index(int index) {
        this.index = index;
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogDohBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        adapter = new DohAdapter(this);
        adapter.setSelect(index);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelect()));
    }

    @Override
    public void onItemClick(Doh item) {
        ((Listener) requireActivity()).setDoh(item);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.4f);
    }

    public interface Listener {

        void setDoh(Doh doh);
    }
}