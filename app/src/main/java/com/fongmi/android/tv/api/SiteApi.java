package com.fongmi.android.tv.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.loader.SpiderTestSession;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.player.media.LocalSubtitleScanner;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.SourceUrl;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Trans;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class SiteApi {

    public static final String LOCAL = "local_file";
    public static final String PUSH = "push_agent";

    /** Short-TTL in-memory cache for VOD API responses (P1). Home/category live 5 min, detail 10 min. */
    private static final Cache<String, HomeEntry> homeCache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(64).build();
    private static final Cache<String, String> categoryCache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(128).build();
    private static final Cache<String, String> detailCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(128).build();

    private record HomeEntry(String home, String video) {
    }

    /** Drop all cached VOD responses, e.g. when the config changes. */
    public static void clearCache() {
        homeCache.invalidateAll();
        categoryCache.invalidateAll();
        detailCache.invalidateAll();
    }

    /** Drop one site's cached home response so the next homeContent call really re-fetches it. */
    public static void invalidateHome(@NonNull Site site) {
        homeCache.invalidate(VodConfig.getCid() + ":" + site.getKey());
    }

    /**
     * 有些站点的筛选值只给了显示名 n，没给 v：此时 Value.getV() 是空串，
     * 同一行的芯片按 v 判等就全被当成同一个（点一个等于全行一起切换），
     * 发给站点的 f 里也是空值，站点直接忽略、返回默认列表——点了筛选毫无反应。
     * 这里把空 v 补成显示名 n（CMS 站点的过滤值本来就是显示名）；“全部/不限/所有”
     * 表示不过滤，保持空值。
     * <p>
     * 只对走 CMS 接口的站生效（type 0/1/2/4）：Spider 站（type 3）走站点自己的 JS，
     * 它的筛选值语义由 Spider 决定，补 n 可能把 Spider 的空值约定改成错误过滤值，
     * 所以 Spider 站不补。
     */
    private static void normalizeFilters(List<Filter> filters, boolean cms) {
        if (!cms) return;
        for (Filter filter : filters) {
            for (com.fongmi.android.tv.bean.Value value : filter.getValue()) {
                if (value.getV().isEmpty() && !value.getN().isEmpty() && !isAllValue(value.getN())) {
                    value.setV(value.getN());
                }
            }
        }
    }

    private static final Set<String> ALL_VALUE_NAMES = Set.of(
            "全部", "不限", "所有", "全部类型", "全部地区", "全部年份", "全部语言",
            "全部影片", "全部视频", "全部内容", "全部频道", "无限制", "不限制", "任意", "默认", "全部的");

    private static boolean isAllValue(String name) {
        return name != null && ALL_VALUE_NAMES.contains(name.trim());
    }

    /**
     * 部分站点 home 返回的筛选键是中文名（地区/年份/类型/语言），CMS 接口只认英文字段名
     * （area/year/class/lang），中文键发过去会被站点忽略、返回默认列表。
     * 发请求前把已知的中文键映射成标准字段；英文键和未知键原样保留。
     * 只用于 CMS 直连分支（type 1 的 f、type 4 的 ext），Spider 走站点自己的 JS，不动。
     */
    private static final Map<String, String> FILTER_KEYS = Map.of(
            "地区", "area", "地域", "area",
            "年份", "year", "年代", "year",
            "类型", "class", "分类", "class",
            "语言", "lang");

    /**
     * 中英文键冲突时英文键优先（CMS 只认英文字段名），其余按输入键排序后依次写入，
     * 两个中文键映射到同一英文字段时排在前面的胜出；结果用 LinkedHashMap 固定顺序，
     * 同一份 extend 每次归一化得到同样的键序和同样的值，可复现。
     */
    private static LinkedHashMap<String, String> normalizeExtend(HashMap<String, String> extend) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(extend.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            String mapped = FILTER_KEYS.getOrDefault(key, key);
            // 中文键对应的英文键已在输入里出现：英文键优先，跳过中文键
            if (FILTER_KEYS.containsKey(key) && extend.containsKey(mapped)) continue;
            normalized.putIfAbsent(mapped, extend.get(key));
        }
        return normalized;
    }

    public static String call(@NonNull Site site, @NonNull ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call call = site.getExt().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeader(), params) : OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        try (Response response = call.execute()) {
            return response.body() == null ? "" : response.body().string();
        }
    }

    private static boolean isSpider(@NonNull Site site) {
        return site.getType() == 3;
    }

    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    @NonNull
    public static AutoCloseable danmaku(@NonNull Uri uri, @NonNull Consumer<JSONObject> emit) throws Exception {
        String key = uri.getAuthority();
        List<String> ids = uri.getPathSegments();
        if (!"spider".equalsIgnoreCase(uri.getScheme()) || key == null || key.isEmpty() || ids.size() != 1 || ids.get(0).isEmpty() || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid live danmaku source");
        String id = ids.get(0);
        SpiderDebug.log("danmaku", "key=%s,id=%s", key, id);
        AutoCloseable subscription = BaseLoader.get().getSpider(key).danmaku(id, emit);
        if (subscription == null) throw new IllegalStateException("danmaku must return a subscription");
        return subscription;
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) throws Exception {
        String cacheKey = VodConfig.getCid() + ":" + site.getKey();
        boolean crash = Prefers.getBoolean("crash");
        HomeEntry cached = crash ? null : homeCache.getIfPresent(cacheKey);
        String home;
        String video;
        if (cached != null) {
            home = cached.home();
            video = cached.video();
        } else if (isSpider(site)) {
            Spider spider = site.recent().spider();
            home = crash ? "" : spider.homeContent(true);
            video = crash ? "" : spider.homeVideoContent();
            Prefers.put("crash", false);
            SpiderDebug.log("home", home);
            SpiderDebug.log("homeVideo", video);
            if (!crash) homeCache.put(cacheKey, new HomeEntry(home, video));
        } else if (site.getType() == 4) {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("filter", "true");
            home = call(site.fetchExt(), params);
            video = "";
            SpiderDebug.log("home", home);
            homeCache.put(cacheKey, new HomeEntry(home, video));
        } else {
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                home = response.body() == null ? "" : response.body().string();
            }
            video = "";
            SpiderDebug.log("home", home);
            homeCache.put(cacheKey, new HomeEntry(home, video));
        }
        Result result;
        if (isSpider(site)) {
            result = Result.fromJson(home);
            List<Vod> list = Result.fromJson(video).getList();
            if (!list.isEmpty()) result.setList(list);
        } else if (site.getType() == 4) {
            result = Result.fromJson(home);
        } else {
            result = fetchPic(site, Result.fromType(site.getType(), home));
        }
        setTypes(site, result);
        return result;
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        SpiderDebug.log("category", "key=%s,tid=%s,page=%s,filter=%s,extend=%s", key, tid, page, filter, extend);
        Site site = VodConfig.get().getSite(key);
        boolean spider = isSpider(site);
        String cacheKey = VodConfig.getCid() + ":cat:" + key + "|" + tid + "|" + page + "|" + filter + "|" + extendKey(extend);
        String categoryContent = categoryCache.getIfPresent(cacheKey);
        if (categoryContent == null) {
            if (spider) {
                categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                HashMap<String, String> normalized = normalizeExtend(extend);
                // type 0/1/2 都是 CMS 接口：筛选以 f（JSON）发出；type 4 用 ext（base64 JSON）。
                // 以前 type 0/2 从来不发 extend，点了筛选等于没点。
                if ((site.getType() == 0 || site.getType() == 1 || site.getType() == 2) && !normalized.isEmpty()) params.put("f", App.gson().toJson(normalized));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(normalized), Util.URL_SAFE));
                params.put("ac", ac(site.getType()));
                params.put("t", tid);
                params.put("pg", page);
                categoryContent = call(site, params);
            }
            categoryCache.put(cacheKey, categoryContent);
        }
        SpiderDebug.log("category", categoryContent);
        return spider ? Result.fromJson(categoryContent) : Result.fromType(site.getType(), categoryContent);
    }

    /**
     * 分类归并后的拉取：一个主分类对应多个成员 typeId，逐个拉取后合并成一个结果。
     * 主分类的视频排在前面，成员间按视频 id（无 id 时按名字 + 年份）去重——主题合集里的
     * 影片大多已在主分类里，去重后不重复出现；同名不同年份的片是不同的片，不会被误去重。
     * 总页数取各成员的最大值，任一成员还有下一页时“加载更多”就继续。单个 typeId 时直接走单分类路径。
     * 各成员的单次请求本身走 5 分钟缓存，重复翻页不重复发请求。
     * <p>
     * 成员容错：每个成员单独 try/catch，某个成员超时/失败只跳过该成员（记日志），
     * 其余成员的结果照常合并返回；全部成员都失败时返回空结果而不是抛错，
     * 不让一次成员失败把整个主分类 tab 变成空白/报错。
     * <p>
     * 全员失败不再反复重试：失败的成员记 30 秒负缓存（失败请求本来不进 5 分钟缓存，
     * 不记就会滑到底部再发一次），30 秒内的重试直接跳过、不重复发请求；同时结果打上
     * allFailed 标记，CustomScroller 见到它就停掉“加载更多”，与“站点没给总页数、
     * pageCount 为 0 但可继续加载”区分开。手动下拉刷新仍可重试。
     * <p>
     * 分页：pageCount 只取本次实际拉到的成员的最大值；已知已耗尽的成员（上次拉到的
     * pageCount 小于当前页）不再请求。跨页去重：第 1 页清空已见集合，之后各页
     * 用同一集合去重，已在前页出现过的视频不再重复出现。
     * <p>
     * 各成员的筛选：extend 按成员分别传入（见带 extendsByTid 的重载），每个成员
     * 只收到自己筛选定义里的键，不再把主分类同一份 extend 原样发给全部成员。
     */
    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull List<String> tids, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) throws Exception {
        return categoryContent(key, tids, page, filter, extend, null);
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull List<String> tids, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend, Map<String, HashMap<String, String>> extendsByTid) throws Exception {
        if (tids.size() == 1) return categoryContent(key, tids.get(0), page, filter, extendsByTid != null && extendsByTid.containsKey(tids.get(0)) ? extendsByTid.get(tids.get(0)) : extend);
        int pageNum = 1;
        try {
            pageNum = Math.max(1, Integer.parseInt(page));
        } catch (NumberFormatException ignored) {
        }
        String mergeKey = key + "|" + String.join(",", tids);
        Set<String> seen = mergedSeen(mergeKey, extend);
        if (pageNum == 1) {
            synchronized (seen) {
                seen.clear();
            }
        }
        List<Vod> merged = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenNameKeys = new HashSet<>();
        int pageCount = 0;
        boolean anySuccess = false;
        boolean anyFailed = false;
        boolean anySkippedExhausted = false;
        for (String tid : tids) {
            // 已耗尽的成员不再请求：上次拉到的总页数小于当前页
            Integer knownPages = mergedPageCount(mergeKey, tid);
            if (knownPages != null && knownPages > 0 && pageNum > knownPages) {
                anySkippedExhausted = true;
                continue;
            }
            HashMap<String, String> memberExtend = extendsByTid != null && extendsByTid.containsKey(tid) ? extendsByTid.get(tid) : extend;
            // 30 秒内刚失败过的成员不再重复发请求（失败请求不进 5 分钟缓存，不记就会滑到底部再发一次）
            if (isRecentlyFailed(mergeKey, tid, page, memberExtend)) {
                anyFailed = true;
                continue;
            }
            Result result;
            try {
                result = categoryContent(key, tid, page, filter, memberExtend);
                anySuccess = true;
            } catch (Exception e) {
                markFailed(mergeKey, tid, page, memberExtend);
                anyFailed = true;
                SpiderDebug.log("category", "merged member failed, skipped: key=%s,tid=%s,page=%s,error=%s", key, tid, page, e.toString());
                continue;
            }
            mergedPageCount(mergeKey, tid, result.getPageCount());
            pageCount = Math.max(pageCount, result.getPageCount());
            for (Vod vod : result.getList()) {
                String id = vod.getId();
                // 无 id 时用“名字 + 年份”去重：同名不同年份是不同的片，不能只按名字去重；
                // 年份缺失时退化为只按名字，与以前一致，不比以前更差
                String dedupKey = !id.isEmpty() ? "id:" + id : (!vod.getName().isEmpty() ? "name:" + vod.getName() + "|" + vod.getYear() : null);
                if (dedupKey == null) {
                    merged.add(vod);
                    continue;
                }
                synchronized (seen) {
                    if (seen.size() >= 5000) {
                        java.util.Iterator<String> it = seen.iterator();
                        if (it.hasNext()) {
                            it.next();
                            it.remove();
                        }
                    }
                    if (!seen.add(dedupKey)) continue;
                }
                if (!id.isEmpty()) {
                    if (!seenIds.add(id)) continue;
                } else if (!vod.getName().isEmpty()) {
                    if (!seenNameKeys.add(dedupKey)) continue;
                }
                merged.add(vod);
            }
        }
        Result result = new Result();
        result.setList(merged);
        result.setPageCount(pageCount);
        if (!anySuccess && anyFailed) {
            // 全员失败：打上标记，CustomScroller 见到就停掉“加载更多”，不再滑到底部反复重试
            result.setAllFailed(true);
        } else if (!anySuccess && anySkippedExhausted) {
            // 一个成员都没拉、且全是已知已耗尽：没有下一页了，把总页数收成当前页之前，
            // 否则 pageCount 0 会被当成“未知总页数、可继续加载”，空转反复请求
            result.setPageCount(Math.max(1, pageNum - 1));
        }
        return result;
    }

    /** 某次归并拉取已知的各成员总页数（用于跳过已耗尽成员），上限 64 个归并键。 */
    private static final Map<String, Map<String, Integer>> mergedPageCounts = new java.util.concurrent.ConcurrentHashMap<>();
    /** 某次归并拉取跨页已见的视频去重键，第 1 页清空；上限 64 个归并键，每键最多 5000 条。 */
    private static final Map<String, Set<String>> mergedSeenSets = new java.util.concurrent.ConcurrentHashMap<>();
    /** 归并成员失败的负缓存：键 → 失败时间戳，30 秒内不再对同一成员发同一请求。 */
    private static final Map<String, Long> mergedFailedAt = new java.util.concurrent.ConcurrentHashMap<>();
    /** 失败成员负缓存时长：30 秒，够拦住滑到底部反复重试，又不至于挡住用户手动刷新重试。 */
    private static final long MERGED_FAIL_NEGATIVE_TTL = 30_000;

    private static String mergedFailKey(String mergeKey, String tid, String page, HashMap<String, String> extend) {
        return mergeKey + "|" + tid + "|" + page + "|" + extendKey(extend);
    }

    private static boolean isRecentlyFailed(String mergeKey, String tid, String page, HashMap<String, String> extend) {
        Long failedAt = mergedFailedAt.get(mergedFailKey(mergeKey, tid, page, extend));
        return failedAt != null && System.currentTimeMillis() - failedAt < MERGED_FAIL_NEGATIVE_TTL;
    }

    private static void markFailed(String mergeKey, String tid, String page, HashMap<String, String> extend) {
        if (mergedFailedAt.size() >= 64) mergedFailedAt.clear();
        mergedFailedAt.put(mergedFailKey(mergeKey, tid, page, extend), System.currentTimeMillis());
    }

    private static Integer mergedPageCount(String mergeKey, String tid) {
        Map<String, Integer> counts = mergedPageCounts.get(mergeKey);
        return counts == null ? null : counts.get(tid);
    }

    private static void mergedPageCount(String mergeKey, String tid, int pageCount) {
        if (pageCount <= 0) return; // 0 表示站点没给总页数，不是“已耗尽”，不记录
        if (mergedPageCounts.size() >= 64 && !mergedPageCounts.containsKey(mergeKey)) mergedPageCounts.clear();
        mergedPageCounts.computeIfAbsent(mergeKey, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(tid, pageCount);
    }

    private static Set<String> mergedSeen(String mergeKey, HashMap<String, String> extend) {
        // extend 变化等于换了一组筛选，旧的已见集合不再适用
        String seenKey = mergeKey + "|" + extendKey(extend);
        if (mergedSeenSets.size() >= 64 && !mergedSeenSets.containsKey(seenKey)) mergedSeenSets.clear();
        return mergedSeenSets.computeIfAbsent(seenKey, k -> Collections.synchronizedSet(new LinkedHashSet<>()));
    }

    private static String extendKey(@NonNull HashMap<String, String> extend) {
        if (extend.isEmpty()) return "";
        return extend.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + "&" + b).orElse("");
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) throws Exception {
        return detailContent(key, id, false);
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id, boolean builtin) throws Exception {
        if (PUSH.equals(key) && SpiderTestSession.isPreview(id)) {
            SpiderTestSession.previewResult(id);
            return directDetail(id, id, ResUtil.getString(R.string.spider_test_title), R.string.push);
        }
        SpiderDebug.log("detail", "key=%s,id=%s", key, id);
        if (LOCAL.equals(key)) return directDetail(id, id, "", R.string.local);
        String originalId = id;
        boolean push = PUSH.equals(key) && Source.isPush(id);
        if (push) id = id.substring(7);
        if (PUSH.equals(key) && builtin && !push) return directDetail(originalId, id, id, R.string.push);
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty() && PUSH.equals(key)) return directDetail(originalId, id, id, R.string.push);
        boolean spider = isSpider(site);
        String cacheKey = VodConfig.getCid() + ":detail:" + key + "|" + id + "|" + builtin;
        String detailContent = detailCache.getIfPresent(cacheKey);
        if (detailContent == null) {
            if (spider) {
                detailContent = site.recent().spider().detailContent(Arrays.asList(id));
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", ac(site.getType()));
                params.put("ids", id);
                detailContent = call(site, params);
            }
            detailCache.put(cacheKey, detailContent);
        }
        SpiderDebug.log("detail", detailContent);
        Result result = spider ? Result.fromJson(detailContent) : Result.fromType(site.getType(), detailContent);
        if (push) result.getVod().setId(originalId);
        Source.get().parse(result.getVod().setFlags());
        return result;
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id, boolean builtin) throws Exception {
        return playerContent(key, flag, id, builtin, -1);
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id, boolean builtin, int qualityPosition) throws Exception {
        SpiderDebug.log("player", "key=%s,flag=%s,id=%s", key, flag, id);
        Source.get().stop();
        Result result = loadPlayerContent(key, flag, id, builtin);
        if (qualityPosition >= 0 && !result.getUrl().getValues().isEmpty()) result.getUrl().set(qualityPosition);
        return resolvePlayerContent(key, id, result);
    }

    @NonNull
    public static Result resolvePlayerContent(@NonNull String key, @NonNull String id, @NonNull Result result) throws Exception {
        result.setUrl(Source.get().fetch(result, id));
        String originalId = SourceUrl.original(id);
        if (LOCAL.equals(key) && !originalId.equals(result.getUrl().v()) && result.getSubs().isEmpty()) result.setSubs(LocalSubtitleScanner.scan(result.getUrl().v()));
        SpiderDebug.log("player", result.toString());
        return result;
    }

    @NonNull
    public static Result preloadContent(@NonNull String key, @NonNull String flag, @NonNull String id, boolean builtin) throws Exception {
        SpiderDebug.log("preload", "key=%s,flag=%s,id=%s", key, flag, id);
        return loadPlayerContent(key, flag, id, builtin);
    }

    @NonNull
    private static Result loadPlayerContent(@NonNull String key, @NonNull String flag, @NonNull String id, boolean builtin) throws Exception {
        id = SourceUrl.original(id);
        if (PUSH.equals(key) && SpiderTestSession.isPreview(id)) {
            Result result = Result.fromJson(SpiderTestSession.previewResult(id));
            result.setKey(key);
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            return result;
        }
        if (LOCAL.equals(key) || PUSH.equals(key) && builtin) return directPlayer(key, flag, id);
        Site site = VodConfig.get().getSite(key);
        if (site.getType() == 3 || site.getType() == 4) {
            String playerContent;
            if (site.getType() == 3) {
                playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("play", id);
                params.put("flag", flag);
                playerContent = call(site, params);
            }
            SpiderDebug.log("player", playerContent);
            Result result = Result.fromJson(playerContent);
            result.getVod().setFlags();
            if (result.getFlag().isEmpty()) result.setFlag(flag);
            result.setHeader(site.getHeader());
            result.setKey(key);
            return result;
        }
        if (site.isEmpty() && PUSH.equals(key)) return directPlayer(key, flag, id);
        Result result = new Result();
        result.setUrl(id);
        result.setKey(key);
        result.setFlag(flag);
        result.setHeader(site.getHeader());
        result.setPlayUrl(site.getPlayUrl());
        result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
        return result;
    }

    private static Result directDetail(String id, String target, String name, @StringRes int source) throws Exception {
        Vod vod = new Vod();
        vod.setId(id);
        vod.setName(name);
        vod.setPic(ResUtil.getString(R.string.push_image));
        Flag flag = Flag.create(ResUtil.getString(source));
        String[] lines = source == R.string.push ? target.split("\\R") : new String[]{target};
        for (String line : lines) addDirectEpisode(flag, line, source);
        vod.getFlags().add(flag);
        Source.get().parse(vod);
        return Result.vod(vod);
    }

    private static void addDirectEpisode(Flag flag, String line, @StringRes int source) {
        String url = line.trim();
        if (url.isEmpty()) return;
        int num = flag.getEpisodes().size() + 1;
        String number = num < 10 ? "0" + num : String.valueOf(num);
        int delimiter = url.indexOf('$');
        if (source == R.string.push && isNamedLine(url)) {
            String title = url.substring(0, delimiter).trim();
            flag.getEpisodes().add(Episode.create(title.isEmpty() ? number : title, url.substring(delimiter + 1).trim()));
        } else {
            flag.getEpisodes().add(Episode.create(number, url));
        }
    }

    private static boolean isNamedLine(String url) {
        int delimiter = url.indexOf('$');
        if (delimiter < 0) return false;
        int colon = url.indexOf(':');
        if (delimiter < colon) return true;
        if (colon >= 0 && url.startsWith("?", colon + 1)) return false;
        int scheme = url.indexOf("://");
        return scheme >= 0 ? delimiter < scheme : Sniffer.AI_PUSH.matcher(url.substring(delimiter + 1).trim()).lookingAt();
    }

    private static Result directPlayer(String key, String flag, String id) throws Exception {
        Result result = new Result();
        result.setUrl(id);
        result.setKey(key);
        result.setFlag(flag);
        result.setParse(PUSH.equals(key) && isWebPage(id) ? 1 : 0);
        if (LOCAL.equals(key)) result.setSubs(LocalSubtitleScanner.scan(id));
        return result;
    }

    static boolean isWebPage(String url) {
        String path = UriUtil.stripQueryAndFragment(url).toLowerCase(Locale.ROOT);
        return path.endsWith(".htm") || path.endsWith(".html") || path.endsWith(".shtm") || path.endsWith(".shtml");
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) throws Exception {
        keyword = Trans.convert(keyword, site.getLang());
        SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s", site.getName(), keyword, quick, page);
        boolean hasPage = !page.equals("1");
        boolean spider = isSpider(site);
        String searchContent;
        if (spider) {
            searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("quick", String.valueOf(quick));
            params.put("extend", "");
            if (hasPage) params.put("pg", page);
            searchContent = call(site, params);
        }
        SpiderDebug.log("search", searchContent);
        Result result = spider ? Result.fromJson(searchContent) : fetchPic(site, Result.fromType(site.getType(), searchContent));
        for (Vod vod : result.getList()) vod.setSite(site);
        return result;
    }

    @NonNull
    public static Result action(@NonNull String key, @NonNull String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        SpiderDebug.log("action", "key=%s,action=%s", key, action);
        if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
        if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
        return Result.empty();
    }

    @NonNull
    public static Result fetchPic(@NonNull Site site, @NonNull Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        boolean empty = site.getCategories().isEmpty();
        Set<String> categories = empty ? null : new HashSet<>(site.getCategories());
        for (Vod item : result.getList()) if (empty || categories.contains(item.getTypeName())) ids.add(item.getId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", ac(site.getType()));
        params.put("ids", TextUtils.join(",", ids));
        try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
            if (response.body() != null) result.setList(Result.fromType(site.getType(), response.body().string()).getList());
            return result;
        }
    }

    private static void setTypes(@NonNull Site site, @NonNull Result result) {
        LinkedHashMap<String, List<Filter>> filters = result.getFilters();
        result.getTypes().stream().filter(type -> filters.containsKey(type.getTypeId())).forEach(type -> {
            List<Filter> list = filters.get(type.getTypeId());
            normalizeFilters(list, !isSpider(site));
            type.setFilters(list);
        });
        if (site.getCategories().isEmpty()) return;
        Map<String, Class> typeByName = new HashMap<>();
        result.getTypes().forEach(type -> typeByName.put(type.getTypeName(), type));
        List<Class> types = site.getCategories().stream().map(typeByName::get).filter(Objects::nonNull).toList();
        if (!types.isEmpty()) result.setTypes(types);
    }
}
