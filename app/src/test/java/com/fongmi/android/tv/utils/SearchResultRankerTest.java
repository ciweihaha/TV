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

    @Test
    public void latencyBreaksTiesWithinSameMatchTier() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("slow", 3000);
        tracker.record("fast", 120);
        tracker.record("mid", 800);
        List<Hit> items = List.of(
                new Hit("星际穿越", "1", "slow"),
                new Hit("星际穿越", "2", "mid"),
                new Hit("星际穿越", "3", "fast"),
                new Hit("星际穿越", "4", "unknown"));
        List<Hit> ranked = SearchResultRanker.rank(items, "星际穿越", h -> h.name, h -> h.id, h -> h.site, h -> tracker.latencyMs(h.site));
        assertEquals(List.of("fast", "mid", "slow", "unknown"), ranked.stream().map(h -> h.site).toList());
    }

    @Test
    public void matchQualityStillBeatsLatency() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("slow-exact", 5000);
        tracker.record("fast-prefix", 50);
        List<Hit> items = List.of(
                new Hit("星际穿越前传", "1", "fast-prefix"),
                new Hit("星际穿越", "2", "slow-exact"));
        List<Hit> ranked = SearchResultRanker.rank(items, "星际穿越", h -> h.name, h -> h.id, h -> h.site, h -> tracker.latencyMs(h.site));
        assertEquals("slow-exact", ranked.get(0).site);
    }

    @Test
    public void emptyKeywordSortsByLatencyOnly() {
        SourceLatencyTracker tracker = new SourceLatencyTracker();
        tracker.record("b", 900);
        tracker.record("a", 100);
        List<Hit> items = List.of(new Hit("b", "1", "b"), new Hit("a", "2", "a"), new Hit("c", "3", "c"));
        List<Hit> ranked = SearchResultRanker.rank(items, "", h -> h.name, h -> h.id, h -> h.site, h -> tracker.latencyMs(h.site));
        assertEquals(List.of("a", "b", "c"), ranked.stream().map(h -> h.site).toList());
    }

    @Test
    public void rankingLongNamesStaysFast() {
        // Scores used to be recomputed (with a fresh normalize) on every
        // comparison; with long names that made ranking quadratic-ish.
        List<Hit> items = new ArrayList<>();
        String pad = "很长的影片标题用来放大每次比较的归一化开销".repeat(4);
        for (int site = 0; site < 20; site++) {
            for (int i = 0; i < 500; i++) items.add(new Hit("影片" + i + pad, String.valueOf(i), "site-" + site));
        }
        long start = System.nanoTime();
        List<Hit> ranked = rank(items, "影片7");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(10_000, ranked.size());
        assertTrue("ranking 10k long-name items took " + elapsedMs + "ms", elapsedMs < 2000);
    }
}
