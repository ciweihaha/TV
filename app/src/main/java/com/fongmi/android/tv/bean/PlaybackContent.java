package com.fongmi.android.tv.bean;

import androidx.media3.common.C;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record PlaybackContent(long primaryStartTimeUs, long primaryEndTimeUs, String primary, long secondaryStartTimeUs, long secondaryEndTimeUs, String secondary, boolean danmaku) {

    public static final Comparator<PlaybackContent> BY_TIME = Comparator.comparingLong(PlaybackContent::startTimeUs).thenComparingLong(PlaybackContent::endTimeUs);

    public PlaybackContent(long startTimeUs, long endTimeUs, String primary, String secondary, boolean danmaku) {
        this(startTimeUs, endTimeUs, primary, startTimeUs, endTimeUs, secondary, danmaku);
    }

    public long startTimeUs() {
        if (secondary.isEmpty()) return primaryStartTimeUs;
        if (primary.isEmpty()) return secondaryStartTimeUs;
        return Math.min(primaryStartTimeUs, secondaryStartTimeUs);
    }

    public long endTimeUs() {
        if (secondary.isEmpty()) return primaryEndTimeUs;
        if (primary.isEmpty()) return secondaryEndTimeUs;
        return primaryEndTimeUs == C.TIME_UNSET || secondaryEndTimeUs == C.TIME_UNSET ? C.TIME_UNSET : Math.max(primaryEndTimeUs, secondaryEndTimeUs);
    }

    public long getPosition() {
        return Math.max(0, startTimeUs() / 1000);
    }

    public long startTimeSeconds() {
        return Math.floorDiv(startTimeUs(), C.MICROS_PER_SECOND);
    }

    public long getId() {
        long id = 0xcbf29ce484222325L;
        id = hash(id, String.valueOf(primaryStartTimeUs));
        id = hash(id, '\n');
        id = hash(id, String.valueOf(primaryEndTimeUs));
        id = hash(id, '\n');
        id = hash(id, String.valueOf(secondaryStartTimeUs));
        id = hash(id, '\n');
        id = hash(id, String.valueOf(secondaryEndTimeUs));
        id = hash(id, '\n');
        id = hash(id, String.valueOf(danmaku));
        id = hash(id, '\n');
        id = hash(id, String.valueOf(primary.length()));
        id = hash(id, ':');
        id = hash(id, primary);
        id = hash(id, secondary);
        return id;
    }

    private static long hash(long id, CharSequence value) {
        for (int i = 0; i < value.length(); i++) id = (id ^ value.charAt(i)) * 0x100000001b3L;
        return id;
    }

    private static long hash(long id, char c) {
        return (id ^ c) * 0x100000001b3L;
    }

    public String getTime() {
        return format(danmaku ? getPosition() / 1000 * 1000 : getPosition());
    }

    public String getTimeRange() {
        return danmaku ? getTime() : timeRange(startTimeUs(), endTimeUs());
    }

    public String getPrimaryTimeRange() {
        return timeRange(primaryStartTimeUs, primaryEndTimeUs);
    }

    public String getSecondaryTimeRange() {
        return timeRange(secondaryStartTimeUs, secondaryEndTimeUs);
    }

    private static String timeRange(long startTimeUs, long endTimeUs) {
        return format(Math.max(0, startTimeUs / 1000)) + (endTimeUs == C.TIME_UNSET ? "" : " – " + format(Math.max(0, endTimeUs / 1000)));
    }

    private static String format(long time) {
        long seconds = time / 1000;
        long millis = time % 1000;
        StringBuilder sb = new StringBuilder();
        if (seconds >= 3600) {
            sb.append(seconds / 3600).append(':');
            appendTwoDigits(sb, seconds / 60 % 60);
            sb.append(':');
            appendTwoDigits(sb, seconds % 60);
        } else {
            appendTwoDigits(sb, seconds / 60);
            sb.append(':');
            appendTwoDigits(sb, seconds % 60);
        }
        if (millis != 0) {
            sb.append('.');
            if (millis < 10) sb.append("00");
            else if (millis < 100) sb.append('0');
            sb.append(millis);
        }
        return sb.toString();
    }

    private static void appendTwoDigits(StringBuilder sb, long value) {
        if (value < 10) sb.append('0');
        sb.append(value);
    }

    public boolean isActive(long positionMs) {
        return isPrimaryActive(positionMs) || isSecondaryActive(positionMs);
    }

    public boolean isPrimaryActive(long positionMs) {
        return !primary.isEmpty() && isActive(primaryStartTimeUs, primaryEndTimeUs, positionMs);
    }

    public boolean isSecondaryActive(long positionMs) {
        return !secondary.isEmpty() && isActive(secondaryStartTimeUs, secondaryEndTimeUs, positionMs);
    }

    private static boolean isActive(long startTimeUs, long endTimeUs, long positionMs) {
        long positionUs = positionMs * 1000;
        return positionUs >= startTimeUs && endTimeUs != C.TIME_UNSET && positionUs < endTimeUs;
    }

    public boolean matches(String keyword) {
        String query = keyword.trim();
        return indexOf(primary, query, 0) >= 0 || indexOf(secondary, query, 0) >= 0;
    }

    public static int indexOf(String text, String keyword, int fromIndex) {
        for (int i = fromIndex; i <= text.length() - keyword.length(); i++) if (text.regionMatches(true, i, keyword, 0, keyword.length())) return i;
        return -1;
    }

    public static List<PlaybackContent> collapseDanmaku(List<PlaybackContent> items) {
        List<PlaybackContent> result = new ArrayList<>(items);
        Set<DanmakuKey> seen = new HashSet<>();
        result.sort(BY_TIME);
        result.removeIf(item -> item.danmaku && !seen.add(new DanmakuKey(item.startTimeSeconds(), item.primary)));
        return result;
    }

    private record DanmakuKey(long second, String text) {
    }

    public PlaybackContent offset(long offsetMs) {
        long offsetUs = offsetMs * 1000;
        return new PlaybackContent(primaryStartTimeUs + offsetUs, primaryEndTimeUs == C.TIME_UNSET ? C.TIME_UNSET : primaryEndTimeUs + offsetUs, primary, secondaryStartTimeUs + offsetUs, secondaryEndTimeUs == C.TIME_UNSET ? C.TIME_UNSET : secondaryEndTimeUs + offsetUs, secondary, danmaku);
    }

    public static List<PlaybackContent> merge(List<PlaybackContent> primary, List<PlaybackContent> secondary) {
        List<PlaybackContent> result = new ArrayList<>(primary.size() + secondary.size());
        int i = 0;
        int j = 0;
        while (i < primary.size() && j < secondary.size()) {
            PlaybackContent first = primary.get(i);
            PlaybackContent second = secondary.get(j);
            int order = Long.compare(Math.floorDiv(first.startTimeUs(), 1000), Math.floorDiv(second.startTimeUs(), 1000));
            if (order == 0) {
                result.add(new PlaybackContent(first.primaryStartTimeUs, first.primaryEndTimeUs, first.primary, second.secondaryStartTimeUs, second.secondaryEndTimeUs, second.secondary, false));
                i++;
                j++;
            } else if (order <= 0) {
                result.add(primary.get(i++));
            } else {
                result.add(secondary.get(j++));
            }
        }
        result.addAll(primary.subList(i, primary.size()));
        result.addAll(secondary.subList(j, secondary.size()));
        result.sort(BY_TIME);
        return result;
    }
}
