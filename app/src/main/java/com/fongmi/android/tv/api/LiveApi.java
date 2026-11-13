package com.fongmi.android.tv.api;

import androidx.annotation.NonNull;

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
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public class LiveApi {

    /** JSON EPG cache (P2): parsed data is valid for the whole day, so one
     * download per (channel, date) is enough. The XMLTV path already has a
     * 6h disk cache; this covers the JSON path (SCF output) which had none. */
    private static final Cache<String, String> epgJsonCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).maximumSize(512).build();

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        boolean parsed = false;
        for (String url : item.getEpgXml()) {
            if (item.tryMarkEpgXmlAttempted(url)) continue;
            if (startXml(item, url)) parsed = true;
            else item.markEpgXmlFailed(url);
        }
        return parsed;
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

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (!need) return;
        String cacheKey = item.getTvgId() + "|" + date;
        String json = epgJsonCache.getIfPresent(cacheKey);
        if (json == null) {
            json = OkHttp.string(url);
            epgJsonCache.put(cacheKey, json);
        }
        item.setData(Epg.objectFrom(json, item.getTvgId(), date, zoneId));
    }
}
