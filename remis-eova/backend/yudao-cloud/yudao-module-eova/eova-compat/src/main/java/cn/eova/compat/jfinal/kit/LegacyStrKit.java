/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.util.UUID;

/**
 * jfinal 5.2.6 的 {@code com.jfinal.kit.StrKit} 的等价接缝（仅 EOVA 实际使用的 3 个方法）。
 *
 * <p>{@code ported from} {@code com.jfinal.kit.StrKit}（jfinal 5.2.6，
 * sha256 {@code 6afdb3b059624f8f41d07a6b6d9f4c889c8e77791f79e4f954af5cfee97d757b}）。</p>
 *
 * <p><b>方法集口径：全树普查后只做被实际调用的 3 个</b> ——
 * 旧树对 {@code StrKit} 的全部用法为 {@code notBlank}(2)、{@code getRandomUUID}(2)、
 * {@code isBlank}(1)，故不引入其余方法（避免"照抄整个工具类但大半无用"）。</p>
 *
 * <p><b>{@link #isBlank(String)} 的语义：判定标准是「所有字符都 ≤ 空格(32)」，不是
 * {@code trim().isEmpty()}</b>。旧字节码为
 * <pre>
 * if (s == null) return true;
 * for (int i = 0, len = s.length(); i &lt; len; i++)
 *     if (s.charAt(i) &gt; ' ') return false;   // ' ' = 32
 * return true;
 * </pre>
 * 本类<b>按此结构逐字实现</b>（而不是改写成 {@code trim().isEmpty()}）——
 * 理由是"源码结构可对照旧字节码"，属可复核性考虑。</p>
 *
 * <p><b>此处我原先写错过一次，记录如下（本轮由变异测试暴露）：</b>
 * 我最初在此断言"两者结论相反"，并举 {@code "\u00A0"}(160) 与 {@code "\u3000"}(12288)
 * 为例、称 {@code trim().isEmpty()} 会判 blank 而旧实现判非 blank。
 * <b>该断言是错的。</b> Java 的 {@code String.trim()} 定义正是"去除首尾 ≤ {@code ' '}(32)
 * 的字符"，因此 {@code trim().isEmpty()} 与"所有字符 ≤ 32"<b>恰好等价</b>：
 * 只要串中存在任一 &gt; 32 的字符，{@code trim()} 之后必然非空。
 * 实测验证：单字符码点 {@code 0..0x3000} 共 <b>12289 个</b>逐个比对，
 * 差异 <b>0 个</b>；另抽查 {@code "a\u00A0b"}、{@code "\u00A0a\u3000"}、
 * {@code " \u00A0 "} 等多字符组合，亦无差异。</p>
 *
 * <p>故：把实现改写成 {@code trim().isEmpty()} 属<b>等价改写</b>，不是缺陷；
 * 变异测试未捕获它属<b>正确</b>结果（无法区分等价实现），
 * 与"判据空洞"必须区分开 —— 判据对本方法的真实判别力由
 * {@code LegacyStrKitGoldenTest} 对旧制品的逐输入比对提供。</p>
 *
 * <p><b>{@link #notBlank(String...)} 的两处边界（同样逐字保真）：</b>
 * <ol>
 *   <li>{@code strings == null || strings.length == 0} → 返回 {@code false}
 *       （空数组不算"非空"，注意这不是"真值真空"语义）；</li>
 *   <li>任一元素 {@code isBlank} → 立即返回 {@code false}。</li>
 * </ol>
 */
public final class LegacyStrKit {

    private LegacyStrKit() {
    }

    /**
     * 是否为空或"全为 ≤ 空格(32) 的字符"。
     *
     * @param s 待判字符串
     * @return 空 / 全低值字符 返回 true
     */
    public static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0, len = s.length(); i < len; i++) {
            if (s.charAt(i) > ' ') {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否全部非 blank；null 或空数组返回 false（旧实现如此）。
     *
     * @param strings 待判字符串
     * @return 全部非 blank 返回 true
     */
    public static boolean notBlank(String... strings) {
        if (strings == null || strings.length == 0) {
            return false;
        }
        for (String s : strings) {
            if (isBlank(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 随机 UUID 字符串，<b>去掉连字符</b>（32 位十六进制）。
     *
     * <p>旧字节码：{@code UUID.randomUUID().toString().replace("-", "")}。</p>
     *
     * @return 32 位无连字符 UUID
     */
    public static String getRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
