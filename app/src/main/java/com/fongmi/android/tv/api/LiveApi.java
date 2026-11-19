package com.fongmi.android.tv.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.utils.EpgFetchGuard;
import com.fongmi.android.tv.utils.EpgRefreshPolicy;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public class LiveApi {

    private static final String TAG = LiveApi.class.getSimpleName();

    /** JSON EPG cache (P2): parsed data is valid for the whole day, so one
     * download per (channel, date) is enough. The XMLTV path already has a
     * 6h disk cache; this covers the JSON path (SCF output) which had none. */
    private static final Cache<String, String> epgJsonCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).maximumSize(512).build();

    private static final EpgFetchGuard EPG_GUARD = new EpgFetchGuard();

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        return parseXml(item, false);
    }

    /**
     * @param force bypass the 6h "already attempted" guard and the 6h disk
     * cache, so a scheduled refresh really re-downloads the server file.
     */
    public static boolean parseXml(@NonNull Live item, boolean force) {
        if (force) item.clearEpgXmlAttempted();
        item.markEpgXmlAttempt(System.currentTimeMillis());
        boolean parsed = false;
        for (String url : item.getEpgXml()) {
            if (item.tryMarkEpgXmlAttempted(url)) continue;
            if (startXml(item, url, force)) {
                parsed = true;
                item.markEpgXmlRefreshed(System.currentTimeMillis());
            } else item.markEpgXmlFailed(url);
        }
        return parsed;
    }

    /**
     * Re-download the XMLTV guide when it is due (hourly, day changed, or
     * the current channel has no programme covering now), then refresh the
     * displayed guide via the caller's xml observer. Returns true when a
     * refresh was started. Safe to call on every channel change and on
     * every return to the foreground: {@link EpgRefreshPolicy} throttles it.
     */
    public static boolean refreshEpgIfDue(@NonNull Live item, @Nullable Channel channel, @NonNull ZoneId zoneId) {
        if (item.getEpgXml().isEmpty()) return false;
        long now = System.currentTimeMillis();
        boolean hasCurrent = channel != null && channel.getData(zoneId).getInRange() >= 0;
        boolean dayChanged = EpgRefreshPolicy.isDayChanged(item.getEpgXmlLastRefreshMs(), now, zoneId);
        if (!EpgRefreshPolicy.shouldRefresh(item.getEpgXmlLastRefreshMs(), item.getEpgXmlLastAttemptMs(), now, hasCurrent, dayChanged)) return false;
        Log.i(TAG, "Epg auto-refresh due channel=" + (channel == null ? "" : channel.getName()) + " hasCurrent=" + hasCurrent + " dayChanged=" + dayChanged + " lastRefresh=" + item.getEpgXmlLastRefreshMs());
        return parseXml(item, true);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        for (int offset = -1; offset <= 1; offset++) fetchEpgDay(item, zoneId, offset);
        return item.getData(zoneId).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Result result = resolveUrl(item);
        item.applyAuth(result);
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = resolveUrl(item);
        if (Source.isPush(result.getUrl().v())) return result;
        result.setTimeshift(null);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        BaseLoader.get().requireProxy(result.getRealUrl());
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        item.applyAuth(result);
        return result;
    }

    private static Result resolveUrl(Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    private static boolean startXml(Live item, String url, boolean force) {
        try {
            EpgParser.start(item, url, force);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        if (!url.startsWith("http")) return;
        Epg existing = item.getDataList().stream().filter(epg -> epg.equal(date)).findFirst().orElse(null);
        boolean need = existing == null;
        if (!need && offset == 0 && isExpired(existing)) {
            // Today's cached day has fully ended (all programmes in the past)
            // but the server file is regenerated daily: drop it and refetch
            // instead of showing "no programme" until the app restarts.
            Log.i(TAG, "Epg day expired, refetching channel=" + item.getName() + " date=" + date);
            epgJsonCache.invalidate(item.getTvgId() + "|" + date);
            item.removeData(date);
            need = true;
        }
        if (!need) return;
        String cacheKey = item.getTvgId() + "|" + date;
        String json = epgJsonCache.getIfPresent(cacheKey);
        if (json == null) {
            // A dead EPG server must never take down the caller: getEpg is
            // also reached from the media-browse path (LiveBrowse
            // .loadMetadata), which has no error handler. A failed day is
            // remembered for a few minutes so rapid channel zapping does
            // not re-issue the same failing request.
            if (!EPG_GUARD.shouldFetch(cacheKey)) return;
            try {
                json = OkHttp.string(url);
                epgJsonCache.put(cacheKey, json);
                EPG_GUARD.recordSuccess(cacheKey);
            } catch (Exception e) {
                EPG_GUARD.recordFailure(cacheKey);
                e.printStackTrace();
                return;
            }
        }
        item.setData(Epg.objectFrom(json, item.getTvgId(), date, zoneId));
    }

    /** A cached day is expired when it has programmes and the last one already ended. */
    private static boolean isExpired(Epg existing) {
        if (existing == null || existing.getList().isEmpty()) return false;
        long now = System.currentTimeMillis();
        long lastEnd = 0;
        for (EpgData data : existing.getList()) lastEnd = Math.max(lastEnd, data.getEndTime());
        return lastEnd > 0 && lastEnd <= now;
    }
}
