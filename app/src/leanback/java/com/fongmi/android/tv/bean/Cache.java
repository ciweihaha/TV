package com.fongmi.android.tv.bean;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Cache {

    private final Map<String, List<Filter>> cache;

    private static class Loader {
        static volatile Cache INSTANCE = new Cache();
    }

    private static Cache get() {
        return Loader.INSTANCE;
    }

    public Cache() {
        cache = Collections.synchronizedMap(new HashMap<>());
    }

    /** 站点 key + 分类 typeId 拼成的缓存键：两站同 typeId 不再共用同一份筛选定义。 */
    private static String scopedKey(String siteKey, String typeId) {
        return siteKey + "|" + typeId;
    }

    /** 当前全部筛选定义的深拷贝快照，用于刷新失败/空结果时回滚（键已按站隔离，快照原样保留）。 */
    public static Map<String, List<Filter>> snapshot() {
        Map<String, List<Filter>> copy = new HashMap<>();
        get().cache.forEach((key, filters) -> copy.put(key, filters.stream().filter(Objects::nonNull).map(Filter::copy).toList()));
        return copy;
    }

    /** 用快照恢复筛选定义（回滚用）。 */
    public static void restore(Map<String, List<Filter>> snapshot) {
        if (snapshot == null) return;
        get().cache.clear();
        get().cache.putAll(snapshot);
    }

    /**
     * 存入某站的筛选定义：只替换该站的旧条目，不动其他站的。
     * 以前是 clear() 全清再 put，两站同 typeId 时后加载的站会覆盖先加载的站。
     */
    public static void put(Result result, String siteKey) {
        if (result == null || siteKey == null) return;
        String prefix = siteKey + "|";
        get().cache.keySet().removeIf(key -> key.startsWith(prefix));
        result.getTypes().forEach(type -> get().cache.put(scopedKey(siteKey, type.getTypeId()), type.getFilters()));
    }

    public static boolean contains(String siteKey, String typeId) {
        return get().cache.containsKey(scopedKey(siteKey, typeId));
    }

    public static List<Filter> get(String siteKey, Class type) {
        return get(siteKey, type.getTypeId());
    }

    public static List<Filter> get(String siteKey, String typeId) {
        List<Filter> filters = get().cache.get(scopedKey(siteKey, typeId));
        return filters == null ? Collections.emptyList() : filters;
    }

    public static List<Filter> copy(String siteKey, String typeId) {
        List<Filter> filters = get().cache.get(scopedKey(siteKey, typeId));
        if (filters == null) return Collections.emptyList();
        return filters.stream().map(Filter::copy).toList();
    }
}
