package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 九键 T9 切分回归测试（第 139 轮新增）：覆盖 5.9.18 的 tushuguan 修复
 *（887484826 必须切成 tu/shu/guan）与已核实的边界行为，防止 RANK 重排
 * 或音节表改动把切分改坏。期望值均经本地 JVM 直接跑 PinyinTable 核实。
 */
public class PinyinTableTest {

    private static void assertSegment(String digits, List<String> syllables, String remainder) {
        PinyinTable.Segmentation seg = PinyinTable.segment(digits);
        assertEquals("syllables for " + digits, syllables, seg.syllables);
        assertEquals("remainder for " + digits, remainder, seg.remainder);
    }

    @Test
    public void tushuguanSegmentsToThreeSyllables() {
        assertSegment("887484826", List.of("tu", "shu", "guan"), "");
    }

    @Test
    public void tushuPartialInput() {
        assertSegment("88748482", List.of("tu", "shu", "gua"), "");
        assertSegment("4826", List.of("guan"), "");
        assertSegment("482", List.of("gua"), "");
        assertSegment("48", List.of("gu"), "");
    }

    @Test
    public void fewestSyllablesWin() {
        // 526 一个音节 kan 覆盖，比 ka + o（两个音节）优先
        assertSegment("526", List.of("kan"), "");
        // 234 一个音节 cei 覆盖
        assertSegment("234", List.of("cei"), "");
        // 968 一个音节 you 覆盖
        assertSegment("968", List.of("you"), "");
        // 73926：re + wan 两个音节全覆盖
        assertSegment("73926", List.of("re", "wan"), "");
        // 8376：te + po 两个音节全覆盖
        assertSegment("8376", List.of("te", "po"), "");
    }

    @Test
    public void singleLettersAndRemainder() {
        assertSegment("2", List.of("a"), "");
        assertSegment("9", List.of("ê"), "");
        assertSegment("26", List.of("an"), "");
        assertSegment("66", List.of("mo"), "");
        // 888：tu 覆盖前两位，尾部 8 单独成不了音节，原样保留
        assertSegment("888", List.of("tu"), "8");
    }

    @Test
    public void unsegmentableDigitsKeptAsRemainder() {
        assertSegment("0", List.of(), "0");
        assertSegment("1", List.of(), "1");
        assertSegment("4", List.of(), "4");
        assertSegment("777", List.of(), "777");
        assertSegment("", List.of(), "");
        assertSegment(null, List.of(), "");
    }

    @Test
    public void canSegment() {
        assertTrue(PinyinTable.canSegment("887484826"));
        assertTrue(PinyinTable.canSegment("4826"));
        assertFalse(PinyinTable.canSegment("777"));
        assertFalse(PinyinTable.canSegment(""));
        assertFalse(PinyinTable.canSegment(null));
    }

    @Test
    public void encodeAndLookup() {
        assertEquals("54264", PinyinTable.encode("liang"));
        // 同一编码 54264 下 liang 排在 jiang 前面（RANK 常用程度）
        assertEquals(List.of("liang", "jiang"), PinyinTable.forDigits("54264"));
        assertTrue(PinyinTable.isDigitCode("4826"));
        assertFalse(PinyinTable.isDigitCode("777"));
    }

    @Test
    public void syllableTable() {
        assertEquals(423, PinyinTable.count());
        assertTrue(PinyinTable.isSyllable("liang"));
        assertTrue(PinyinTable.isSyllable("guan"));
        // lo / ra 不是标准音节（已从表里删掉）
        assertFalse(PinyinTable.isSyllable("lo"));
        assertFalse(PinyinTable.isSyllable("ra"));
        assertFalse(PinyinTable.isSyllable(null));
    }
}
