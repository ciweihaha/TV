package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

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
 * <p>Pure JDK, no Android dependency, so it is covered by unit tests.
 */
public final class SearchResultRanker {

    private SearchResultRanker() {
    }

    public static <T> List<T> rank(List<T> items, String keyword, Function<T, String> name, Function<T, String> id, Function<T, String> siteKey) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        Map<String, T> unique = new LinkedHashMap<>();
        for (T item : items) {
            if (item == null) continue;
            String key = dedupKey(name.apply(item), id.apply(item), siteKey.apply(item));
            unique.putIfAbsent(key, item);
        }
        List<T> ranked = new ArrayList<>(unique.values());
        String query = normalize(keyword);
        if (!query.isEmpty()) ranked.sort((a, b) -> Integer.compare(score(name.apply(a), query), score(name.apply(b), query)));
        return ranked;
    }

    private static String dedupKey(String name, String id, String siteKey) {
        String site = siteKey == null ? "" : siteKey;
        String identity = id != null && !id.trim().isEmpty() ? id.trim() : normalize(name);
        return site + "\0" + identity;
    }

    private static int score(String name, String query) {
        String value = normalize(name);
        if (value.equals(query)) return 0;
        if (value.startsWith(query)) return 1;
        if (value.contains(query)) return 2;
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
}
