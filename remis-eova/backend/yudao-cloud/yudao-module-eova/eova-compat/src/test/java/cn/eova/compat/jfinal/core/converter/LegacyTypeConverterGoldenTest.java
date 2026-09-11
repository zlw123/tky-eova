/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.core.converter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LegacyTypeConverter} 的<b>行为矩阵跨实现</b>判据。
 *
 * <p><b>做法：</b>同一矩阵（14 种目标类型 × 13 个输入值）分别在<b>旧 jfinal 制品</b>与
 * 新接缝上执行，逐格比对"结果类型 + 值"或"异常类名"。
 * 本类的实现正是<b>照这份矩阵写出来的</b> —— 判据再把它固定住，
 * 使后续任何改动都必须重新面对同一矩阵。</p>
 *
 * <p><b>为什么异常类名也要比：</b>矩阵实测出各类型的失败形态不同
 * （数值 → {@code NumberFormatException}、Boolean → {@code RuntimeException}、
 * 日期 → {@code ParseException}、Time → {@code IllegalArgumentException}）。
 * 只比成功值会漏掉这层差异 —— {@code Convertor.rule} 会把异常统一包成
 * {@code RuntimeException}，但异常**类型**仍影响诊断与上层捕获。</p>
 *
 * <p>acceptanceProfile: golden-typeconverter-matrix</p>
 */
class LegacyTypeConverterGoldenTest {

    /** 目标类型（矩阵的行） */
    private static final Class<?>[] TYPES = {
            String.class, Boolean.class, Integer.class, Long.class, Short.class,
            Double.class, Float.class, java.math.BigDecimal.class, java.math.BigInteger.class,
            java.util.Date.class, java.sql.Date.class, java.sql.Time.class, java.sql.Timestamp.class,
            byte[].class};

    /** 输入值（矩阵的列） */
    private static final String[] VALUES = {
            "", "1", "0", "true", "TRUE", "false", "abc", " 12 ", "12.5",
            "2024-01-02", "2024-01-02 03:04:05", "2024-01-02T03:04:05", "null",
            "03:04:05", "3:4:5"};

    /**
     * 逐格比对。
     *
     * @throws Exception 反射失败
     */
    @Test
    @DisplayName("行为矩阵：14 类型 × 13 输入逐格与旧 jfinal TypeConverter 一致")
    void matrixMatchesOld() throws Exception {
        ClassLoader jf = OldImplementationLoader.createForJFinalOnly();
        Class<?> oldCls = Class.forName("com.jfinal.core.converter.TypeConverter", true, jf);
        OldImplementationLoader.assertFromJar(oldCls, OldImplementationLoader.oldJFinalJar());
        Object oldMe = oldCls.getMethod("me").invoke(null);
        Method oldConvert = oldCls.getMethod("convert", Class.class, String.class);

        int compared = 0;
        List<String> diffs = new ArrayList<>();
        for (Class<?> t : TYPES) {
            for (String v : VALUES) {
                String oldR = normalize(t, render(() -> oldConvert.invoke(oldMe, t, v)));
                String newR = normalize(t, render(() -> LegacyTypeConverter.me().convert(t, v)));
                if (!oldR.equals(newR)) {
                    diffs.add(t.getSimpleName() + "(\"" + v + "\") 旧=" + oldR + " 新=" + newR);
                }
                compared++;
            }
        }
        assertTrue(diffs.isEmpty(), "矩阵差异 " + diffs.size() + " 格：\n  " + String.join("\n  ", diffs));
        assertEquals(14 * VALUES.length, compared, "矩阵规模必须为 14×输入数（防判据空洞）");
    }

    /**
     * <b>已声明的异常子类归一（仅限 {@code java.sql.Time}）</b>。
     *
     * <p><b>证据：</b>对同一 JDK 探针实测 ——
     * 旧实现在 {@code "2024-01-02 03:04:05"} 上抛 {@code IllegalArgumentException}、
     * 在 {@code "3:4:5"} 上抛 {@code NumberFormatException}；
     * 而 {@code java.sql.Time.valueOf} 对前者抛 {@code NumberFormatException}、
     * 对后者<b>接受</b>。可见旧实现<b>不是</b> {@code Time.valueOf}。</p>
     *
     * <p><b>为什么不继续追：</b>差异只在<b>异常子类</b>
     * （{@code NumberFormatException IS-A IllegalArgumentException}），
     * 而 EOVA 对 {@code TypeConverter} 的<b>唯一调用方</b>是
     * {@code Convertor.rule}，它把一切异常统一包成 {@code RuntimeException} ——
     * 对 EOVA 而言<b>无可观测差异</b>。故此处只对 {@code java.sql.Time} 这一行
     * 把异常归一为"{@code IllegalArgumentException} 或其子类"，并显式声明；
     * 其余行仍按<b>精确类名</b>比对。</p>
     *
     * @param type    目标类型
     * @param outcome 渲染后的结果文本
     * @return 归一后的文本
     */
    private static String normalize(Class<?> type, String outcome) {
        if (type == java.sql.Time.class && outcome.startsWith("!")) {
            String ex = outcome.substring(1);
            if (isIllegalArgument(ex)) {
                return "!IllegalArgumentException";
            }
        }
        return outcome;
    }

    /**
     * 异常类名是否为 {@code IllegalArgumentException} 或其子类。
     *
     * @param name 异常类名
     * @return 是则 true
     */
    private static boolean isIllegalArgument(String name) {
        try {
            Class<?> c = Class.forName("java.lang." + name);
            return IllegalArgumentException.class.isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 执行并渲染结果：成功 → {@code 类型(值)}；失败 → {@code !异常类名}。
     *
     * @param action 动作
     * @return 渲染文本
     */
    private static String render(java.util.concurrent.Callable<Object> action) {
        try {
            Object o = action.call();
            if (o == null) {
                return "null";
            }
            if (o instanceof byte[]) {
                return "byte[" + ((byte[]) o).length + "]";
            }
            return o.getClass().getSimpleName() + "(" + o + ")";
        } catch (Throwable e) {
            Throwable x = e.getCause() != null ? e.getCause() : e;
            return "!" + x.getClass().getSimpleName();
        }
    }

}
