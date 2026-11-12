package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public record StateEvent(Type type) {

    public static void empty() {
        EventBus.getDefault().post(new StateEvent(Type.EMPTY));
    }

    public enum Type {
        EMPTY, PROGRESS, CONTENT
    }
}
