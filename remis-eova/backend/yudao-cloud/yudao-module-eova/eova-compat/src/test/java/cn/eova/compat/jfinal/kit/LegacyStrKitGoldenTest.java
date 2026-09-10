/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.lang.reflect.Method;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyStrKit} 的<b>跨实现</b>等价判据（对照旧 jfinal 5.2.6 制品）。
 *
 * <p><b>为什么必须对照旧制品而不是"自己看着办"：</b>
 * {@code isBlank} 是那种"看起来显而易见、实则容易写错"的方法。
 * 旧实现<b>不是</b> {@code trim().isEmpty()}，而是
 * <b>"所有字符都 ≤ 空格(32)"</b>。两者在多种输入上结论<b>相反</b>：
 * <ul>
 *   <li>{@code "\u0001"}（控制字符 1）—— 旧实现判 <b>blank</b>；
 *       直觉版 trim 也判 blank，此处一致；</li>
 *   <li>{@code "\u00A0"}（不换行空格，160）与 {@code "\u3000"}（全角空格，12288）——
 *       旧实现判 <b>非</b>blank（因为 160/12288 &gt; 32）；
 *       而 {@code trim().isEmpty()} 会判 <b>blank</b>。→ <b>结论相反</b></li>
 *   <li>{@code "\u0000"}（NUL）—— 旧实现判 blank（0 ≤ 32）。</li>
 * </ul>
 * 本判据把这些输入<b>逐条与旧制品比对</b>，而不是由我断言"应该是这样"。</p>
 *
 * <p>acceptanceProfile: golden-strkit-seam</p>
 */
class LegacyStrKitGoldenTest {

    /** 覆盖各类边界：null / 空串 / 单空格 / 低值控制字符 / 高值空白 / 正常字符 */
    private static final String[] INPUTS = {
            null, "", " ", "   ",
            "\t", "\n", "\r", "\u0000", "\u0001", "\u001f",
            "\u00A0",            // 不换行空格(160) —— 旧实现判【非】blank
            "\u3000",            // 全角空格(12288) —— 旧实现判【非】blank
            "a", " a ", "\u00A0a",
    };

    /**
     * {@code isBlank} 逐输入对照旧 {@code StrKit.isBlank}。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("isBlank 逐输入与旧 jfinal StrKit 一致（含 \\u00A0 / \\u3000 这类反向边界）")
    void isBlankMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.kit.StrKit", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Method oldIsBlank = oldCls.getMethod("isBlank", String.class);

        int compared = 0;
        for (String in : INPUTS) {
            boolean oldR = (Boolean) oldIsBlank.invoke(null, in);
            boolean newR = LegacyStrKit.isBlank(in);
            assertEquals(oldR, newR,
                    "isBlank(" + describe(in) + ") 必须与旧实现一致");
            compared++;
        }
        assertTrue(compared >= 10, "至少比对 10 个输入，实际 " + compared + " 个（防判据空洞）");

        // 把两条"反向边界"钉死为显式断言：它们正是 trim 版会写错的地方
        assertFalse(LegacyStrKit.isBlank("\u00A0"),
                "\\u00A0(160) > 32，旧实现判非 blank —— trim().isEmpty() 会在此处写错");
        assertFalse(LegacyStrKit.isBlank("\u3000"),
                "\\u3000(12288) > 32，旧实现判非 blank —— trim().isEmpty() 会在此处写错");
        assertTrue(LegacyStrKit.isBlank("\u0001"),
                "\\u0001(1) ≤ 32，旧实现判 blank");
        assertTrue(LegacyStrKit.isBlank("\u0000"),
                "NUL(0) ≤ 32，旧实现判 blank");
    }

    /**
     * {@code notBlank} 的数组边界（null / 空数组 / 含 blank 元素）对照旧实现。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("notBlank 对照旧 jfinal StrKit（null 与空数组均为 false）")
    void notBlankMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.kit.StrKit", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Method oldNotBlank = oldCls.getMethod("notBlank", String[].class);

        String[][] cases = {
                null,
                {},
                {"a"},
                {"a", "b"},
                {"a", null},
                {"a", ""},
                {"a", " "},
                {"\u00A0"},            // 非 blank（> 32）
                {"\u00A0", "b"},
        };
        for (String[] c : cases) {
            boolean oldR = (Boolean) oldNotBlank.invoke(null, (Object) c);
            boolean newR = LegacyStrKit.notBlank(c);
            assertEquals(oldR, newR, "notBlank(" + (c == null ? "null" : java.util.Arrays.toString(c))
                    + ") 必须与旧实现一致");
        }

        // 空数组不算"非空"—— 这条容易被"真值真空"直觉写错
        assertFalse(LegacyStrKit.notBlank(), "空数组必须返回 false（旧实现如此）");
        assertFalse(LegacyStrKit.notBlank((String[]) null), "null 必须返回 false");
        assertTrue(LegacyStrKit.notBlank("a"));
    }

    /**
     * {@code getRandomUUID} 形态：32 位小写十六进制、无连字符、且每次不同。
     */
    @Test
    @DisplayName("getRandomUUID 为 32 位无连字符十六进制且不重复")
    void getRandomUuidShape() {
        String a = LegacyStrKit.getRandomUUID();
        String b = LegacyStrKit.getRandomUUID();
        assertTrue(a.matches("[0-9a-f]{32}"), "应匹配 [0-9a-f]{32}，实际：" + a);
        assertTrue(b.matches("[0-9a-f]{32}"), "应匹配 [0-9a-f]{32}，实际：" + b);
        assertFalse(a.equals(b), "两次取值不得相同");
    }

    /**
     * 输入的可读描述。
     *
     * @param s 输入
     * @return 描述
     */
    private static String describe(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            sb.append(c < 33 || c > 126 ? String.format("\\u%04x", (int) c) : c);
        }
        return sb.append('"').toString();
    }

}
