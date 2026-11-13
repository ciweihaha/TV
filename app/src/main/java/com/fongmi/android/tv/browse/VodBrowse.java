package com.fongmi.android.tv.browse;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class VodBrowse {

    static final String VOD_EP = "VE:";
    static final String VOD_PLAY = "VP:";
    static final String VOD_SEARCH = "VS:";
    static final String VOD_DETAIL = "VD:";

    private static final char KEY_SEPARATOR = '|';
    private static final int MAX_VOD_CACHE = 50;
    private static final Map<String, CompletableFuture<Vod>> detailRequests = new ConcurrentHashMap<>();
    /** True LRU (P7): Guava evicts the least-recently-used entry once the
     * cache holds MAX_VOD_CACHE items, and the removal listener drops the
     * evicted title's episode indexes. Previously a random entry was
     * evicted (ConcurrentHashMap keySet().stream().findFirst()). */
    /** The listener is typed separately because CacheBuilder.removalListener
     * takes RemovalListener<? super K, ? super V>: an inline lambda would
     * see getKey() as Object and not compile against evictRelated(String). */
    private static final RemovalListener<String, Vod> evictionListener = notification -> {
        if (notification.wasEvicted()) evictRelated(notification.getKey());
    };
    private static final Cache<String, Vod> vodCache = CacheBuilder.<String, Vod>newBuilder().maximumSize(MAX_VOD_CACHE).removalListener(evictionListener).build();
    private static final Map<String, String> epNavMap = new ConcurrentHashMap<>();
    /** Per-title episode index (P5): each title's entries live in its own
     * list, so rebuilding one title touches only that title instead of
     * scanning every cached title's entries. */
    private static final Map<String, List<EpEntry>> epIndexMap = new ConcurrentHashMap<>();
    private static final Map<String, EpEntry> epEntries = new ConcurrentHashMap<>();
    private static final Map<String, Integer> epCountMap = new ConcurrentHashMap<>();
    private static volatile int cacheGeneration;

    static synchronized void clearCache() {
        cacheGeneration++;
        for (CompletableFuture<Vod> future : detailRequests.values()) future.complete(null);
        detailRequests.clear();
        vodCache.invalidateAll();
        epNavMap.clear();
        epEntries.clear();
        epIndexMap.clear();
        epCountMap.clear();
        SiteApi.clearCache();
    }

    @NonNull
    static ImmutableList<MediaItem> getHistory() {
        return History.get().stream().map(history -> detailItem(history.getKey(), history.getVodName(), history.getVodRemarks(), history.getVodPic())).collect(ImmutableList.toImmutableList());
    }

    @NonNull
    static ImmutableList<MediaItem> getFavorites() {
        return Keep.getVod().stream().filter(keep -> keep.getCid() == VodConfig.getCid())
                .map(keep -> detailItem(keep.getKey(), keep.getVodName(), keep.getSiteName(), keep.getVodPic())).collect(ImmutableList.toImmutableList());
    }

    @NonNull
    static ImmutableList<MediaItem> getEpisodes(@NonNull String mediaId) throws Exception {
        int generation = cacheGeneration;
        String key = mediaId.substring(VOD_DETAIL.length());
        History history = findHistory(key);
        if (history == null) return ImmutableList.of();
        Vod vod = getOrFetchVod(key, history);
        if (vod == null) return ImmutableList.of();
        Flag flag = findFlag(vod, history.getVodFlag());
        if (flag == null || flag.getEpisodes().isEmpty()) return ImmutableList.of();
        synchronized (VodBrowse.class) {
            if (buildEpIndex(key, flag, history, generation) == C.INDEX_UNSET) return ImmutableList.of();
            ImmutableList.Builder<MediaItem> items = ImmutableList.builder();
            items.add(BrowseTree.playable(VOD_PLAY + key, ResUtil.getString(R.string.auto_resume), history.getVodRemarks(), BrowseTree.icon(R.drawable.ic_browse_resume)));
            for (int i = 0; i < flag.getEpisodes().size(); i++) items.add(episodeItem(key, flag, i, history, vod));
            return items.build();
        }
    }

    private static MediaItem episodeItem(String key, Flag flag, int index, History history, Vod vod) {
        Episode episode = flag.getEpisodes().get(index);
        boolean current = flag.getFlag().equals(history.getVodFlag()) && (history.getEpisodeIndex() >= 0 ? index == history.getEpisodeIndex() : episode.getUrl().equals(history.getEpisodeUrl()));
        return BrowseTree.playable(VOD_EP + epNavKey(key, flag.getFlag(), index), episode.getName(), current ? ResUtil.getString(R.string.auto_current_episode) : vod.getName(history.getVodName()), history.getVodPic());
    }

    @Nullable
    private static History findHistory(String key) {
        History history = History.find(key);
        if (history != null) return history;
        Keep keep = Keep.find(key);
        if (keep == null) return null;
        history = new History();
        history.setKey(key);
        history.setCid(keep.getCid());
        history.setVodName(keep.getVodName());
        history.setVodPic(keep.getVodPic());
        return history;
    }

    private static MediaItem detailItem(String key, String title, String subtitle, String art) {
        MediaItem item = BrowseTree.folder(VOD_DETAIL + key, title);
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setSubtitle(subtitle)
                .setArtworkUri(MediaItemFactory.getArtworkUri(BrowseTree.artwork(art, R.drawable.ic_browse_movie))).build()).build();
    }

    @NonNull
    static List<MediaItem> search(@NonNull Site site, @NonNull String keyword) throws Exception {
        Result result = SiteApi.searchContent(site, keyword, false, "1");
        return result.getList().stream().map(vod -> BrowseTree.playable(searchId(site.getKey(), vod.getId()), vod.getName(), vod.getRemarks(), vod.getPic())).toList();
    }

    @Nullable
    static MediaItem getItem(@NonNull String mediaId) throws Exception {
        if (mediaId.startsWith(VOD_DETAIL)) {
            History history = findHistory(mediaId.substring(VOD_DETAIL.length()));
            return history == null ? null : detailItem(history.getKey(), history.getVodName(), history.getVodRemarks(), history.getVodPic());
        }
        if (mediaId.startsWith(VOD_EP)) return getEpisodeItem(mediaId);
        if (mediaId.startsWith(VOD_PLAY)) return getHistoryItem(mediaId);
        if (mediaId.startsWith(VOD_SEARCH)) return BrowseSearch.getItem(mediaId);
        return null;
    }

    @Nullable
    static MediaItem resolve(@NonNull String mediaId) throws Exception {
        if (mediaId.startsWith(VOD_PLAY)) return resolvePlay(mediaId);
        if (mediaId.startsWith(VOD_EP)) return resolveEp(mediaId);
        if (mediaId.startsWith(VOD_SEARCH)) return resolveSearch(mediaId);
        return null;
    }

    @Nullable
    static MediaItem navigate(@NonNull String mediaId, int delta) throws Exception {
        if (mediaId.startsWith(VOD_EP)) return navigateEpisode(mediaId, delta);
        String epId = ensureEpLoaded(mediaId);
        if (epId != null) return navigateEpisode(epId, delta);
        return null;
    }

    @Nullable
    private static MediaItem resolvePlay(@NonNull String mediaId) throws Exception {
        String historyKey = mediaId.substring(VOD_PLAY.length());
        String currentEpId = ensureEpLoaded(historyKey);
        return currentEpId != null ? resolveEp(currentEpId) : null;
    }

    @Nullable
    private static MediaItem resolveSearch(@NonNull String mediaId) throws Exception {
        int generation = cacheGeneration;
        SearchEntry search = SearchEntry.parse(mediaId);
        if (search == null) return null;
        String historyKey = historyKey(search.siteKey, search.vodId);
        History existing = History.find(historyKey);
        if (existing != null) return resolveWithHistory(historyKey, existing);
        VodConfig.get().ensureLoaded();
        boolean builtin = SiteApi.PUSH.equals(search.siteKey) && Source.isBuiltin(search.vodId);
        CompletableFuture<Vod> future = new CompletableFuture<>();
        CompletableFuture<Vod> inFlight;
        synchronized (VodBrowse.class) {
            if (generation != cacheGeneration) return null;
            inFlight = detailRequests.putIfAbsent(historyKey, future);
        }
        if (inFlight != null) return awaitSearchResult(historyKey, generation, inFlight);
        Vod vod;
        try {
            vod = SiteApi.detailContent(search.siteKey, search.vodId, builtin).getVod();
        } catch (Throwable e) {
            detailRequests.remove(historyKey, future);
            future.completeExceptionally(e);
            throw e;
        }
        MediaItem source = BrowseSearch.getItem(mediaId);
        if (source != null && source.mediaMetadata.title != null) vod.checkName(source.mediaMetadata.title.toString());
        String epId = indexSearchResult(historyKey, vod, future, generation);
        return epId != null ? resolveEp(epId) : null;
    }

    @Nullable
    private static synchronized String indexSearchResult(String historyKey, Vod vod, CompletableFuture<Vod> future, int generation) {
        if (!detailRequests.remove(historyKey, future) || generation != cacheGeneration) {
            future.complete(null);
            return null;
        }
        if (TextUtils.isEmpty(vod.getId()) || vod.getFlags().isEmpty()) {
            future.complete(null);
            return null;
        }
        putVod(historyKey, vod);
        History history = createHistory(historyKey, vod);
        future.complete(vod);
        Flag resumeFlag = findFlag(vod, history.getVodFlag());
        if (resumeFlag == null || resumeFlag.getEpisodes().isEmpty()) return null;
        int currentIdx = buildEpIndex(historyKey, resumeFlag, history, generation);
        return epNavMap.get(epNavKey(historyKey, resumeFlag.getFlag(), currentIdx));
    }

    @Nullable
    private static MediaItem resolveWithHistory(@NonNull String historyKey, @NonNull History history) throws Exception {
        String epId = ensureEpLoaded(historyKey, history);
        return epId != null ? resolveEp(epId) : null;
    }

    private static History createHistory(@NonNull String historyKey, @NonNull Vod vod) {
        History history = new History();
        history.setKey(historyKey);
        history.setCid(VodConfig.getCid());
        history.setVodName(vod.getName());
        history.setVodPic(vod.getPic());
        history.findEpisode(vod.getFlags());
        return history;
    }

    @Nullable
    private static String ensureEpLoaded(@NonNull String historyKey) throws Exception {
        return ensureEpLoaded(historyKey, findHistory(historyKey));
    }

    @Nullable
    private static String ensureEpLoaded(@NonNull String historyKey, @Nullable History history) throws Exception {
        int generation = cacheGeneration;
        if (history == null) return null;
        Vod vod = getOrFetchVod(historyKey, history);
        if (vod == null) return null;
        Flag flag = findFlag(vod, history.getVodFlag());
        if (flag == null || flag.getEpisodes().isEmpty()) return null;
        int currentIdx = buildEpIndex(historyKey, flag, history, generation);
        return epNavMap.get(epNavKey(historyKey, flag.getFlag(), currentIdx));
    }

    private static synchronized int buildEpIndex(@NonNull String historyKey, @NonNull Flag flag, @NonNull History history, int generation) {
        if (generation != cacheGeneration) return C.INDEX_UNSET;
        evictRelated(historyKey);
        String flagName = flag.getFlag();
        List<Episode> episodes = flag.getEpisodes();
        epCountMap.put(epCountKey(historyKey, flagName), episodes.size());
        String currentUrl = history.getEpisode() != null ? history.getEpisode().getUrl() : null;
        int current = 0;
        List<EpEntry> entries = new ArrayList<>(episodes.size());
        for (int i = 0; i < episodes.size(); i++) {
            if (!TextUtils.isEmpty(currentUrl) && episodes.get(i).getUrl().equals(currentUrl)) current = i;
            entries.add(indexEpisode(historyKey, flagName, i, history));
        }
        epIndexMap.put(historyKey, entries);
        history.setEpisodeIndex(current);
        return current;
    }

    private static EpEntry indexEpisode(@NonNull String historyKey, @NonNull String flagName, int index, @NonNull History history) {
        String key = epNavKey(historyKey, flagName, index);
        String id = VOD_EP + key;
        EpEntry entry = new EpEntry(historyKey, flagName, index, history.getSiteKey(), history.getVodPic(), history);
        epEntries.put(id, entry);
        epNavMap.put(key, id);
        return entry;
    }

    @Nullable
    private static MediaItem navigateEpisode(@NonNull String mediaId, int delta) throws Exception {
        EpEntry current = findEntry(mediaId);
        if (current == null) return null;
        Integer count = epCountMap.get(epCountKey(current.historyKey, current.flagName));
        if (count == null || count == 0) return null;
        int target = BrowseTree.wrapIndex(current.index, delta, count);
        String nextId = epNavMap.get(epNavKey(current.historyKey, current.flagName, target));
        if (nextId == null) return null;
        return resolveEp(nextId);
    }

    @Nullable
    private static MediaItem resolveEp(@NonNull String mediaId) throws Exception {
        EpEntry entry = findEntry(mediaId);
        if (entry == null) return null;
        Vod vod = getOrFetchVod(entry.historyKey);
        if (vod == null) return null;
        Flag flag = findFlag(vod, entry.flagName);
        if (flag == null || entry.index >= flag.getEpisodes().size()) return null;
        Episode episode = flag.getEpisodes().get(entry.index);
        History saved = History.find(entry.historyKey);
        History history = (saved == null ? entry.history : saved).copy();
        if (!entry.flagName.equals(history.getVodFlag()) || !episode.getUrl().equals(history.getEpisodeUrl())) {
            history.setPosition(C.TIME_UNSET);
            history.setDuration(C.TIME_UNSET);
        }
        boolean builtin = SiteApi.PUSH.equals(entry.siteKey) && Source.isBuiltin(vod.getId());
        Result result = SiteApi.playerContent(entry.siteKey, entry.flagName, episode.getUrl(), builtin);
        if (TextUtils.isEmpty(result.getRealUrl()) && !result.hasMsg()) return null;
        Vod metadata = result.getVod();
        if (TextUtils.isEmpty(metadata.getName())) metadata = vod;
        String title = metadata.getName(history.getVodName());
        history.setVodName(title);
        history.setVodFlag(entry.flagName);
        history.setVodRemarks(episode.getName());
        history.setEpisodeUrl(episode.getUrl());
        return BrowseTree.stream(new BrowsePlayback(mediaId, result, history, null), title, episode.getName(), entry.vodPic);
    }

    @Nullable
    private static EpEntry findEntry(@NonNull String mediaId) throws Exception {
        int generation = cacheGeneration;
        EpEntry entry = epEntries.get(mediaId);
        if (entry != null) return entry;
        int indexSeparator = mediaId.lastIndexOf(KEY_SEPARATOR);
        if (indexSeparator < VOD_EP.length()) return null;
        int flagSeparator = mediaId.lastIndexOf(KEY_SEPARATOR, indexSeparator - 1);
        if (flagSeparator < VOD_EP.length()) return null;
        String historyKey = mediaId.substring(VOD_EP.length(), flagSeparator);
        History history = findHistory(historyKey);
        if (history == null) return null;
        Vod vod = getOrFetchVod(historyKey, history);
        if (vod == null) return null;
        String flagName = mediaId.substring(flagSeparator + 1, indexSeparator);
        Flag flag = vod.getFlags().stream().filter(item -> item.getFlag().equals(flagName)).findFirst().orElse(null);
        if (flag == null) return null;
        if (buildEpIndex(historyKey, flag, history, generation) == C.INDEX_UNSET) return null;
        return epEntries.get(mediaId);
    }

    @Nullable
    private static MediaItem getHistoryItem(@NonNull String mediaId) {
        History history = findHistory(mediaId.substring(VOD_PLAY.length()));
        return history == null ? null : BrowseTree.playable(VOD_PLAY + history.getKey(), history.getVodName(), history.getVodRemarks(), history.getVodPic());
    }

    @Nullable
    private static MediaItem getEpisodeItem(@NonNull String mediaId) throws Exception {
        EpEntry entry = findEntry(mediaId);
        if (entry == null) return null;
        Vod vod = vodCache.getIfPresent(entry.historyKey);
        if (vod == null) return null;
        Flag flag = findFlag(vod, entry.flagName);
        if (flag == null || entry.index >= flag.getEpisodes().size()) return null;
        Episode episode = flag.getEpisodes().get(entry.index);
        return BrowseTree.playable(mediaId, vod.getName(entry.history.getVodName()), episode.getName(), entry.vodPic);
    }

    @Nullable
    private static Vod getOrFetchVod(@NonNull String historyKey) throws Exception {
        Vod vod = vodCache.getIfPresent(historyKey);
        if (vod != null) return vod;
        return getOrFetchVod(historyKey, findHistory(historyKey));
    }

    @Nullable
    private static Vod getOrFetchVod(@NonNull String historyKey, @Nullable History history) throws Exception {
        int generation = cacheGeneration;
        Vod vod = vodCache.getIfPresent(historyKey);
        if (vod != null) return vod;
        if (history == null) return null;
        CompletableFuture<Vod> future = new CompletableFuture<>();
        CompletableFuture<Vod> existing;
        synchronized (VodBrowse.class) {
            if (generation != cacheGeneration) return null;
            existing = detailRequests.putIfAbsent(historyKey, future);
        }
        if (existing != null) return awaitVod(historyKey, generation, existing);
        try {
            vod = fetchDetail(history);
        } catch (Throwable e) {
            detailRequests.remove(historyKey, future);
            future.completeExceptionally(e);
            throw e;
        }
        synchronized (VodBrowse.class) {
            if (!detailRequests.remove(historyKey, future) || generation != cacheGeneration || vod == null) {
                future.complete(null);
                return null;
            }
            putVod(historyKey, vod);
            future.complete(vod);
            return vod;
        }
    }

    private static synchronized void putVod(String historyKey, Vod vod) {
        vodCache.put(historyKey, vod);
    }

    private static synchronized void evictVod(String historyKey) {
        vodCache.invalidate(historyKey);
        evictRelated(historyKey);
    }

    private static void evictRelated(String historyKey) {
        List<EpEntry> old = epIndexMap.remove(historyKey);
        if (old == null) return;
        for (EpEntry entry : old) {
            epEntries.remove(VOD_EP + epNavKey(entry.historyKey(), entry.flagName(), entry.index()));
            epNavMap.remove(epNavKey(entry.historyKey(), entry.flagName(), entry.index()));
        }
        old.stream().map(EpEntry::flagName).distinct().forEach(flagName -> epCountMap.remove(epCountKey(historyKey, flagName)));
    }

    private static final long DETAIL_WAIT_MS = 10_000;

    @Nullable
    private static MediaItem awaitSearchResult(@NonNull String historyKey, int generation, CompletableFuture<Vod> future) throws Exception {
        Vod vod = awaitVod(historyKey, generation, future);
        if (vod == null) return null;
        if (generation != cacheGeneration) return null;
        History existing = History.find(historyKey);
        if (existing != null) return resolveWithHistory(historyKey, existing);
        return resolveWithHistory(historyKey, createHistory(historyKey, vod));
    }

    @Nullable
    private static Vod awaitVod(@NonNull String historyKey, int generation, CompletableFuture<Vod> future) {
        if (generation != cacheGeneration) return null;
        try {
            Vod vod = future.get(DETAIL_WAIT_MS, TimeUnit.MILLISECONDS);
            return generation != cacheGeneration ? null : vod;
        } catch (TimeoutException e) {
            return generation != cacheGeneration ? null : vodCache.getIfPresent(historyKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    @Nullable
    private static Vod fetchDetail(@NonNull History history) throws Exception {
        VodConfig.get().ensureLoaded();
        boolean builtin = SiteApi.PUSH.equals(history.getSiteKey()) && Source.isBuiltin(history.getVodId());
        Vod vod = SiteApi.detailContent(history.getSiteKey(), history.getVodId(), builtin).getVod();
        return TextUtils.isEmpty(vod.getId()) ? null : vod;
    }

    @Nullable
    private static Flag findFlag(@NonNull Vod vod, @Nullable String name) {
        if (vod.getFlags().isEmpty()) return null;
        if (!TextUtils.isEmpty(name)) for (Flag flag : vod.getFlags()) if (flag.getFlag().equals(name)) return flag;
        return vod.getFlags().get(0);
    }

    @NonNull
    private static String historyKey(@NonNull String siteKey, @NonNull String vodId) {
        return siteKey + AppDatabase.SYMBOL + vodId + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    @NonNull
    private static String epNavKey(@NonNull String historyKey, @NonNull String flagName, int index) {
        return epCountKey(historyKey, flagName) + KEY_SEPARATOR + index;
    }

    @NonNull
    private static String epCountKey(@NonNull String historyKey, @NonNull String flagName) {
        return historyKey + KEY_SEPARATOR + flagName;
    }

    @NonNull
    private static String searchId(@NonNull String siteKey, @NonNull String vodId) {
        return VOD_SEARCH + siteKey + KEY_SEPARATOR + vodId;
    }

    private record SearchEntry(String siteKey, String vodId) {

        @Nullable
        static SearchEntry parse(@NonNull String mediaId) {
            String body = mediaId.substring(VOD_SEARCH.length());
            int sep = body.indexOf(KEY_SEPARATOR);
            if (sep < 0) return null;
            return new SearchEntry(body.substring(0, sep), body.substring(sep + 1));
        }
    }

    private record EpEntry(String historyKey, String flagName, int index, String siteKey, String vodPic, History history) {
    }
}
