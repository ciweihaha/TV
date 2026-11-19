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
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
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

/**
 * 当贝市场式搜索屏：左栏输拼音首字母、右栏实时出结果。
 * <p>
 * 输入模型是“按 A–Z 首字母、右侧实时刷新”：没有拼写条、没有候选词提交、没有九键切音节。
 * 点右侧任一建议/历史直接进该标题的搜索结果页。焦点分三个区：左栏（搜索框/功能行/字母格/模式行）、
 * 右栏 tab（应用）、右栏列表（历史/建议）。游戏、影片两个 tab 暂无后端分类支持，置灰不可用。
 * <p>
 * 联想链复用旧的 getWord() → getSuggest() iQIYI 建议 + 300ms 防抖 + 三序号防过期（M22），一字未动：
 * 字母键输入更快（连按），防抖比原来更重要。
 */
public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener {

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
        mKeyboard = CustomKeyboard.init(mBinding);
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
        // “应用”即全部结果（默认选中）；游戏、影片置灰不可用，不接点击。
        mBinding.tabApp.setOnClickListener(v -> mBinding.tabApp.setSelected(true));
        // v5.9.8 CI fix: android:selected is not a valid XML attribute (AAPT2 refuses
        // to link it); initial selected state must be set in code so the
        // state_selected pill background of selector_tab_dangbei lights up.
        mBinding.tabApp.setSelected(true);
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
        updateResultCount();
        updateMatchDisplay();
    }

    /** 右上角“相关搜索结果：N个”（历史 + 建议两栏的条数之和）。 */
    private void updateResultCount() {
        int count = 0;
        if (mWordAdapter != null) count += mWordAdapter.getItemCount();
        if (mRecordAdapter != null) count += mRecordAdapter.getItemCount();
        mBinding.resultCount.setText(getString(R.string.search_result_count, count));
    }

    /** 左下角当前匹配：黄色已输首字母（字母间空格分隔）+ 白色大号中文（首个建议标题）。
     * 项目里没有中文→拼音的转换库，黄行不用首个标题的拼音、而用用户已输的首字母——
     * 视觉结构与当贝一致，数据不编造；空输入或无建议时整块隐藏。 */
    private void updateMatchDisplay() {
        if (mWordAdapter == null || mWordAdapter.getItemCount() == 0 || empty()) {
            mBinding.matchLayout.setVisibility(View.GONE);
            return;
        }
        mBinding.matchPin.setText(spacedLetters(mBinding.keyword.getText().toString().trim().toUpperCase()));
        mBinding.matchTitle.setText(mWordAdapter.getItem(0).getTitle());
        mBinding.matchLayout.setVisibility(View.VISIBLE);
    }

    private static String spacedLetters(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(text.charAt(i));
        }
        return sb.toString();
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
     * 焦点路由（三个区，左栏 ↔ tab ↔ 右栏列表）：
     * 左栏：搜索框 → 功能行 → 字母格 → 模式行（上下直通）；右栏：tab（应用）→ 历史/建议列表。
     * 区之间：左栏右边缘 → tab；tab 左 → 字母格最右列；列表左边缘 → tab；列表上边缘 → 上一栏。
     * 每条边都显式处理：越界的方向返回 true 吞掉（不让焦点飞出屏幕），其余返回 false 走系统默认移动。
     */
    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.mic) return handleMicKey(event);
        if (current == mBinding.funcGrid || current == mBinding.funcDigits || current == mBinding.funcClear || current == mBinding.funcBack) return handleFuncRowKey(event, current);
        if (current == mBinding.modeFull || current == mBinding.modeSystem) return handleModeRowKey(event, current);
        if (current == mBinding.tabApp) return handleTabKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    /** tab 可用时落到 tab，否则直接落到建议列表首项。 */
    private boolean focusTabOrWord() {
        if (mBinding.tabApp.isFocusable()) {
            mBinding.tabApp.requestFocus();
            return true;
        }
        return focusFirst(mBinding.wordRecycler);
    }

    private View findNearestInFirstRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int firstTop = rv.getChildAt(0).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == firstTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
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

    /** 字母格首行最右一格（tab 在右栏顶部，左移的落点固定是首行末尾的 E）。
     * 注意不用 tab 的 getTop() 去比字母格子项的 getTop()：两者不在同一坐标系
     *（tab 在右栏顶部，字母格在左栏中部），直接取首行最右一格就是几何上最近的。 */
    private View findLastInFirstRow(RecyclerView rv) {
        if (rv.getChildCount() == 0) return null;
        int firstTop = rv.getChildAt(0).getTop();
        View nearest = null;
        int maxLeft = Integer.MIN_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == firstTop && child.getLeft() > maxLeft) {
                maxLeft = child.getLeft();
                nearest = child;
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

    private static int centerX(View view) {
        return view.getLeft() + view.getWidth() / 2;
    }

    private View findNearestFunc(int targetLeft) {
        View[] items = {mBinding.funcGrid, mBinding.funcDigits, mBinding.funcClear, mBinding.funcBack};
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View item : items) {
            int dist = Math.abs(centerX(item) - targetLeft);
            if (dist < minDist) {
                minDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    private View findNearestMode(int targetLeft) {
        View[] items = {mBinding.modeFull, mBinding.modeSystem};
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View item : items) {
            int dist = Math.abs(centerX(item) - targetLeft);
            if (dist < minDist) {
                minDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            mKeyboard.focusFunctionRow();
            return true;
        }
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleMicKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            mKeyboard.focusFunctionRow();
            return true;
        }
        if (KeyUtil.isRightKey(event)) return focusTabOrWord();
        return false;
    }

    private boolean handleFuncRowKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            View child = findNearestInFirstRow(mBinding.keyboard, centerX(item));
            if (child != null) {
                child.requestFocus();
                return true;
            }
            // 系统输入法模式下字母格隐藏，下方就是模式行，直接落到“全键盘”
            mBinding.modeFull.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.funcGrid) return true;
        if (KeyUtil.isRightKey(event) && item == mBinding.funcBack) return focusTabOrWord();
        return false;
    }

    private boolean handleModeRowKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            if (mBinding.keyboard.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.keyboard, centerX(item));
                if (child != null) {
                    child.requestFocus();
                    return true;
                }
            }
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) return true;
        // 全键盘 ↔ 系统输入法一按就切：之前左右键走系统默认焦点搜索，模式行在屏外时
        // 焦点会无声地挪到看不见的位置，来回切要按很多次方向键。
        if (KeyUtil.isRightKey(event) && item == mBinding.modeFull) {
            mBinding.modeSystem.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.modeSystem) {
            mBinding.modeFull.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.modeFull) return true;
        if (KeyUtil.isRightKey(event) && item == mBinding.modeSystem) return focusTabOrWord();
        return false;
    }

    private boolean handleTabKey(KeyEvent event) {
        if (KeyUtil.isUpKey(event)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) return focusFirst(mBinding.wordRecycler);
        if (KeyUtil.isLeftKey(event)) {
            if (mBinding.keyboard.getVisibility() != View.VISIBLE) return false;
            View child = findLastInFirstRow(mBinding.keyboard);
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isRightKey(event)) {
            boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
            return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
        }
        return false;
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            View func = findNearestFunc(centerX(item));
            if (func != null) {
                func.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item)) {
            View mode = findNearestMode(centerX(item));
            if (mode != null) {
                mode.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.keyboard, item)) return focusTabOrWord();
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.recordRecycler, item)) {
            // tab 在历史列表正上方，与建议列表首行按上进 tab 一致
            mBinding.tabApp.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.recordRecycler, item)) {
            mBinding.tabApp.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.recordRecycler, item)) return true;
        return false;
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            mBinding.tabApp.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.wordRecycler, item)) return true;
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.wordRecycler, item)) {
            mBinding.tabApp.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.wordRecycler, item)) return true;
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
