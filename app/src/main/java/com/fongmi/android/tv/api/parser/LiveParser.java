package com.fongmi.android.tv.api.parser;

import androidx.media3.common.MimeTypes;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Catchup;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.ClearKey;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Auth;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.Response;

public class LiveParser {

    private static final Pattern M3U = Pattern.compile("^(?!.*#genre#).*#EXTM3U.*", Pattern.MULTILINE);

    private static final String GROUP_TOKEN = "group-title=\"";
    private static final String TVG_NAME_TOKEN = "tvg-name=\"";
    private static final String TVG_CHNO_TOKEN = "tvg-chno=\"";
    private static final String TVG_LOGO_TOKEN = "tvg-logo=\"";
    private static final String TVG_ID_TOKEN = "tvg-id=\"";
    private static final String CATCHUP_TOKEN = "catchup=\"";
    private static final String CATCHUP_SOURCE_TOKEN = "catchup-source=\"";
    private static final String CATCHUP_REPLACE_TOKEN = "catchup-replace=\"";
    private static final String TVG_URL_TOKEN = "tvg-url=\"";
    private static final String URL_TVG_TOKEN = "url-tvg=\"";
    private static final String HTTP_USER_AGENT_TOKEN = "http-user-agent=\"";

    private static String extractAttr(String line, String token) {
        int start = line.lastIndexOf(token);
        if (start < 0) return "";
        int valueStart = start + token.length();
        int end = line.indexOf('"', valueStart);
        if (end < 0) return "";
        return line.substring(valueStart, end).trim();
    }

    private static String extractName(String line) {
        String trimmed = line.trim();
        int comma = trimmed.lastIndexOf(',');
        return comma < 0 ? "" : trimmed.substring(comma + 1).trim();
    }

    private static String extract(String line, String... keywords) {
        String[] splits = line.split(" ");
        for (String split : splits) for (String keyword : keywords) if (split.contains(keyword)) {
            // 空值（如 tvg-url=）时 split("=") 只有 1 段，[1] 会抛 ArrayIndexOutOfBoundsException，
            // 整个 m3u() 解析中途失败、直播源一个台都导不进来；用 limit 2 保留值里的 '='（如 EPG 地址的 ?x=1）
            String[] kv = split.split("=", 2);
            return kv.length > 1 ? kv[1].replace("\"", "") : "";
        }
        return "";
    }

    public static void start(Live live) throws Exception {
        if (!live.getGroups().isEmpty()) return;
        String text = getText(live);
        if (Json.isArray(text)) json(live, text);
        else text(live, text);
    }

    private static String getText(Live live) throws Exception {
        live.setAuth(null);
        if (!live.getApi().isEmpty()) return live.spider().liveContent(live.getUrl());
        BaseLoader.get().requireProxy(live.getUrl());
        String url = UrlUtil.convert(live.getUrl());
        if (!url.startsWith("http")) return "";
        Map<String, String> headers = live.getHeaders();
        try (Response response = OkHttp.newCall(url, headers, Auth.Context.NONE).execute()) {
            String text = response.body().string();
            if (live.getUrl().startsWith("proxy://") && !response.isSuccessful()) throw new ExtractException(text);
            if (response.isSuccessful()) live.setAuth(Auth.capture(live.getUrl(), headers, response.request().url().toString(), response.request().header(HttpHeaders.AUTHORIZATION)));
            return text;
        }
    }

    public static void text(Live live, String text) {
        if (!live.getGroups().isEmpty()) return;
        if (M3U.matcher(text).find()) m3u(live, text);
        else txt(live, text);
        apply(live);
    }

    private static void json(Live live, String text) {
        live.getGroups().addAll(Group.arrayFrom(text));
        apply(live);
    }

    private static void apply(Live live) {
        int number = 0;
        for (Group group : live.getGroups()) {
            for (Channel channel : group.trans().getChannel()) {
                if (channel.getNumber().isEmpty()) channel.setNumber(++number);
                channel.trans().live(live);
            }
        }
    }

    private static void m3u(Live live, String text) {
        Setting setting = Setting.create();
        Catchup catchup = Catchup.create();
        Channel channel = Channel.create("");
        text = text.replace("\r\n", "\n").replace("\r", "");
        for (String line : text.split("\n")) {
            if (Thread.interrupted()) break;
            if (setting.find(line)) {
                setting.check(line);
            } else if (line.startsWith("#EXTM3U")) {
                catchup.setType(extractAttr(line, CATCHUP_TOKEN));
                catchup.setSource(extractAttr(line, CATCHUP_SOURCE_TOKEN));
                catchup.setReplace(extractAttr(line, CATCHUP_REPLACE_TOKEN));
                if (live.getEpg().isEmpty()) live.setEpg(extractAttr(line, TVG_URL_TOKEN).replace("\"", ""));
                if (live.getEpg().isEmpty()) live.setEpg(extractAttr(line, URL_TVG_TOKEN).replace("\"", ""));
                if (live.getEpg().isEmpty()) live.setEpg(extract(line, "tvg-url=", "url-tvg="));
            } else if (line.startsWith("#EXTINF:")) {
                Group group = live.find(Group.create(extractAttr(line, GROUP_TOKEN), live.isPass()));
                channel = group.find(Channel.create(extractName(line)));
                channel.setUa(extractAttr(line, HTTP_USER_AGENT_TOKEN));
                channel.setTvgName(extractAttr(line, TVG_NAME_TOKEN));
                channel.setNumber(extractAttr(line, TVG_CHNO_TOKEN));
                channel.setLogo(extractAttr(line, TVG_LOGO_TOKEN));
                channel.setTvgId(extractAttr(line, TVG_ID_TOKEN));
                Catchup unknown = Catchup.create();
                unknown.setType(extractAttr(line, CATCHUP_TOKEN));
                unknown.setSource(extractAttr(line, CATCHUP_SOURCE_TOKEN));
                unknown.setReplace(extractAttr(line, CATCHUP_REPLACE_TOKEN));
                channel.setCatchup(Catchup.decide(unknown, catchup));
            } else if (!line.startsWith("#") && line.contains("://")) {
                int pipe = line.indexOf('|');
                if (pipe >= 0) setting.headers(line.substring(pipe + 1));
                channel.getUrls().add(pipe < 0 ? line : line.substring(0, pipe));
                setting.copy(channel).clear();
            }
        }
    }

    private static void txt(Live live, String text) {
        Setting setting = Setting.create();
        text = text.replace("\r\n", "\n").replace("\r", "");
        for (String line : text.split("\n")) {
            if (Thread.interrupted()) break;
            if (line.isEmpty()) continue;
            String[] split = line.split(",", 2);
            if (setting.find(line)) setting.check(line);
            if (line.contains("#genre#")) setting.clear();
            if (line.contains("#genre#")) live.getGroups().add(Group.create(split[0], live.isPass()));
            if (split.length > 1 && live.getGroups().isEmpty()) live.getGroups().add(Group.create());
            if (split.length > 1 && split[1].contains("://")) {
                Group group = live.getGroups().get(live.getGroups().size() - 1);
                Channel channel = group.find(Channel.create(split[0]));
                for (String url : split[1].split("#")) {
                    int pipe = url.indexOf('|');
                    if (pipe >= 0) setting.headers(url.substring(pipe + 1));
                    channel.getUrls().add(pipe < 0 ? url : url.substring(0, pipe));
                    setting.copy(channel);
                }
            }
        }
    }

    private static class Setting {

        private static final Pattern PIPE = Pattern.compile("\\|");

        private String ua;
        private String key;
        private String type;
        private String click;
        private String format;
        private String origin;
        private String referer;
        private Integer parse;
        private boolean forceKey;
        private Map<String, String> header;
        private Map<String, String> drmHeader;

        private static Setting create() {
            return new Setting();
        }

        public Setting() {
            header = new HashMap<>();
            drmHeader = new HashMap<>();
        }

        private boolean find(String line) {
            return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click") || line.startsWith("header") || line.startsWith("format") || line.startsWith("origin") || line.startsWith("referer") || line.startsWith("forceKey") || line.startsWith("#EXTHTTP:") || line.startsWith("#EXTVLCOPT:") || line.startsWith("#KODIPROP:");
        }

        private void check(String line) {
            if (line.startsWith("ua")) ua(line);
            else if (line.startsWith("parse")) parse(line);
            else if (line.startsWith("click")) click(line);
            else if (line.startsWith("header")) header(line);
            else if (line.startsWith("format")) format(line);
            else if (line.startsWith("origin")) origin(line);
            else if (line.startsWith("referer")) referer(line);
            else if (line.startsWith("#EXTHTTP:")) header(line);
            else if (line.startsWith("forceKey")) forceKey(line);
            else if (line.startsWith("#EXTVLCOPT:http-cookie")) cookie(line);
            else if (line.startsWith("#EXTVLCOPT:http-origin")) origin(line);
            else if (line.startsWith("#EXTVLCOPT:http-user-agent")) ua(line);
            else if (line.startsWith("#EXTVLCOPT:http-referrer")) referrer(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key")) key(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type")) type(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.drm_legacy")) drmLegacy(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.manifest_type")) format(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.stream_headers")) headers(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.common_headers")) headers(line);
        }

        private Setting copy(Channel channel) {
            if (ua != null) channel.setUa(ua);
            if (parse != null) channel.setParse(parse);
            if (click != null) channel.setClick(click);
            if (format != null) channel.setFormat(format);
            if (origin != null) channel.setOrigin(origin);
            if (referer != null) channel.setReferer(referer);
            if (!header.isEmpty()) channel.setHeader(header);
            if (key != null && type != null) channel.setDrm(Drm.create(key, type, drmHeader, forceKey));
            return this;
        }

        private static String valueAfter(String line, String key) {
            int index = line.indexOf(key);
            if (index < 0) return null;
            String value = line.substring(index + key.length()).trim();
            return value.isEmpty() ? null : value;
        }

        private static String valueAfterIgnoreCase(String line, String key) {
            int index = indexOfIgnoreCase(line, key);
            if (index < 0) return null;
            String value = line.substring(index + key.length()).trim();
            return value.isEmpty() ? null : value;
        }

        private static int indexOfIgnoreCase(String text, String key) {
            int max = text.length() - key.length();
            for (int i = 0; i <= max; i++) if (text.regionMatches(true, i, key, 0, key.length())) return i;
            return -1;
        }

        private static String unquote(String value) {
            return value == null ? null : value.replace("\"", "");
        }

        private void ua(String line) {
            if (line.contains("user-agent=")) ua = unquote(valueAfter(line, "user-agent="));
            if (line.contains("ua=")) ua = unquote(valueAfter(line, "ua="));
        }

        private void referer(String line) {
            referer = unquote(valueAfterIgnoreCase(line, "referer="));
        }

        private void referrer(String line) {
            referer = unquote(valueAfterIgnoreCase(line, "referrer="));
        }

        private void parse(String line) {
            try {
                String value = valueAfter(line, "parse=");
                parse = value == null ? null : Integer.parseInt(value);
            } catch (Exception e) {
                e.printStackTrace();
                parse = null;
            }
        }

        private void click(String line) {
            click = valueAfter(line, "click=");
        }

        private void format(String line) {
            if (line.startsWith("format=")) format = valueAfter(line, "format=");
            if (line.contains("manifest_type=")) format = valueAfter(line, "manifest_type=");
            if ("mpd".equals(format) || "dash".equals(format)) format = MimeTypes.APPLICATION_MPD;
            if ("hls".equals(format)) format = MimeTypes.APPLICATION_M3U8;
        }

        private void cookie(String line) {
            String value = unquote(valueAfterIgnoreCase(line, "cookie="));
            if (value != null) header.put("Cookie", value);
        }

        private void origin(String line) {
            origin = valueAfterIgnoreCase(line, "origin=");
        }

        private void key(String line) {
            try {
                key = line.contains("license_key=") ? valueAfter(line, "license_key=") : line;
                if (key == null) return;
                if (key.startsWith("http")) httpKey();
                else localKey();
            } catch (Exception e) {
                e.printStackTrace();
                key = null;
            }
        }

        private void type(String line) {
            type = line.contains("license_type=") ? valueAfter(line, "license_type=") : line;
        }

        private void drmLegacy(String line) {
            String value = valueAfter(line, "drm_legacy=");
            int pipe = value == null ? -1 : value.indexOf('|');
            if (pipe < 0) {
                type = null;
                key = null;
                return;
            }
            type(value.substring(0, pipe).trim());
            key(value.substring(pipe + 1).trim());
        }

        private void header(String line) {
            try {
                if (line.contains("#EXTHTTP:")) {
                    String value = valueAfter(line, "#EXTHTTP:");
                    if (value != null) header.putAll(Json.toMap(Json.parse(value)));
                }
                if (line.contains("header=")) {
                    String value = valueAfter(line, "header=");
                    if (value != null) header.putAll(Json.toMap(Json.parse(value)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void headers(String line) {
            try {
                if (line.contains("headers=")) {
                    String value = valueAfter(line, "headers=");
                    if (value != null) headers(header, value.split("&"));
                } else if (line.contains("|")) for (String text : PIPE.split(line)) headers(text);
                else headers(header, line.trim().split("&"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void drmHeaders(String line) {
            try {
                if (line.contains("|")) for (String text : PIPE.split(line)) drmHeaders(text);
                else headers(drmHeader, line.trim().split("&"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void headers(Map<String, String> map, String[] params) {
            for (String param : params) {
                if (!param.contains("=")) continue;
                String[] a = param.split("=", 2);
                String k = a[0].trim().replace("\"", "");
                String v = a[1].trim().replace("\"", "");
                if ("drmScheme".equals(k)) type(v);
                else if ("drmLicense".equals(k)) key(v);
                else map.put(k, v);
            }
        }

        private void forceKey(String line) {
            String value = valueAfter(line, "forceKey=");
            if (value != null) forceKey = Boolean.parseBoolean(value);
        }

        private void httpKey() {
            int pipe = key.indexOf('|');
            if (pipe >= 0) {
                drmHeaders(key.substring(pipe + 1));
                key = key.substring(0, pipe).trim();
            } else {
                key = key.trim();
            }
        }

        private void localKey() {
            try {
                ClearKey.objectFrom(key);
            } catch (Exception e) {
                key = ClearKey.get(key.replace("\"", "").replace("{", "").replace("}", "")).toString();
            }
        }

        private void clear() {
            ua = null;
            key = null;
            type = null;
            parse = null;
            click = null;
            format = null;
            origin = null;
            referer = null;
            forceKey = false;
            header = new HashMap<>();
            drmHeader = new HashMap<>();
        }
    }
}
