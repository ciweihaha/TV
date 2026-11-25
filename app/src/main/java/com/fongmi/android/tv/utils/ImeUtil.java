package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 系统输入法工具：影视 app 与 OrionTV 重写版统一走系统输入法（电视上当贝市场装的百度输入法电视版），
 * 自带九键/全键键盘已停用。
 * <p>
 * 没装系统输入法、或没设默认时不静默回落：弹引导，让用户去当贝市场装百度输入法电视版、
 * 在设置里设为默认。判断不依赖输入法包名：默认输入法为空就是没设；弹不出则按激活状态再判一次。
 */
public class ImeUtil {

    /** 当前默认输入法 id（Settings.Secure.DEFAULT_INPUT_METHOD），没设过就是空。 */
    public static String getDefaultImeId(Context context) {
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        } catch (Exception e) {
            return null;
        }
    }

    /** 有没有设默认输入法。 */
    public static boolean hasDefaultIme(Context context) {
        return !TextUtils.isEmpty(getDefaultImeId(context));
    }

    /** 系统输入法当前是否已激活到该输入框。 */
    public static boolean isActive(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        return imm != null && imm.isActive(view);
    }

    /** 引导：去当贝市场装百度输入法电视版、设为默认。调用方控制只弹一次。 */
    public static void showGuide(Activity activity) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.search_ime_guide_title)
                .setMessage(R.string.search_ime_guide_message)
                .setPositiveButton(R.string.search_ime_guide_settings, (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.search_ime_guide_dismiss, null)
                .show();
    }
}
