package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.dialog.LocalConnectDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
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

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private ActivitySearchBinding mBinding;
    private CustomKeyboard mKeyboard;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    /** 联想请求序号：每次输入变化 +1，只有最新一次请求的响应能进候选列表，慢到的过期响应直接丢弃。 */
    private int suggestSeq;
    /** 已实际发出的联想请求序号（防抖后才发出）。 */
    private int suggestRequestedSeq;
    /** 已应用到候选列表的联想响应序号。 */
    private int suggestAppliedSeq;
    /** 待执行的防抖任务（只有一个实例）：每次输入先取消上一个再发新的，
     * Activity 销毁时取消，避免销毁后任务仍执行、发起联想请求。 */
    private Runnable suggestTask;
    /** 输入变化防抖：300ms 内连续输入只发最后一次联想请求。 */
    private static final long SUGGEST_DEBOUNCE_MS = 300;

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
        mKeyboard = CustomKeyboard.init(this, mBinding);
        setRecyclerView();
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
        if (mKeyboard != null) mKeyboard.syncFromBox();
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
        suggestRequestedSeq = seq;
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.setItems(Word.objectFrom(Setting.getHot()).getData());
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(getCallback(true, seq));
    }

    private void getSuggest(String text, int seq) {
        suggestRequestedSeq = seq;
        mBinding.word.setText(R.string.search_suggest);
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(ZhuToPin.get(text))).enqueue(getCallback(false, seq));
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
        // 过期响应丢弃：用户已经又输了字，这份候选是旧输入的，绝不能覆盖新候选，
        // 否则 Done/空格/标点会把错词提交进搜索框
        if (seq != suggestSeq) return;
        suggestAppliedSeq = seq;
        if (!save && empty()) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
    }

    @Override
    public void onItemClick(String text) {
        // 正在拼写时点候选词：把中文提交进搜索框，不立即搜索（手机输入法的行为）
        if (mKeyboard != null && mKeyboard.isComposing()) {
            mKeyboard.commitCandidate(text);
            return;
        }
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
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        // 正在拼写时先提交（首选候选，没有则提交拼音原文），再搜索
        if (mKeyboard != null) mKeyboard.commitComposing();
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    @Override
    public void onRemote() {
        LocalConnectDialog.show(this, "search");
    }

    @Override
    public String getFirstCandidate() {
        // 只有当前显示的是联想候选（不是热搜词）时才返回首个候选，供完成/空格/标点时提交拼写
        if (mWordAdapter == null || mWordAdapter.getItemCount() == 0) return null;
        if (!mBinding.word.getText().toString().equals(getString(R.string.search_suggest))) return null;
        // 候选必须与当前输入对应：新输入还没发出请求、或请求响应还没回来时，
        // 列表里还是上一轮输入的候选，返回 null 让调用方提交拼音原文，绝不提交错词
        if (suggestSeq != suggestRequestedSeq || suggestSeq != suggestAppliedSeq) return null;
        return mWordAdapter.getItem(0).getTitle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.mic) return handleMicKey(event);
        if (current == mBinding.tabFull || current == mBinding.tabNine) return handleTabKey(event, current);
        if (current == mBinding.iconRemote || current == mBinding.iconLeft || current == mBinding.iconRight || current == mBinding.iconBack || current == mBinding.iconSearch || current == mBinding.iconKeyboard || current == mBinding.iconHome) return handleIconRowKey(event, current);
        if (current == mBinding.systemIme || current == mBinding.done) return handleBottomKey(event, current);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inComposing = mBinding.composingBar.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inComposing != null) return handleComposingKey(event, inComposing);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private boolean isSpacer(View view) {
        return view != null && KeyboardAdapter.TAG_SPACER.equals(view.getTag());
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop && !isSpacer(child)) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (!isSpacer(child) && child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (!isSpacer(child) && child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            mKeyboard.focusSelectedTab();
            return true;
        }
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleMicKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            mKeyboard.focusSelectedTab();
            return true;
        }
        return false;
    }

    private boolean handleTabKey(KeyEvent event, View tab) {
        if (KeyUtil.isUpKey(event)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (mKeyboard.focusComposingFirst()) return true;
            return focusFirst(mBinding.keyboard);
        }
        if (KeyUtil.isLeftKey(event) && tab == mBinding.tabFull) return true;
        return false;
    }

    private boolean handleIconRowKey(KeyEvent event, View icon) {
        if (KeyUtil.isUpKey(event)) {
            mKeyboard.focusSelectedTab();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (mKeyboard.focusComposingFirst()) return true;
            if (mBinding.keyboard.getVisibility() == View.VISIBLE) return focusFirst(mBinding.keyboard);
            mBinding.systemIme.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && icon == mBinding.iconRemote) return true;
        return false;
    }

    private boolean handleBottomKey(KeyEvent event, View button) {
        if (KeyUtil.isUpKey(event)) {
            if (mBinding.keyboard.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.keyboard, button.getLeft());
                if (child != null) {
                    child.requestFocus();
                    return true;
                }
            } else {
                mBinding.iconRemote.requestFocus();
                return true;
            }
        }
        if (KeyUtil.isDownKey(event)) return true;
        if (KeyUtil.isLeftKey(event) && button == mBinding.systemIme) return true;
        return false;
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            if (mKeyboard.focusComposingNearest(item.getLeft())) return true;
            mKeyboard.focusSelectedTab();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item)) {
            mBinding.systemIme.requestFocus();
            return true;
        }
        return false;
    }

    private boolean handleComposingKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            mBinding.iconRemote.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            return focusFirst(mBinding.keyboard);
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.composingBar, item)) return true;
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.composingBar, item)) return true;
        return false;
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event)) return isFirstRow(mBinding.recordRecycler, item);
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        return false;
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
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
        if (mKeyboard != null && mKeyboard.isSystemMode()) mKeyboard.showSystemKeyboard();
        else if (mKeyboard != null) mKeyboard.hideSystemKeyboard();
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
