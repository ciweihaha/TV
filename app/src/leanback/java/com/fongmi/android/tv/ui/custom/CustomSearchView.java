package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

public class CustomSearchView extends CustomEditText {

    private Animation flicker;

    public CustomSearchView(@NonNull Context context) {
        super(context);
    }

    public CustomSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        flicker = ResUtil.getAnim(R.anim.flicker);
    }

    // 注意：这里曾经 override isFocused() 恒返回 true（想让光标一直闪），已删掉——它让搜索框永远
    // “看起来聚焦”：uiautomator dump 永远报 keyword focused=true（焦点实际已移到按键上也照样报），
    // 遥控导航看起来像锁死在输入框、无障碍也读错焦点。光标由框架按真实焦点显示，拼写文本一直在框里。
    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) startAnimation(flicker);
        else clearAnimation();
    }
}
