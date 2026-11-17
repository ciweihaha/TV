package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SearchResultRankerTest {

    private static class Hit {
        final String name;
        final String id;
        final String site;

        Hit(String name, String id, String site) {
            this.name = name;
            this.id = id;
            this.site = site;
        }
    }

    private static List<Hit> rank(List<Hit> items, String keyword) {
        return SearchResultRanker.rank(items, keyword, h -> h.name, h -> h.id, h -> h.site);
    }

    private static List<String> names(List<Hit> hits) {
        List<String> names = new ArrayList<>();
        for (Hit hit : hits) names.add(hit.name);
        return names;
    }

    @Test
    public void exactMatchFirst() {
        List<Hit> items = List.of(
                new Hit("星际穿越 2024", "1", "a"),
                new Hit("星际穿越", "2", "a"),
                new Hit("星际", "3", "a"));
        assertEquals(List.of("星际穿越", "星际穿越 2024", "星际"), names(rank(items, "星际穿越")));
    }

    @Test
    public void dedupsSameSiteSameId() {
        List<Hit> items = List.of(
                new Hit("星际穿越", "1", "a"),
                new Hit("星际穿越", "1", "a"),
                new Hit("流浪地球", "2", "a"));
        assertEquals(2, rank(items, "星际穿越").size());
    }

    @Test
    public void keepsSameTitleFromDifferentSites() {
        List<Hit> items = List.of(
                new Hit("星际穿越", "1", "a"),
                new Hit("星际穿越", "9", "b"));
        assertEquals(2, rank(items, "星际穿越").size());
    }

    @Test
    public void dedupsByNameWhenIdEmpty() {
        List<Hit> items = List.of(
                new Hit("星际穿越", "", "a"),
                new Hit("星际穿越 ", "", "a"),
                new Hit(" 流浪地球", "", "a"));
        assertEquals(2, rank(items, "星际").size());
    }

    @Test
    public void nullAndEmptyKeywordKeepsOrder() {
        List<Hit> items = List.of(new Hit("b", "1", "a"), new Hit("a", "2", "a"), new Hit("c", "3", "a"));
        assertEquals(items, rank(items, ""));
        assertEquals(items, rank(items, null));
    }

    @Test
    public void stressManySitesAndDuplicates() {
        List<Hit> items = new ArrayList<>();
        for (int site = 0; site < 40; site++) {
            for (int i = 0; i < 250; i++) items.add(new Hit("影片" + (i % 50), String.valueOf(i % 50), "site-" + site));
        }
        long start = System.nanoTime();
        List<Hit> ranked = rank(items, "影片7");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 10,000 raw hits collapse to 40 sites x 50 titles.
        assertEquals(2000, ranked.size());
        assertTrue("first must be an exact/prefix match, got " + ranked.get(0).name, ranked.get(0).name.startsWith("影片7"));
        assertTrue("ranking 10k items took " + elapsedMs + "ms", elapsedMs < 2000);
    }
}
