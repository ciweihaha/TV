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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Word;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.CandidateAdapter;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

/**
 * 百度输入法电视版搜索屏：左栏输拼音、右栏实时出结果。
 * <p>
 * 输入模型是手机输入法式：正在拼的拼音显示在搜索框里（已提交文本 + 拼写串），键盘上方有一行候选栏
 * （中文模式有拼写时显示，首选蓝底，遥控选词上屏去搜；英文模式没有候选栏，字母直输）。
 * 九键中文是 T9 预测（数字序列按拼音音节切分再走联想链），九键英文是多击（同键连按循环字母）。
 * 点右侧任一建议/历史直接进该标题的搜索结果页。右栏顶部是“影视”范围说明（搜索只搜影视，
 * 2026-11-21 之前的应用/游戏/影片三个 tab 已去掉：应用是摆设、游戏/影片点不进）。
 * 焦点分三个区：左栏面板（页签/候选栏/左列/键格/右列/模式行）、右栏列表（历史/建议）。
 * 键盘有中/英切换：中文模式空格 = 确认首个联想词并搜索，英文模式空格 = 输入空格字符。
 * <p>
 * 联想链复用旧的 getWord() → getSuggest() iQIYI 建议 + 300ms 防抖 + 三序号防过期（M22），一字未动：
 * 字母键输入更快（连按），防抖比原来更重要。候选栏用的就是这条链返回的建议词，不另找词典。
 */
public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback, CandidateAdapter.OnClickListener {

    private ActivitySearchBinding mBinding;
    private CustomKeyboard mKeyboard;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private CandidateAdapter mCandidateAdapter;
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
                // 键盘自己回写（renderBox）不走这里；外部直接往框里打字时先同步键盘状态，
                // 否则 committed 陈旧、下次按删除会把旧文本回写覆盖刚打的字
                if (mKeyboard != null && !mKeyboard.isRendering()) mKeyboard.syncExternalInput();
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
        mBinding.candidateBar.setItemAnimator(null);
        mBinding.candidateBar.setHasFixedSize(false);
        mBinding.candidateBar.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mBinding.candidateBar.setAdapter(mCandidateAdapter = new CandidateAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
        // 外部改了搜索框：键盘以框内文本为准，丢弃拼写状态
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
        // 过期响应丢弃：用户已经又输了字，这份候选是旧输入的，绝不能覆盖新候选，
        // 否则 Done/空格/标点会把错词提交进搜索框
        if (seq != suggestSeq) return;
        suggestAppliedSeq = seq;
        if (!save && empty()) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
        updateResultCount();
        updateMatchDisplay();
        updateCandidateBar();
    }

    /** 右上角“相关搜索结果：N个”（历史 + 建议两栏的条数之和）。 */
    private void updateResultCount() {
        int count = 0;
        if (mWordAdapter != null) count += mWordAdapter.getItemCount();
        if (mRecordAdapter != null) count += mRecordAdapter.getItemCount();
        mBinding.resultCount.setText(getString(R.string.search_result_count, count));
    }

    /** 左下角当前匹配：黄色拼音（正在拼的拼音字母，字母间空格分隔）+ 白色大号中文（首个建议标题）。
     * 英文模式直接显示原文（CCTV 5）；没有拼写时（只有已提交文本）整块隐藏。 */
    private void updateMatchDisplay() {
        if (mWordAdapter == null || mWordAdapter.getItemCount() == 0 || empty()) {
            mBinding.matchLayout.setVisibility(View.GONE);
            return;
        }
        String keyword = mBinding.keyword.getText().toString().trim();
        if (mKeyboard != null && mKeyboard.isEnglishMode()) {
            mBinding.matchPin.setText(keyword);
        } else if (mKeyboard != null && mKeyboard.isComposing()) {
            String pinyin = mKeyboard.composingDisplay().replace("'", "").replaceAll("[^A-Za-z]", "");
            if (pinyin.isEmpty()) {
                mBinding.matchLayout.setVisibility(View.GONE);
                return;
            }
            mBinding.matchPin.setText(spacedLetters(pinyin.toUpperCase()));
        } else {
            mBinding.matchLayout.setVisibility(View.GONE);
            return;
        }
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

    /** 候选栏：中文模式正在拼写且有建议时显示前 8 个建议词，首选蓝底；英文/符号盘/系统模式不显示。 */
    private void updateCandidateBar() {
        if (mKeyboard == null || mWordAdapter == null || mCandidateAdapter == null) return;
        boolean show = mBinding.kbPanel.getVisibility() == View.VISIBLE
                && mKeyboard.getMode() != KeyboardAdapter.MODE_SYMBOL
                && !mKeyboard.isSystemMode() && !mKeyboard.isEnglishMode()
                && mKeyboard.isComposing() && mWordAdapter.getItemCount() > 0;
        if (!show) {
            if (mBinding.candidateBar.getVisibility() != View.GONE) mBinding.candidateBar.setVisibility(View.GONE);
            return;
        }
        List<String> titles = new ArrayList<>();
        int n = Math.min(8, mWordAdapter.getItemCount());
        for (int i = 0; i < n; i++) titles.add(mWordAdapter.getItem(i).getTitle());
        boolean hadFocus = mBinding.candidateBar.hasFocus();
        mCandidateAdapter.setItems(titles);
        mBinding.candidateBar.setVisibility(View.VISIBLE);
        if (hadFocus) mBinding.candidateBar.post(() -> focusCandidate(0));
    }

    private boolean isCandidateBarVisible() {
        return mBinding.candidateBar.getVisibility() == View.VISIBLE && mCandidateAdapter != null && mCandidateAdapter.getItemCount() > 0;
    }

    private boolean focusCandidate(int position) {
        if (mCandidateAdapter == null || position < 0 || position >= mCandidateAdapter.getItemCount()) return false;
        RecyclerView.LayoutManager lm = mBinding.candidateBar.getLayoutManager();
        View child = lm == null ? null : lm.findViewByPosition(position);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    @Override
    public void onCandidateClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onCandidateFocus(int position) {
        // 蓝底跟随焦点：CandidateAdapter 的焦点监听里已经 setSelected，这里不用再做事
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    /** 中文模式空格键：确认首个联想词（与点首个建议相同：填进搜索框并立即搜）。
     * 无建议（或建议未对应当前输入）时什么都不做。英文/数字盘模式的空格不走这里（直接输空格字符，见 CustomKeyboard）。 */
    @Override
    public void onConfirmFirstAndSearch() {
        String first = getFirstCandidate();
        if (first == null || first.isEmpty()) return;
        setKeyword(first);
        onSearch();
    }

    /** 当前候选词的第一个：只有最新一次联想请求已应用、且正在拼写时才有效，
     * 否则返回 null（避免把旧输入的候选、热搜词当成当前拼音的首选提交）。 */
    @Override
    public String getFirstCandidate() {
        if (mWordAdapter == null || mWordAdapter.getItemCount() == 0) return null;
        if (mKeyboard == null || !mKeyboard.isComposing()) return null;
        if (suggestAppliedSeq != suggestSeq) return null;
        return mWordAdapter.getItem(0).getTitle();
    }

    /** 键盘状态变了（模式/页签/中英文切换）：刷新候选栏与当前匹配。 */
    @Override
    public void onStateChanged() {
        updateCandidateBar();
        updateMatchDisplay();
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
        // 完成/搜索键：先把正在拼的拼音提交为首选候选（没有则提交拼音原文），再搜索
        if (mKeyboard != null && mKeyboard.isComposing()) mKeyboard.commitComposing(true);
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
     * 焦点路由（三个区，左栏面板 ↔ 右栏列表）：
     * 左栏面板：搜索框 → 模式行 → 页签（拼音全键/九键）→ 候选栏 → 键格（左列/右列在两侧），
     * 面板贴着屏幕底边，键格末行下方什么都没有；模式行在搜索框下方、面板外，
     * 系统输入法模式下面板隐藏、模式行还在，可切回全键盘。
     * 右栏：历史/建议列表。
     * 区之间：键格右边缘 → 右列，右列右边缘 → 右栏列表首项；列表左边缘 → 键格首行末尾；列表上边缘 → 搜索框。
     * 每条边都显式处理：越界的方向返回 true 吞掉（不让焦点飞出屏幕），其余返回 false 走系统默认移动。
     * 占位键（QWERTY 错列用的 spacer）不可聚焦，边缘判断全部跳过。
     */
    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.mic) return handleMicKey(event);
        if (current == mBinding.tabFull || current == mBinding.tabNine) return handleTabKey(event, current);
        if (current == mBinding.segKey || current == mBinding.symKey || current == mBinding.langKey) return handleLeftColKey(event, current);
        if (current == mBinding.delKey || current == mBinding.doneKey) return handleRightColKey(event, current);
        if (current == mBinding.modeFull || current == mBinding.modeSystem) return handleModeRowKey(event, current);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inCandidate = mBinding.candidateBar.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inCandidate != null) return handleCandidateKey(event, inCandidate);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    /** 右栏列表首项：有历史时落到历史首项，否则落到建议首项（右栏已没有 tab）。 */
    private boolean focusRightList() {
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private static boolean isSpacer(View view) {
        return view != null && KeyboardAdapter.TAG_SPACER.equals(view.getTag());
    }

    private static int centerXOnScreen(View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return loc[0] + view.getWidth() / 2;
    }

    private static int centerYOnScreen(View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return loc[1] + view.getHeight() / 2;
    }

    /** 键格里按屏上 x 最近的非占位子项（跨容器移动用：候选栏/模式行下来时，坐标系不同不能直接比 getLeft）。 */
    private View findNearestKeyboardByScreenX(List<View> row, int targetX) {
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View child : row) {
            int dist = Math.abs(centerXOnScreen(child) - targetX);
            if (dist < minDist) {
                minDist = dist;
                nearest = child;
            }
        }
        return nearest;
    }

    /** 键格里按屏上 y 最近的非占位子项（左列/右列横向进出键格时用）。 */
    private View findNearestKeyboardByScreenY(int targetY) {
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < mBinding.keyboard.getChildCount(); i++) {
            View child = mBinding.keyboard.getChildAt(i);
            if (isSpacer(child)) continue;
            int dist = Math.abs(centerYOnScreen(child) - targetY);
            if (dist < minDist) {
                minDist = dist;
                nearest = child;
            }
        }
        return nearest;
    }

    private List<View> keyboardRow(boolean first) {
        List<View> row = new ArrayList<>();
        int edgeTop = -1;
        for (int i = 0; i < mBinding.keyboard.getChildCount(); i++) {
            View child = mBinding.keyboard.getChildAt(i);
            if (isSpacer(child)) continue;
            if (edgeTop == -1) edgeTop = child.getTop();
            if (first && child.getTop() == edgeTop) row.add(child);
            if (!first) {
                if (child.getTop() > edgeTop) edgeTop = child.getTop();
            }
        }
        if (!first) {
            for (int i = 0; i < mBinding.keyboard.getChildCount(); i++) {
                View child = mBinding.keyboard.getChildAt(i);
                if (!isSpacer(child) && child.getTop() == edgeTop) row.add(child);
            }
        }
        return row;
    }

    private View findNearestInRow(List<View> row, int targetX) {
        return findNearestKeyboardByScreenX(row, targetX);
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        int firstTop = -1;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (rv == mBinding.keyboard && isSpacer(child)) continue;
            firstTop = child.getTop();
            break;
        }
        return firstTop != -1 && item.getTop() == firstTop;
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        int lastTop = -1;
        for (int i = rv.getChildCount() - 1; i >= 0; i--) {
            View child = rv.getChildAt(i);
            if (rv == mBinding.keyboard && isSpacer(child)) continue;
            lastTop = child.getTop();
            break;
        }
        return lastTop != -1 && item.getTop() == lastTop;
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (rv == mBinding.keyboard && isSpacer(child)) continue;
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (rv == mBinding.keyboard && isSpacer(child)) continue;
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private static int centerX(View view) {
        return view.getLeft() + view.getWidth() / 2;
    }

    /** 键格首行最右一格（右栏列表左移的落点固定是首行末尾的 P）。 */
    private View findLastInFirstRow(RecyclerView rv) {
        List<View> row = keyboardRow(true);
        View nearest = null;
        int maxLeft = Integer.MIN_VALUE;
        for (View child : row) {
            if (child.getLeft() > maxLeft) {
                maxLeft = child.getLeft();
                nearest = child;
            }
        }
        return nearest;
    }

    private View findNearestModeByScreenX(int targetX) {
        View[] items = {mBinding.modeFull, mBinding.modeSystem};
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View item : items) {
            int dist = Math.abs(centerXOnScreen(item) - targetX);
            if (dist < minDist) {
                minDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    private View findNearestLeftCol(int targetY) {
        View[] items = {mBinding.segKey, mBinding.symKey, mBinding.langKey};
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View item : items) {
            int dist = Math.abs(centerYOnScreen(item) - targetY);
            if (dist < minDist) {
                minDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    private View findNearestRightCol(int targetY) {
        View[] items = {mBinding.delKey, mBinding.doneKey};
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (View item : items) {
            int dist = Math.abs(centerYOnScreen(item) - targetY);
            if (dist < minDist) {
                minDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            // 搜索框下方是模式行（面板贴底后模式行移到搜索框下方）
            View mode = findNearestModeByScreenX(centerXOnScreen(mBinding.keyword));
            if (mode != null) mode.requestFocus();
            return true;
        }
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleMicKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) {
            View mode = findNearestModeByScreenX(centerXOnScreen(mBinding.mic));
            if (mode != null) mode.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event)) return focusRightList();
        return false;
    }

    private boolean handleTabKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            // 页签上方是模式行（面板贴底后模式行移到搜索框下方）
            View mode = findNearestModeByScreenX(centerXOnScreen(item));
            if (mode != null) {
                mode.requestFocus();
                return true;
            }
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (isCandidateBarVisible()) return focusCandidate(mCandidateAdapter.getSelected());
            View child = findNearestInRow(keyboardRow(true), centerXOnScreen(item));
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.tabFull) return true;
        if (KeyUtil.isLeftKey(event) && item == mBinding.tabNine) {
            mBinding.tabFull.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event) && item == mBinding.tabFull) {
            mBinding.tabNine.requestFocus();
            return true;
        }
        if (KeyUtil.isRightKey(event) && item == mBinding.tabNine) return focusRightList();
        return false;
    }

    private boolean handleCandidateKey(KeyEvent event, View item) {
        int position = mBinding.candidateBar.getChildAdapterPosition(item);
        if (KeyUtil.isUpKey(event)) {
            mKeyboard.focusSelectedTab();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            View child = findNearestInRow(keyboardRow(true), centerXOnScreen(item));
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isLeftKey(event)) {
            if (position > 0) return focusCandidate(position - 1);
            return true;
        }
        if (KeyUtil.isRightKey(event)) {
            if (position >= 0 && position < mCandidateAdapter.getItemCount() - 1) return focusCandidate(position + 1);
            return focusRightList();
        }
        return false;
    }

    private boolean handleLeftColKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            if (item == mBinding.segKey) {
                if (isCandidateBarVisible()) return focusCandidate(mCandidateAdapter.getSelected());
                mKeyboard.focusSelectedTab();
                return true;
            }
            if (item == mBinding.symKey) {
                mBinding.segKey.requestFocus();
                return true;
            }
            mBinding.symKey.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (item == mBinding.segKey) {
                mBinding.symKey.requestFocus();
                return true;
            }
            if (item == mBinding.symKey) {
                mBinding.langKey.requestFocus();
                return true;
            }
            // 左列最下方是 中/英，下方什么都没有（面板贴底），吞掉
            return true;
        }
        if (KeyUtil.isLeftKey(event)) return true;
        if (KeyUtil.isRightKey(event)) {
            View child = findNearestKeyboardByScreenY(centerYOnScreen(item));
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean handleRightColKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            if (item == mBinding.delKey) {
                if (isCandidateBarVisible()) return focusCandidate(mCandidateAdapter.getSelected());
                mKeyboard.focusSelectedTab();
                return true;
            }
            mBinding.delKey.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            if (item == mBinding.delKey) {
                mBinding.doneKey.requestFocus();
                return true;
            }
            // 右列最下方是 完成，下方什么都没有（面板贴底），吞掉
            return true;
        }
        if (KeyUtil.isLeftKey(event)) {
            View child = findNearestKeyboardByScreenY(centerYOnScreen(item));
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isRightKey(event)) return focusRightList();
        return false;
    }

    private boolean handleModeRowKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event)) {
            // 模式行上方是搜索框（面板贴底后模式行移到搜索框下方）
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            // 模式行下方是页签；系统输入法模式下面板隐藏，什么都没有
            if (mBinding.kbPanel.getVisibility() == View.VISIBLE) {
                mKeyboard.focusSelectedTab();
            }
            return true;
        }
        // 全键盘 ↔ 系统输入法左右切换
        if (KeyUtil.isRightKey(event) && item == mBinding.modeFull) {
            mBinding.modeSystem.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.modeSystem) {
            mBinding.modeFull.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && item == mBinding.modeFull) return true;
        if (KeyUtil.isRightKey(event) && item == mBinding.modeSystem) return focusRightList();
        return false;
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            if (isCandidateBarVisible()) {
                View candidate = findNearestCandidateByScreenX(centerXOnScreen(item));
                if (candidate != null) {
                    candidate.requestFocus();
                    return true;
                }
            }
            mKeyboard.focusSelectedTab();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item)) {
            // 键格末行下方什么都没有（面板贴着屏幕底边），吞掉
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) {
            View col = findNearestLeftCol(centerYOnScreen(item));
            if (col != null) {
                col.requestFocus();
                return true;
            }
            return true;
        }
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.keyboard, item)) {
            View col = findNearestRightCol(centerYOnScreen(item));
            if (col != null) {
                col.requestFocus();
                return true;
            }
            return focusRightList();
        }
        return false;
    }

    private View findNearestCandidateByScreenX(int targetX) {
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < mBinding.candidateBar.getChildCount(); i++) {
            View child = mBinding.candidateBar.getChildAt(i);
            int dist = Math.abs(centerXOnScreen(child) - targetX);
            if (dist < minDist) {
                minDist = dist;
                nearest = child;
            }
        }
        return nearest;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.recordRecycler, item)) {
            // 右栏已没有 tab：历史列表首行按上直接回搜索框
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.recordRecycler, item)) {
            // 左移回左栏：落到键格首行末尾（P）
            View child = findLastInFirstRow(mBinding.keyboard);
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
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
            // 右栏已没有 tab：建议列表首行按上直接回搜索框
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.wordRecycler, item)) return true;
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.wordRecycler, item)) {
            // 左移回左栏：落到键格首行末尾（P）
            View child = findLastInFirstRow(mBinding.keyboard);
            if (child != null) {
                child.requestFocus();
                return true;
            }
            return false;
        }
        if (KeyUtil.isRightKey(event) && isLastInRow(mBinding.wordRecycler, item)) return true;
        return false;
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
