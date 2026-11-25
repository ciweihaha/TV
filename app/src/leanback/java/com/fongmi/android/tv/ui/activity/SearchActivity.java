package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.ImeUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

/**
 * 系统输入法搜索屏：左栏搜索框、右栏实时出结果。
 * <p>
 * 2026-11-25 输入法改版：影视 app 与 OrionTV 重写版统一用系统输入法（电视上当贝市场装的百度输入法
 * 电视版），自带九键/全键键盘面板已停用（CustomKeyboard / KeyboardAdapter / CandidateAdapter /
 * PinyinTable 及相关布局、字符串一并删除）。打字、选词都由系统输入法自己管；搜索框文本变化仍走
 * getWord() → getSuggest() iQIYI 建议链，右栏实时出建议/历史。进搜索页就弹系统输入法；没设默认
 * 输入法、或两次都弹不出来时弹引导（去当贝市场装百度输入法电视版、设为默认），不再回落自带键盘。
 * <p>
 * 焦点只有两个区：左栏搜索框/语音 ↔ 右栏列表（历史/建议）。搜索框 ↓ / → 直接进右栏首项；
 * 右栏首行 ↑ 回搜索框；末行 ↓ 吞掉；行首 ← 回搜索框。
 * 联想链复用旧的 getWord() → getSuggest() iQIYI 建议 + 300ms 防抖 + 三序号防过期（M22），一字未动。
 */
public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    /** 联想请求序号：每次输入变化 +1，只有最新一次请求的响应能进候选列表，慢到的过期响应直接丢弃。 */
    private int suggestSeq;
    /** 待执行的防抖任务（只有一个实例）：每次输入先取消上一个再发新的，
     * Activity 销毁时取消，避免销毁后任务仍执行、发起联想请求。 */
    private Runnable suggestTask;
    /** 输入变化防抖：300ms 内连续输入只发最后一次联想请求。 */
    private static final long SUGGEST_DEBOUNCE_MS = 300;
    /** 输入法缺失引导本页只弹一次。 */
    private boolean guideShown;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        updateResultCount();
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        suggestSeq++;
        int seq = suggestSeq;
        if (text.isEmpty()) {
            getHot(seq);
            return;
        }
        // 防抖：连续输入时只发最后一次请求，中间的请求连发都不发；
        // 任务是同一个字段持有的实例，销毁时能取消（以前每次新建 lambda，App.post 里的
        // removeCallbacks 根本取消不到上一个，销毁后任务仍会执行并发起联想请求）
        if (suggestTask != null) App.removeCallbacks(suggestTask);
        suggestTask = () -> {
            if (isDestroyed() || isFinishing() || seq != suggestSeq) return;
            getSuggest(text, seq);
        };
        App.post(suggestTask, SUGGEST_DEBOUNCE_MS);
    }

    private void getHot(int seq) {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.setItems(Word.objectFrom(Setting.getHot()).getData());
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getCallback(true, seq));
    }

    private void getSuggest(String text, int seq) {
        mBinding.word.setText(R.string.search_suggest);
        // 分词符 ' 只分隔拼写显示，不进联想查询
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(ZhuToPin.get(text.replace("'", "")))).enqueue(getCallback(false, seq));
    }

    private Callback getCallback(boolean hot, int seq) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setAdapter(result, hot, seq));
            }
        };
    }

    private void setAdapter(String result, boolean save, int seq) {
        // 销毁后到达的响应直接丢弃：不再碰已销毁 Activity 的视图
        if (isDestroyed() || isFinishing()) return;
        // 过期响应丢弃：用户已经又输了字，这份候选是旧输入的，绝不能覆盖新候选
        if (seq != suggestSeq) return;
        if (!save && empty()) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
        updateResultCount();
    }

    /** 右上角“相关搜索结果：N个”（历史 + 建议两栏的条数之和）。 */
    private void updateResultCount() {
        int count = 0;
        if (mWordAdapter != null) count += mWordAdapter.getItemCount();
        if (mRecordAdapter != null) count += mRecordAdapter.getItemCount();
        mBinding.resultCount.setText(getString(R.string.search_result_count, count));
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onRecordClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        updateResultCount();
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /**
     * 焦点路由（两个区，左栏搜索框/语音 ↔ 右栏列表）：
     * 搜索框 ↓ / → 进右栏首项（有历史时历史首项，否则建议首项）；语音键 ↓ / → 同样进右栏。
     * 右栏首行 ↑ 回搜索框；末行 ↓ 吞掉；行首 ← 回搜索框；行尾 → 吞掉。
     */
    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.mic) return handleMicKey(event);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inRecord != null) return handleRightListKey(event, inRecord, true);
        if (inWord != null) return handleRightListKey(event, inWord, false);
        return false;
    }

    /** 右栏列表首项：有历史时落到历史首项，否则落到建议首项。 */
    private boolean focusRightList() {
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) return focusRightList();
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        return focusRightList();
    }

    private boolean handleMicKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) return focusRightList();
        if (KeyUtil.isRightKey(event)) return focusRightList();
        return false;
    }

    private boolean handleRightListKey(KeyEvent event, View item, boolean isRecord) {
        RecyclerView rv = isRecord ? mBinding.recordRecycler : mBinding.wordRecycler;
        if (KeyUtil.isUpKey(event) && isFirstRow(rv, item)) {
            if (!isRecord && mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            // 右栏首行按上直接回搜索框
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(rv, item)) {
            if (isRecord) return focusFirst(mBinding.wordRecycler);
            // 建议列表末行下方什么都没有，吞掉
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(rv, item)) {
            // 左移回左栏：落回搜索框
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event) && isLastInRow(rv, item)) return true;
        return false;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        if (rv.getChildCount() == 0) return false;
        return item.getTop() == rv.getChildAt(0).getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        if (rv.getChildCount() == 0) return false;
        return item.getTop() == rv.getChildAt(rv.getChildCount() - 1).getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    /** 进搜索页就弹系统输入法；没设默认输入法、或两次都弹不出来时弹引导，不回落自带键盘。 */
    private void showSystemIme() {
        if (isDestroyed() || isFinishing()) return;
        if (!ImeUtil.hasDefaultIme(this)) {
            if (!guideShown) {
                guideShown = true;
                ImeUtil.showGuide(this);
            }
            return;
        }
        Util.showKeyboard(mBinding.keyword);
        // 系统输入法有时第一次弹不出来（EMUI/Leanback 下 showSoftInput 可能被拒）：600ms 后若仍未激活再强制弹一次；
        // 1200ms 后仍未激活就弹引导，不再静默重试、也不回落自带键盘。
        mBinding.keyword.postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && !imm.isActive(mBinding.keyword)) {
                imm.showSoftInput(mBinding.keyword, InputMethodManager.SHOW_FORCED);
            }
        }, 600);
        mBinding.keyword.postDelayed(() -> {
            if (isDestroyed() || isFinishing() || guideShown) return;
            if (!mBinding.keyword.getText().toString().isEmpty()) return;
            if (!ImeUtil.isActive(mBinding.keyword)) {
                guideShown = true;
                ImeUtil.showGuide(this);
            }
        }, 1200);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
        showSystemIme();
    }

    @Override
    protected void onDestroy() {
        // 取消还没执行的防抖任务：否则销毁 300ms 后它仍会发起联想请求，
        // 响应回来再碰已销毁的视图
        if (suggestTask != null) App.removeCallbacks(suggestTask);
        super.onDestroy();
        mBinding.mic.destroy();
    }
}
