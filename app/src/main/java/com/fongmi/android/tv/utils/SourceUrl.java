package com.fongmi.android.tv.utils;

import android.net.Uri;

public record SourceUrl(String id, String target) {

    private static final String PREFIX = "source://episode?";

    public SourceUrl {
        if (id == null || id.isEmpty() || target == null || target.isEmpty()) throw new IllegalArgumentException("Invalid source selection");
    }

    public static boolean match(String url) {
        return url.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    public static SourceUrl from(String url) {
        if (!match(url)) throw new IllegalArgumentException("Invalid source selection");
        Uri uri = Uri.parse(url);
        return new SourceUrl(uri.getQueryParameter("id"), uri.getQueryParameter("target"));
    }

    public static boolean isResolved(String url) {
        return match(url) || TorrentUrl.match(url);
    }

    public static String original(String url) {
        if (!match(url)) return TorrentUrl.original(url);
        try {
            return from(url).id();
        } catch (IllegalArgumentException e) {
            // 残缺的 source://episode 链接（缺 id/target）按原样返回：调用方（选集替换、历史恢复、
            // 播放 id 归一）只拿它做 equals 比对，原样返回即“比对不上”，不会闪退；第 133 轮只守住了 same()
            return url;
        }
    }

    public static boolean same(String first, String second) {
        try {
            if (match(first) && match(second)) return from(first).equals(from(second));
            return TorrentUrl.match(first) && TorrentUrl.match(second) && TorrentUrl.from(first).equals(TorrentUrl.from(second));
        } catch (IllegalArgumentException e) {
            // 残缺的 source:// 或 magnet://file 链接（缺 id/target/index，或 index 不是数字）按“不是同一集”处理：
            // TorrentUrl.from() 里 Integer.parseInt(null) 会抛 NumberFormatException（是 IllegalArgumentException 的子类），
            // 若在此处抛出，播放中更新历史（VodHistoryPolicy.matchesEpisode）与选集过滤（Source）都会直接闪退/报错
            return false;
        }
    }

    public static String restore(String saved, String id) {
        if (match(saved)) {
            try {
                return from(saved).id().equals(id) ? saved : null;
            } catch (IllegalArgumentException e) {
                // 残缺的 source://episode 链接无法还原成有效选集，按“还原失败”处理（调用方继续走其他恢复路径）
                return null;
            }
        }
        return TorrentUrl.restore(saved, id);
    }

    public String url() {
        return new Uri.Builder().scheme("source").authority("episode").appendQueryParameter("id", id).appendQueryParameter("target", target).build().toString();
    }
}
