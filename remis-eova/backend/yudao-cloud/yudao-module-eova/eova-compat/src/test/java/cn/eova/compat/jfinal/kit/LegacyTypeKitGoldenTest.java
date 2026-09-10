/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegacyTypeKit} 对 {@code com.jfinal.kit.TypeKit}（jfinal 5.2.6 真制品）的逐方法等价验证。
 *
 * <p><b>为什么需要这个测试：</b>{@code RecordSemanticsGoldenTest} 只覆盖 114 条<b>列值级</b>探针，
 * 它证明了 EOVA 实际用到的取值路径一致，但没有覆盖类型转换矩阵的边界
 * （{@code toBoolean} 的 Float/Double 分支、{@code toNumber} 的小数判定、
 * {@code toDate} 的长度分派边界、{@code toShort}/{@code toByte} 等）。
 * 本测试让 <b>5.2.6 的原始实现上场作证</b>，逐 (方法 × 输入) 比对：
 * 返回值相等，或<b>异常类型与消息</b>相同。
 *
 * <p><b>这个测试防的是什么：</b>{@code com.jfinal.kit.TypeKit} 同时存在于旧栈的
 * jfinal 5.2.6 与新栈的 enjoy 5.3.0 中，生效者取决于 classpath 顺序。
 * 若不固化，等价性会随依赖解析结果漂移 —— 这类问题不会以编译失败形式暴露，
 * 只会在金标比对上表现为难解释的差异。
 *
 * <p>acceptanceProfile: golden-legacy-typekit
 */
class LegacyTypeKitGoldenTest {

    /** 旧栈 jfinal 制品中的原版实现 */
    private static Class<?> oldTypeKit;

    /** 输入值矩阵：名称 -> 值（含 null、各数值型、各字符串形态、各时间型） */
    private static Map<String, Object> inputs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("null", null);
        // —— 整数型 ——
        m.put("Integer(1)", 1);
        m.put("Integer(0)", 0);
        m.put("Integer(2)", 2);
        m.put("Integer(-1)", -1);
        m.put("Long(7)", 7L);
        m.put("Short(3)", (short) 3);
        m.put("Byte(4)", (byte) 4);
        m.put("BigInteger(9)", new BigInteger("9"));
        // —— 浮点型（toBoolean 在此必须抛 ClassCastException）——
        m.put("Double(1.0)", 1.0d);
        m.put("Float(1.0f)", 1.0f);
        m.put("BigDecimal(1)", new BigDecimal("1"));
        m.put("BigDecimal(1.5)", new BigDecimal("1.5"));
        // —— 布尔 ——
        m.put("Boolean(true)", Boolean.TRUE);
        m.put("Boolean(false)", Boolean.FALSE);
        // —— 字符串：数字形态 ——
        m.put("Str(1)", "1");
        m.put("Str(0)", "0");
        m.put("Str(5)", "5");
        m.put("Str(1.5)", "1.5");
        m.put("Str(-2)", "-2");
        m.put("Str(empty)", "");
        m.put("Str(abc)", "abc");
        m.put("Str(这个)", "这个");
        // —— 字符串：布尔形态（大小写敏感度边界）——
        m.put("Str(true)", "true");
        m.put("Str(TRUE)", "TRUE");
        m.put("Str(True)", "True");
        m.put("Str(false)", "false");
        m.put("Str(FALSE)", "FALSE");
        m.put("Str(yes)", "yes");
        // —— 字符串：日期时间形态（触发 toDate 的长度分派）——
        m.put("Str(date10)", "2019-09-20");
        m.put("Str(dt16)", "2019-09-20 15:31");
        m.put("Str(dt19)", "2019-09-20 15:31:19");
        m.put("Str(dt21)", "2019-09-20 15:31:19.0");
        m.put("Str(len9)", "test11111");
        m.put("Str(len12-nocolon)", "2019-09-20x");
        // —— 时间型 ——
        m.put("java.util.Date", new Date(1568964679000L));
        m.put("java.sql.Date", java.sql.Date.valueOf("2019-09-20"));
        m.put("java.sql.Time", Time.valueOf("15:31:19"));
        m.put("Timestamp", Timestamp.valueOf("2019-09-20 15:31:19"));
        m.put("LocalDate", LocalDate.of(2019, 9, 20));
        m.put("LocalDateTime", LocalDateTime.of(2019, 9, 20, 15, 31, 19));
        m.put("LocalTime", LocalTime.of(15, 31, 19));
        m.put("OffsetDateTime", OffsetDateTime.of(2019, 9, 20, 15, 31, 19, 0, ZoneOffset.ofHours(8)));
        m.put("ZonedDateTime",
                ZonedDateTime.of(2019, 9, 20, 15, 31, 19, 0, ZoneOffset.ofHours(8)));
        // —— 不兼容类型：必须走兜底 cast ——
        m.put("Object", new Object());
        return m;
    }

    /** 待比对的方法名（5.2.6 全部 12 个 to* 公开方法） */
    private static final List<String> METHODS = List.of(
            "toStr", "toInt", "toLong", "toDouble", "toBigDecimal", "toFloat",
            "toShort", "toByte", "toBoolean", "toNumber", "toDate", "toLocalDateTime");

    @Test
    @DisplayName("逐 (方法 × 输入) 比对 LegacyTypeKit 与 jfinal 5.2.6 TypeKit：差异应为 0")
    void matchesOldTypeKit() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());

        ClassLoader loader =
                OldImplementationLoader.createWithOldJFinal(OldImplementationLoader.locateRepoRoot());
        oldTypeKit = loader.loadClass("com.jfinal.kit.TypeKit");
        // 自校验：确保上场的是 5.2.6 制品，而非新栈 enjoy 的同名类
        OldImplementationLoader.assertFromJar(oldTypeKit, OldImplementationLoader.oldJFinalJar());

        Map<String, Object> inputs = inputs();
        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int okCount = 0;
        int throwCount = 0;

        for (String method : METHODS) {
            Method oldM = oldTypeKit.getMethod(method, Object.class);
            Method newM = LegacyTypeKit.class.getMethod(method, Object.class);
            for (Map.Entry<String, Object> e : inputs.entrySet()) {
                compared++;
                Object oldOut = invoke(oldM, e.getValue());
                Object newOut = invoke(newM, e.getValue());
                if (oldOut instanceof Ok) {
                    okCount++;
                } else {
                    throwCount++;
                }
                if (!render(oldOut).equals(render(newOut))) {
                    diffs.add(method + "(" + e.getKey() + "): 旧=" + render(oldOut)
                            + " / 新=" + render(newOut));
                }
            }
        }

        // 非空转证据：若矩阵只覆盖一种结果形态，比对就说明不了什么
        System.out.println("[LegacyTypeKit 比对] 方法 " + METHODS.size() + " × 输入 " + inputs.size()
                + " = 比对 " + compared + " 条；差异 " + diffs.size()
                + "；旧侧正常返回 " + okCount + " 条 / 抛异常 " + throwCount + " 条");
        assertTrue(throwCount > 0 && okCount > 0,
                "比对矩阵退化：正常返回 " + okCount + " 条、抛异常 " + throwCount
                        + " 条，两类都必须覆盖，否则比对不具判别力");
        assertTrue(diffs.isEmpty(),
                "LegacyTypeKit 与 jfinal 5.2.6 差异 " + diffs.size() + " 条：\n" + String.join("\n", diffs));
    }

    /**
     * 反射调用并把"正常返回"与"抛异常"统一为可观测量。
     *
     * @return 正常返回时为 {@code Ok:值}，抛异常时为 {@code Throw:异常类名:消息}
     */
    private static Object invoke(Method m, Object arg) {
        try {
            return new Ok(m.invoke(null, arg));
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            return new Throw(t.getClass().getName(), t.getMessage());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 正常返回标记 */
    private record Ok(Object value) {
    }

    /** 异常标记（类名 + 消息，两者都是金标口径的一部分） */
    private record Throw(String type, String message) {
    }

    /**
     * 渲染比对口径。异常统一去掉消息中的类加载器/模块措辞差异？
     * <b>不</b> —— 消息本身是金标捕获到的可观测差异（如 ClassCastException 的
     * "is in module java.base of loader 'bootstrap'"），刻意逐字比对。
     */
    private static String render(Object o) {
        if (o instanceof Ok ok) {
            return "Ok:" + String.valueOf(ok.value());
        }
        if (o instanceof Throw t) {
            return "Throw:" + t.type() + ":" + t.message();
        }
        return "?:" + o;
    }
}
