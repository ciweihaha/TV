package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Ranks aggregated search results.
 *
 * <p>Search fans out to every searchable site, so the same title comes back
 * once per site (and sometimes twice from one site). Previously the fallback
 * list showed those duplicates in arrival order, with an exact-title match
 * buried behind partial matches. This helper de-duplicates (same site +
 * same id, or same site + same title when the id is empty) and orders exact
 * matches first, then prefix matches, then substring matches.
 *
 * <p>Within one match tier, items are ordered by measured source latency
 * (see {@link SourceLatencyTracker}) when a latency function is supplied:
 * the fastest source is tried first instead of whichever site happened to
 * answer first. Sites with no measurement yet sort after measured ones.
 *
 * <p>Scores and normalized names are computed once per item before sorting
 * instead of once per comparison, so ranking a large result set is linear
 * in the number of items plus the sort itself.
 *
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class SearchResultRanker {

    private SearchResultRanker() {
    }

    public static <T> List<T> rank(List<T> items, String keyword, Function<T, String> name, Function<T, String> id, Function<T, String> siteKey) {
        return rank(items, keyword, name, id, siteKey, null);
    }

    public static <T> List<T> rank(List<T> items, String keyword, Function<T, String> name, Function<T, String> id, Function<T, String> siteKey, ToLongFunction<T> latencyMs) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        Map<String, T> unique = new LinkedHashMap<>();
        for (T item : items) {
            if (item == null) continue;
            String key = dedupKey(name.apply(item), id.apply(item), siteKey.apply(item));
            unique.putIfAbsent(key, item);
        }
        List<T> ranked = new ArrayList<>(unique.values());
        String query = normalize(keyword);
        if (query.isEmpty()) {
            if (latencyMs != null) ranked.sort((a, b) -> Long.compare(latencyMs.applyAsLong(a), latencyMs.applyAsLong(b)));
            return ranked;
        }
        List<Scored<T>> scored = new ArrayList<>(ranked.size());
        for (T item : ranked) scored.add(new Scored<>(item, score(normalize(name.apply(item)), query), latencyMs == null ? 0 : latencyMs.applyAsLong(item)));
        scored.sort((a, b) -> {
            int byScore = Integer.compare(a.score, b.score);
            if (byScore != 0) return byScore;
            return Long.compare(a.latencyMs, b.latencyMs);
        });
        List<T> result = new ArrayList<>(scored.size());
        for (Scored<T> entry : scored) result.add(entry.item);
        return result;
    }

    private static String dedupKey(String name, String id, String siteKey) {
        String site = siteKey == null ? "" : siteKey;
        String identity = id != null && !id.trim().isEmpty() ? id.trim() : normalize(name);
        return site + "\0" + identity;
    }

    private static int score(String normalizedName, String query) {
        if (normalizedName.equals(query)) return 0;
        if (normalizedName.startsWith(query)) return 1;
        if (normalizedName.contains(query)) return 2;
        return 3;
    }

    static String normalize(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    private record Scored<T>(T item, int score, long latencyMs) {
    }
}
