package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Class;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类归并：界面上只显示电影 / 电视剧 / 动画片 / 综艺四个 tab，
 * 站点的其他分类按名字规则并进这四个主分类；被并进去的分类的内容
 * 在对应主分类下一起拉取、按视频去重后展示，不会丢失。
 * <p>
 * 规则是通用的（只看分类名里的关键词），不针对任何一个站写死列表：
 * 动漫 / 动画 / 卡通 / 番剧 → 动画片；综艺 / 真人秀 / 选秀 → 综艺；
 * 电影 / 影片 / 院线 → 电影；电视剧 / 剧集 / 国产剧 / 韩剧 / 美剧…
 * 或名字里含“剧”的剧种（古装剧、悬疑剧…）→ 电视剧；
 * 其余主题合集（悬疑 / 科幻 / 动作 / 喜剧 / 爱情 / 恐怖 / 战争 / 合家欢 /
 * 治愈 / 经典…）按内容归属并进电影——这类合集在 CMS 站上绝大多数是电影；
 * 未识别的名字也默认并进电影，保证没有任何分类的内容丢失。
 * <p>
 * 主分类的代表项优先取本站同名分类（电影取“电影”，动画片取“动漫/动画片”），
 * 没有同名分类时取该桶的第一个分类；主分类的筛选面板用代表项的筛选定义。
 */
public class CategoryMerger {

    public static final String MOVIE = "电影";
    public static final String TV = "电视剧";
    public static final String ANIME = "动画片";
    public static final String VARIETY = "综艺";

    private static final String[] ORDER = {MOVIE, TV, ANIME, VARIETY};

    private static final String[] ANIME_KEYS = {"动漫", "动画", "卡通", "番剧", "国漫", "日漫", "動漫"};
    private static final String[] VARIETY_KEYS = {"综艺", "真人秀", "选秀", "脱口秀", "晚会", "演唱会", "綜藝"};
    private static final String[] MOVIE_KEYS = {"电影", "影片", "院线", "電影"};
    private static final String[] TV_KEYS = {"电视剧", "剧集", "国产剧", "韩剧", "美剧", "日剧", "港剧", "台剧", "泰剧", "英剧", "海外剧", "電視劇"};
    /** 电影体裁词：名字里含“剧”但实际是电影体裁（喜剧、剧情…），须先于裸“剧”判断。 */
    private static final String[] MOVIE_GENRE_KEYS = {"喜剧", "剧情", "悲剧", "闹剧", "歌剧", "舞剧"};
    private static final String[] GENRE_KEYS = {"悬疑", "科幻", "动作", "喜剧", "爱情", "恐怖", "战争", "犯罪", "剧情", "冒险", "奇幻", "古装", "武侠", "青春", "家庭", "合家欢", "治愈", "经典", "纪录", "历史", "传记", "音乐", "歌舞", "体育", "运动", "儿童", "少儿", "网络大电影"};

    private static final Map<String, List<String>> PRIMARY_NAMES = new HashMap<>();

    static {
        PRIMARY_NAMES.put(MOVIE, Arrays.asList("电影"));
        PRIMARY_NAMES.put(TV, Arrays.asList("电视剧"));
        PRIMARY_NAMES.put(ANIME, Arrays.asList("动漫", "动画片", "动画"));
        PRIMARY_NAMES.put(VARIETY, Arrays.asList("综艺"));
    }

    /** 站点 key + 主分类 typeId → 被并进来的全部成员 typeId（主分类自身排第一）。 */
    private static final Map<String, List<String>> REGISTRY = new HashMap<>();

    private CategoryMerger() {
    }

    /** 分类名属于哪个主分类。 */
    public static String bucketOf(String name) {
        if (name == null) return MOVIE;
        String n = name.trim();
        if (containsAny(n, ANIME_KEYS)) return ANIME;
        if (containsAny(n, VARIETY_KEYS)) return VARIETY;
        if (containsAny(n, MOVIE_KEYS)) return MOVIE;
        if (containsAny(n, TV_KEYS)) return TV;
        if (containsAny(n, MOVIE_GENRE_KEYS)) return MOVIE;
        if (n.contains("剧")) return TV;
        if (containsAny(n, GENRE_KEYS)) return MOVIE;
        return MOVIE;
    }

    /**
     * 把站点的分类列表归并成最多四个主分类（按电影 / 电视剧 / 动画片 / 综艺顺序，
     * 本站没有的分类不显示空 tab），并登记各主分类的成员 typeId。
     * 返回的是拷贝，主分类显示名为标准名（动漫显示为动画片），原分类对象不动。
     */
    public static List<Class> merge(String key, List<Class> types) {
        if (types == null) return new ArrayList<>();
        String prefix = key + "|";
        REGISTRY.keySet().removeIf(k -> k.startsWith(prefix));
        LinkedHashMap<String, List<Class>> buckets = new LinkedHashMap<>();
        for (String name : ORDER) buckets.put(name, new ArrayList<>());
        List<Class> folders = new ArrayList<>();
        for (Class type : types) {
            // 文件夹（从墙上点开的子目录）不是分类，不参与归并，原样保留
            if (type.isFolder()) folders.add(type);
            else buckets.get(bucketOf(type.getTypeName())).add(type);
        }
        List<Class> merged = new ArrayList<>();
        for (Map.Entry<String, List<Class>> entry : buckets.entrySet()) {
            List<Class> members = entry.getValue();
            if (members.isEmpty()) continue;
            Class primary = pickPrimary(members, entry.getKey());
            if (primary.getTypeId().isEmpty()) continue;
            Class bucket = primary.copy();
            bucket.setTypeName(entry.getKey());
            List<String> ids = new ArrayList<>();
            ids.add(primary.getTypeId());
            for (Class member : members) {
                if (!member.getTypeId().isEmpty() && !ids.contains(member.getTypeId())) ids.add(member.getTypeId());
            }
            REGISTRY.put(prefix + primary.getTypeId(), ids);
            merged.add(bucket);
        }
        merged.addAll(folders);
        return merged;
    }

    /** 取回某主分类的成员 typeId（主分类自身排第一），未登记时返回空列表。 */
    public static List<String> getMergedIds(String key, String typeId) {
        List<String> ids = REGISTRY.get(key + "|" + typeId);
        return ids == null ? Collections.emptyList() : ids;
    }

    private static Class pickPrimary(List<Class> members, String bucket) {
        List<String> names = PRIMARY_NAMES.get(bucket);
        for (Class member : members) {
            if (names.contains(member.getTypeName()) && !member.getTypeId().isEmpty()) return member;
        }
        for (Class member : members) if (!member.getTypeId().isEmpty()) return member;
        return members.get(0);
    }

    private static boolean containsAny(String name, String[] keys) {
        for (String key : keys) if (name.contains(key)) return true;
        return false;
    }
}
