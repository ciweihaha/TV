package com.fongmi.android.tv.ui.dialog;

import org.greenrobot.eventbus.EventBus;

public abstract class BaseEventDialog extends BaseAlertDialog {

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }
}
