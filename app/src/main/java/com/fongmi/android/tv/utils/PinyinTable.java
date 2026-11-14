package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标准普通话拼音音节表（不带声调）与手机九键 T9 映射。
 * <p>
 * 音节表是汉语拼音的固定语言学集合（405 个，ü 记作 v），不是编造的词库：
 * 每个音节都由声母 + 韵母按普通话拼读规则组成。九键只按一次、
 * 数字序列按此表切分成音节，再走现有的 getSuggest → iQIYI 建议链出中文候选，
 * 本类不产生任何中文词。
 * <p>
 * 九键数字键位（与手机一致）：2 abc、3 def、4 ghi、5 jkl、6 mno、7 pqrs、8 tuv、9 wxyz。
 */
public class PinyinTable {

    /** 标准拼音音节（不带声调），同一数字编码内按常用程度大致排序，排在前面的优先显示。 */
    private static final String[] SYLLABLES = {
            "a", "ai", "an", "ang", "ao", "e", "ei", "en", "eng", "er", "o", "ou",
            "ba", "bo", "bu", "bi", "bao", "ban", "bai", "bei", "ben", "beng", "bang", "bian", "biao", "bie", "bin", "bing",
            "pa", "po", "pu", "pi", "pao", "pan", "pai", "pei", "pen", "peng", "pang", "pian", "piao", "pie", "pin", "ping", "pou",
            "ma", "mo", "mu", "mi", "mao", "man", "mai", "mei", "men", "meng", "mang", "mian", "miao", "mie", "min", "ming", "miu", "me",
            "fa", "fo", "fu", "fan", "fang", "fei", "fen", "feng", "fou",
            "da", "de", "du", "di", "dao", "dan", "dai", "dang", "deng", "dong", "dou", "dian", "diao", "die", "ding", "diu", "duan", "dui", "dun", "duo", "dei", "den",
            "ta", "te", "tu", "ti", "tao", "tan", "tai", "tang", "teng", "tong", "tou", "tian", "tiao", "tie", "ting", "tuan", "tui", "tun", "tuo",
            "na", "ni", "nu", "nv", "nao", "nan", "nai", "nang", "neng", "nian", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nuan", "nuo", "nve", "ne", "nei", "nen", "niang",
            "la", "li", "lu", "lv", "lao", "lan", "lai", "lang", "leng", "lian", "liao", "lie", "lin", "ling", "liu", "long", "lou", "luan", "lun", "luo", "lve", "le", "lei", "lia", "lo",
            "ga", "ge", "gu", "gao", "gan", "gai", "geng", "gong", "gou", "gua", "guo", "guai", "gui", "guan", "guang", "gun", "gei", "gen", "gang",
            "ka", "ke", "ku", "kao", "kan", "kai", "keng", "kong", "kou", "kua", "kuo", "kuai", "kui", "kuan", "kuang", "kun", "ken", "kang",
            "ha", "he", "hu", "hao", "han", "hai", "hang", "heng", "hong", "hou", "hua", "huo", "huai", "hui", "huan", "huang", "hun", "hei", "hen",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiu", "ju", "juan", "jue", "jun", "jiong",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiu", "qu", "quan", "que", "qun", "qiong",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiu", "xu", "xuan", "xue", "xun", "xiong",
            "zha", "zhe", "zhi", "zhu", "zhao", "zhan", "zhai", "zhang", "zheng", "zhong", "zhou", "zhua", "zhuo", "zhuai", "zhui", "zhuan", "zhuang", "zhun", "zhei", "zhen",
            "cha", "che", "chi", "chu", "chao", "chan", "chai", "chang", "cheng", "chong", "chou", "chua", "chuo", "chuai", "chui", "chuan", "chuang", "chun", "chen",
            "sha", "she", "shi", "shu", "shao", "shan", "shai", "shang", "sheng", "shou", "shua", "shuo", "shuai", "shui", "shuan", "shuang", "shun", "shei", "shen",
            "ra", "re", "ri", "ru", "rao", "ran", "rang", "reng", "rong", "rou", "rua", "ruan", "rui", "run", "ruo", "ren",
            "za", "ze", "zi", "zu", "zao", "zan", "zai", "zang", "zeng", "zong", "zou", "zuo", "zui", "zuan", "zun", "zei", "zen",
            "ca", "ce", "ci", "cu", "cao", "can", "cai", "cang", "ceng", "cong", "cou", "cuo", "cui", "cuan", "cun", "cen",
            "sa", "se", "si", "su", "sao", "san", "sai", "sang", "seng", "song", "sou", "suo", "sui", "suan", "sun", "sen",
            "ya", "yi", "yu", "yao", "yan", "yang", "ye", "yin", "ying", "you", "yuan", "yue", "yun", "yo", "yong",
            "wa", "wu", "wo", "wai", "wan", "wang", "wei", "wen", "weng"
    };

    /**
     * 常用音节优先顺序（粗略按现代汉语使用频率排列，仅用于决定同一数字编码下默认显示哪一个音节；
     * 用户点拼写条上的音节可切换到同编码的任一其他音节）。未列出的音节按音节表顺序排在后面。
     */
    private static final String[] RANK = {
            "de", "shi", "yi", "le", "ji", "ge", "zhi", "wo", "ni", "bu", "you", "ta", "he", "zai", "ren", "shen",
            "me", "dou", "zhe", "na", "lai", "qu", "shuo", "hui", "neng", "xia", "shang", "zhong", "guo", "jiu",
            "mei", "kan", "xiang", "dao", "huan", "gang", "bian", "zhen", "hao", "gao", "dian", "ying", "sou", "suo", "kong",
            "zhan", "dong", "xi", "ai", "qing", "wei", "wen", "jian", "xin", "xiao", "da", "xian", "zhao", "zuo", "wan",
            "yang", "qi", "si", "fa", "ma", "ju", "li", "lu", "tu", "cheng", "chu", "shou", "tou", "tian", "guan",
            "chan", "chang", "fang", "dang", "tang", "lang", "kang", "hang", "zhang", "qiang", "wang", "huang", "guang",
            "kuang", "zhuang", "chuang", "shuang", "feng", "peng", "meng", "deng", "teng", "geng", "keng", "heng", "zheng",
            "sheng", "reng", "zeng", "ceng", "seng", "bing", "ping", "ming", "ding", "ting", "ning", "ling", "jing", "xing"
    };

    private static final Map<String, List<String>> BY_DIGITS = new HashMap<>();
    private static final Set<String> DIGIT_CODES = new HashSet<>();
    private static final Set<String> SYLLABLE_SET = new HashSet<>(Arrays.asList(SYLLABLES));

    static {
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < RANK.length; i++) rank.put(RANK[i], i);
        for (String syllable : SYLLABLES) {
            String code = encode(syllable);
            BY_DIGITS.computeIfAbsent(code, k -> new ArrayList<>()).add(syllable);
            DIGIT_CODES.add(code);
        }
        // 同一编码的候选按常用程度排序，排在第一位的作为默认切分结果
        for (List<String> list : BY_DIGITS.values()) {
            list.sort((a, b) -> Integer.compare(rank.getOrDefault(a, Integer.MAX_VALUE), rank.getOrDefault(b, Integer.MAX_VALUE)));
        }
    }

    private PinyinTable() {
    }

    /** 音节总数。 */
    public static int count() {
        return SYLLABLES.length;
    }

    /** 全部音节（只读）。 */
    public static List<String> syllables() {
        return Collections.unmodifiableList(Arrays.asList(SYLLABLES));
    }

    /** 是否为标准音节。 */
    public static boolean isSyllable(String text) {
        return text != null && SYLLABLE_SET.contains(text);
    }

    /** 把一个音节编码成九键数字串（a/b/c→2，d/e/f→3，g/h/i→4，j/k/l→5，m/n/o→6，p/q/r/s→7，t/u/v→8，w/x/y/z→9）。 */
    public static String encode(String syllable) {
        if (syllable == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < syllable.length(); i++) sb.append(digitOf(syllable.charAt(i)));
        return sb.toString();
    }

    private static char digitOf(char c) {
        if (c >= 'a' && c <= 'c') return '2';
        if (c >= 'd' && c <= 'f') return '3';
        if (c >= 'g' && c <= 'i') return '4';
        if (c >= 'j' && c <= 'l') return '5';
        if (c >= 'm' && c <= 'o') return '6';
        if (c >= 'p' && c <= 's') return '7';
        if (c >= 't' && c <= 'v') return '8';
        return '9';
    }

    /** 编码成该数字串的全部音节（按常用程度排序），没有则返回空列表。 */
    public static List<String> forDigits(String digits) {
        List<String> list = BY_DIGITS.get(digits);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /** 该数字串是否恰好是一个音节的编码。 */
    public static boolean isDigitCode(String digits) {
        return digits != null && DIGIT_CODES.contains(digits);
    }

    /**
     * 把数字序列切分成音节：取能覆盖的最长前缀（音节数最少者优先），
     * 覆盖不到的尾部数字原样保留为 remainder（用户继续按键后会重新切分）。
     */
    public static Segmentation segment(String digits) {
        if (digits == null || digits.isEmpty()) return new Segmentation(Collections.emptyList(), 0, "");
        int n = digits.length();
        List<String>[] best = new List[n + 1];
        best[0] = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (best[i] == null) continue;
            for (int len = 1; len <= 6 && i + len <= n; len++) {
                List<String> cands = BY_DIGITS.get(digits.substring(i, i + len));
                if (cands == null) continue;
                List<String> next = new ArrayList<>(best[i]);
                next.add(cands.get(0));
                if (best[i + len] == null || next.size() < best[i + len].size()) best[i + len] = next;
            }
        }
        int covered = 0;
        for (int i = n; i >= 0; i--) {
            if (best[i] != null) {
                covered = i;
                break;
            }
        }
        List<String> syllables = covered == 0 ? Collections.emptyList() : best[covered];
        return new Segmentation(syllables, covered, digits.substring(covered));
    }

    /** 数字序列能否完整切分成音节。 */
    public static boolean canSegment(String digits) {
        if (digits == null || digits.isEmpty()) return false;
        return segment(digits).covered == digits.length();
    }

    /** 切分结果：syllables 为已切出的音节，covered 为覆盖的数字个数，remainder 为未覆盖的尾部数字。 */
    public static class Segmentation {

        public final List<String> syllables;
        public final int covered;
        public final String remainder;

        public Segmentation(List<String> syllables, int covered, String remainder) {
            this.syllables = syllables;
            this.covered = covered;
            this.remainder = remainder;
        }

        /** 已切出的音节连写（不带空格），用作联想的拼音串；remainder 另行附加。 */
        public String joined() {
            StringBuilder sb = new StringBuilder();
            for (String s : syllables) sb.append(s);
            return sb.toString();
        }

        public boolean isEmpty() {
            return syllables.isEmpty() && remainder.isEmpty();
        }
    }
}
