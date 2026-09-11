/**
 * Copyright (c) 2015-2026 EOVA.CN. All rights reserved.
 * Licensed under the LGPL-3.0 license
 * For authorization, please contact: admin@eova.cn
 */
package cn.eova.compat.jfinal.kit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.eova.testkit.OldImplementationLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **`LegacyTimeKit` 与 jfinal 5.2.6 `TimeKit` 的跨制品比对（第 177 轮）**。
 *
 * <p><b>为什么补（第 176 轮扫描的直接产物）：</b>`judge-coverage-sweep.py` 报出
 * `LegacyTimeKit`（159 行）**类名在判据里零出现**。查证后属**真缺口**：R37 的处置写的是
 * "把 5.2.6 语义固化为项目自有 `LegacyTypeKit`/`LegacyTimeKit`"，并且确实建了
 * 逐 (方法 × 输入) 的跨制品表（`LegacyTypeKitGoldenTest`，540 条）—— **但那张表只覆盖
 * `LegacyTypeKit` 这一半**；TimeKit 这一半始终没有判据。R37 的原话是
 * "凡新旧两侧都存在的同名第三方类，都必须固化，不得依赖 classpath 顺序"，
 * 故这条缺口正是 R37 自己要求的收尾。
 *
 * <p><b>比什么：</b>`LegacyTimeKit` 是 jfinal `TimeKit` 的**子集 port**（8 个公开方法），
 * 故只比对**两侧都存在的确定型方法**（`now()`/`format(now)` 之类依赖"当前时刻"的不入表）：
 * <pre>
 *   getDateTimeFormatter(String) / getSimpleDateFormat(String)
 *   parse(String, String) / parseLocalDateTime(String, String)
 *   toLocalDateTime(Date) / toDate(LocalDateTime) / toDate(LocalDate) / toDate(LocalTime)
 * </pre>
 * 每个 (方法 × 输入) 取**结果描述串**：成功 ⇒ 规范化后的值（`Date` 取毫秒；
 * 时间类型取 `toString()`；格式化器取 `toString()`/`toPattern()`）；失败 ⇒
 * **异常类型 + 消息逐字** —— R37 的实测教训正是"异常类型与消息也不同"
 * （`RuntimeException(ParseException: Unparseable date)` vs
 * `IllegalArgumentException: Invalid date string ...`），故异常必须入判据。
 *
 * <p><b>反空断言：</b>断言比对条数 ≥ 40，且**成功与抛异常两类都出现过**
 * （否则可能只覆盖了一条分支）。
 *
 * <p><b>如实登记的边界：</b>旧 `TimeKit` 还有 now 族、format 族、isAfter/isBefore/isEqual、
 * parseLocalDate/parseLocalTime/toLocalDate/toLocalTime/toDate(LocalDate,LocalTime)/toLong
 * 等方法，**新类未 port**（新栈按需子集）⇒ 这些不属等价范围，不得据此声称"TimeKit 全量等价"。
 */
class LegacyTimeKitGoldenTest {

    /** 旧制品里 TimeKit 的确定型方法（两侧同名同参） */
    private static final String[][] CASES = {
            // {方法名, 参数类型种类, 输入}
            {"parse", "SS", "2026-09-12 03:20:00", "yyyy-MM-dd HH:mm:ss"},
            {"parse", "SS", "2026-09-12", "yyyy-MM-dd"},
            {"parse", "SS", "2026/09/12", "yyyy-MM-dd"},
            {"parse", "SS", "not-a-date", "yyyy-MM-dd"},
            {"parse", "SS", "", "yyyy-MM-dd"},
            {"parse", "SS", "2026-13-45", "yyyy-MM-dd"},
            {"parse", "SS", "2026-09-12 03:20:00", "yyyy/MM/dd"},
            {"parseLocalDateTime", "SS", "2026-09-12 03:20:00", "yyyy-MM-dd HH:mm:ss"},
            {"parseLocalDateTime", "SS", "2026-09-12", "yyyy-MM-dd"},
            {"parseLocalDateTime", "SS", "bad", "yyyy-MM-dd"},
            {"parseLocalDateTime", "SS", "", "yyyy-MM-dd HH:mm:ss"},
            {"getSimpleDateFormat", "S", "yyyy-MM-dd HH:mm:ss"},
            {"getSimpleDateFormat", "S", "yyyy-MM-dd"},
            {"getSimpleDateFormat", "S", "HH:mm:ss"},
            {"getSimpleDateFormat", "S", ""},
            {"getDateTimeFormatter", "S", "yyyy-MM-dd HH:mm:ss"},
            {"getDateTimeFormatter", "S", "yyyy-MM-dd"},
            {"getDateTimeFormatter", "S", ""},
            // ---- 第二轮补：把表扩到足够宽（初稿只 25 条，而我拍的阈值是 40 ⇒ 又是"拍数字"，
            //      这次按"补足到覆盖更多形态"来扩，而不是把阈值改小）----
            {"parse", "SS", "2026-09-12 03:20:00.123", "yyyy-MM-dd HH:mm:ss.SSS"},
            {"parse", "SS", "03:20:00", "HH:mm:ss"},
            {"parse", "SS", "2026-9-1", "yyyy-M-d"},
            {"parse", "SS", "20260912", "yyyyMMdd"},
            {"parse", "SS", "2026-09-12T03:20:00", "yyyy-MM-dd'T'HH:mm:ss"},
            {"parse", "SS", "2026年09月12日", "yyyy年MM月dd日"},
            {"parseLocalDateTime", "SS", "2026-09-12 03:20:00.123", "yyyy-MM-dd HH:mm:ss.SSS"},
            {"parseLocalDateTime", "SS", "2026-9-1", "yyyy-M-d"},
            {"parseLocalDateTime", "SS", "2026-09-12T03:20:00", "yyyy-MM-dd'T'HH:mm:ss"},
            {"parseLocalDateTime", "SS", "2026年09月12日", "yyyy年MM月dd日"},
            {"parseLocalDateTime", "SS", "2026-02-30", "yyyy-MM-dd"},
            {"getSimpleDateFormat", "S", "yyyy年MM月dd日"},
            {"getSimpleDateFormat", "S", "yyyy-MM-dd'T'HH:mm:ss.SSS"},
            {"getSimpleDateFormat", "S", "EEE MMM dd HH:mm:ss zzz yyyy"},
            {"getDateTimeFormatter", "S", "yyyy年MM月dd日"},
            {"getDateTimeFormatter", "S", "yyyy-MM-dd'T'HH:mm:ss.SSS"},
            {"getDateTimeFormatter", "S", "EEE MMM dd HH:mm:ss zzz yyyy"},
    };

    /**
     * 单参 Date 方法 × 输入。
     *
     * <p>★ 只有 `toLocalDateTime(Date)`：旧 `TimeKit` **没有** `toDate(Date)` 重载
     * （它的 toDate 只收 LocalDateTime/LocalDate/LocalTime/(LocalDate,LocalTime)），
     * 初稿把 "toDate" 放进来 ⇒ `NoSuchMethodException`（本轮实测踩到）。
     */
    private static final String[] ONE_ARG_METHODS = {
            "toLocalDateTime",
    };

    private static String render(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Date d) {
            return "Date:" + d.getTime();
        }
        if (v instanceof java.text.SimpleDateFormat f) {
            return "SDF:" + f.toPattern();
        }
        if (v instanceof java.time.format.DateTimeFormatter f) {
            return "DTF:" + f.toString();
        }
        return v.getClass().getSimpleName() + ":" + v;
    }

    private static String invokeOld(Method m, Object... args) {
        try {
            return render(m.invoke(null, args));
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            return "ERR:" + c.getClass().getName() + ":" + c.getMessage();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getName() + ":" + t.getMessage();
        }
    }

    private static String invokeNew(Method m, Object... args) {
        try {
            return render(m.invoke(null, args));
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            return "ERR:" + c.getClass().getName() + ":" + c.getMessage();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getName() + ":" + t.getMessage();
        }
    }

    @Test
    @DisplayName("★ 逐 (方法 × 输入) 比对 LegacyTimeKit 与 jfinal 5.2.6 TimeKit：差异应为 0")
    void matchesOldTimeKit() throws Exception {
        Assumptions.assumeTrue(OldImplementationLoader.oldJFinalJarAvailable(),
                "旧 jfinal 制品缺失：" + OldImplementationLoader.oldJFinalJar());

        ClassLoader loader =
                OldImplementationLoader.createWithOldJFinal(OldImplementationLoader.locateRepoRoot());
        Class<?> oldTimeKit = loader.loadClass("com.jfinal.kit.TimeKit");
        // 自校验：上场的是 5.2.6 制品，而不是新栈 enjoy 里的同名类
        OldImplementationLoader.assertFromJar(oldTimeKit, OldImplementationLoader.oldJFinalJar());

        List<String> diffs = new ArrayList<>();
        int compared = 0;
        int okCount = 0;
        int errCount = 0;

        // ① 两参 / 单参字符串方法
        for (String[] c : CASES) {
            String name = c[0];
            compared++;
            String oldOut;
            String newOut;
            if ("SS".equals(c[1])) {
                Method om = oldTimeKit.getMethod(name, String.class, String.class);
                Method nm = LegacyTimeKit.class.getMethod(name, String.class, String.class);
                oldOut = invokeOld(om, c[2], c[3]);
                newOut = invokeNew(nm, c[2], c[3]);
            } else {
                Method om = oldTimeKit.getMethod(name, String.class);
                Method nm = LegacyTimeKit.class.getMethod(name, String.class);
                oldOut = invokeOld(om, c[2]);
                newOut = invokeNew(nm, c[2]);
            }
            if (oldOut.startsWith("ERR:")) {
                errCount++;
            } else {
                okCount++;
            }
            if (!oldOut.equals(newOut)) {
                diffs.add(name + "(" + (c.length > 3 ? c[2] + "," + c[3] : c[2]) + ") 旧=" + oldOut + " 新=" + newOut);
            }
        }

        // ② 时间类型单参方法（含 null 与三类时间类型）
        Object[][] values = {
                {new Date(1_700_000_000_000L)},
                {new Date(0L)},
                {new Date(-86_400_000L)},
                {new Date(2_000_000_000_000L)},
                {new Date(1L)},
                {null},
        };
        for (String name : ONE_ARG_METHODS) {
            for (Object[] v : values) {
                compared++;
                Method om = oldTimeKit.getMethod(name, Date.class);
                Method nm = LegacyTimeKit.class.getMethod(name, Date.class);
                String oldOut = invokeOld(om, v);
                String newOut = invokeNew(nm, v);
                if (oldOut.startsWith("ERR:")) {
                    errCount++;
                } else {
                    okCount++;
                }
                if (!oldOut.equals(newOut)) {
                    diffs.add(name + "(" + v[0] + ") 旧=" + oldOut + " 新=" + newOut);
                }
            }
        }
        // ③ toDate 的三个时间类型重载
        Object[][] typed = {
                {LocalDateTime.of(2026, 9, 12, 3, 20, 0), LocalDateTime.class},
                {LocalDate.of(2026, 9, 12), LocalDate.class},
                {LocalTime.of(3, 20, 0), LocalTime.class},
                {LocalDateTime.of(1970, 1, 1, 0, 0, 0), LocalDateTime.class},
                {LocalDate.of(1970, 1, 1), LocalDate.class},
                {LocalTime.MIDNIGHT, LocalTime.class},
        };
        for (Object[] t : typed) {
            compared++;
            Method om = oldTimeKit.getMethod("toDate", (Class<?>) t[1]);
            Method nm = LegacyTimeKit.class.getMethod("toDate", (Class<?>) t[1]);
            String oldOut = invokeOld(om, t[0]);
            String newOut = invokeNew(nm, t[0]);
            if (oldOut.startsWith("ERR:")) {
                errCount++;
            } else {
                okCount++;
            }
            if (!oldOut.equals(newOut)) {
                diffs.add("toDate(" + ((Class<?>) t[1]).getSimpleName() + ") 旧=" + oldOut + " 新=" + newOut);
            }
        }

        // ★ 反空断言：条数够、且成功与异常两类都出现过（否则可能只覆盖一条分支）
        assertTrue(compared >= 40, "比对条数必须 ≥40，实际=" + compared);
        assertTrue(okCount >= 10, "必须有足够多的成功用例，实际 ok=" + okCount);
        assertTrue(errCount >= 3, "必须有异常用例（异常类型与消息也是契约），实际 err=" + errCount);

        assertEquals(List.of(), diffs, "与 jfinal 5.2.6 TimeKit 的差异必须为 0；实际差异 " + diffs.size() + " 处");
    }
}
